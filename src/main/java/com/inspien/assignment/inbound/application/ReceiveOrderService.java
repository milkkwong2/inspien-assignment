package com.inspien.assignment.inbound.application;

import java.nio.charset.Charset;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import com.inspien.assignment.global.config.AppProperties;
import com.inspien.assignment.global.error.ApiException;
import com.inspien.assignment.global.error.DbFailures;
import com.inspien.assignment.global.error.ErrorCode;
import com.inspien.assignment.global.event.ProcessEvent.Result;
import com.inspien.assignment.global.event.ProcessEvent.Stage;
import com.inspien.assignment.global.event.ProcessEvents;
import com.inspien.assignment.inbound.application.port.OrderMessageParser;
import com.inspien.assignment.inbound.domain.AssignedOrder;
import com.inspien.assignment.inbound.domain.MessageKey;
import com.inspien.assignment.inbound.domain.MessageParseException;
import com.inspien.assignment.inbound.domain.OrderAssembly;
import com.inspien.assignment.inbound.domain.ReceivedMessage;
import com.inspien.assignment.inbound.domain.ValueLimit;
import com.inspien.assignment.numbering.domain.IdExhaustedException;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.order.domain.Order;

/**
 * IF-01 주문 수신 (설계서 §7.1). 요청 안에서 동기로 쇼핑몰 DB에 저장한다 (D-04).
 *
 * <p>트랜잭션은 걸지 않는다. 짧은 트랜잭션 셋(H2 원본 저장·채번 → Oracle INSERT → H2 완료 기록)을 차례로 부르고,
 * 수신 잠금은 그 바깥에 건다 (D-16). 두 DB는 한 트랜잭션으로 묶을 수 없으므로 정합성은 미리 배정한 주문번호와
 * 재처리 때의 조회로 맞춘다 (D-03, D-05).
 */
@Service
public class ReceiveOrderService {

	private static final Logger log = LoggerFactory.getLogger(ReceiveOrderService.class);

	static final String MDC_MESSAGE_ID = "messageId";

	private final MessageLocks locks;

	private final InboundRecords records;

	private final OrderMessageParser parser;

	private final OrderService orders;

	private final ProcessEvents events;

	private final Clock clock;

	private final Duration duplicateWindow;

	private final ValueLimit valueLimit;

	ReceiveOrderService(MessageLocks locks, InboundRecords records, OrderMessageParser parser, OrderService orders,
			ProcessEvents events, Clock clock, AppProperties properties) {
		this.locks = locks;
		this.records = records;
		this.parser = parser;
		this.orders = orders;
		this.events = events;
		this.clock = clock;
		this.duplicateWindow = properties.inbound().duplicateWindow();
		this.valueLimit = new ValueLimit(Charset.forName(properties.inbound().dbCharset()),
				properties.inbound().maxValueBytes());
	}

	public ReceiveResult receive(byte[] body, String contentType) {
		Instant arrivedAt = this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
		MessageKey key = MessageKey.of(body);
		if (!this.locks.tryLock(key)) {
			throw new ApiException(ErrorCode.BUSY, "같은 본문을 처리하는 중입니다. 잠시 후 같은 본문을 다시 보내 주세요.");
		}
		try {
			Optional<ReceivedMessage> latest = history(() -> this.records.findLatest(key));
			ReceivedMessage.Handling handling = latest.map(m -> m.handlingOf(arrivedAt, this.duplicateWindow))
				.orElse(ReceivedMessage.Handling.NEW);
			return switch (handling) {
				case REPLAY -> replay(latest.get());
				case RESUME -> resume(latest.get());
				case NEW -> receiveNew(key, arrivedAt, contentType, body);
			};
		}
		finally {
			this.locks.unlock(key);
			MDC.remove(MDC_MESSAGE_ID);
		}
	}

	private ReceiveResult receiveNew(MessageKey key, Instant arrivedAt, String contentType, byte[] body) {
		ReceivedMessage message = history(() -> this.records.start(key, arrivedAt, contentType, body));
		MDC.put(MDC_MESSAGE_ID, message.id());
		return process(message, List.of());
	}

	/**
	 * 완료된 메시지와 같은 본문이 완료 후 1분 안에 왔다. 응답이 유실된 뒤의 재전송으로 보고 이전 결과를 다시 보낸다
	 * (D-15, D-24). 정상 재주문일 수도 있어 표시와 안내를 붙인다.
	 */
	private ReceiveResult replay(ReceivedMessage message) {
		MDC.put(MDC_MESSAGE_ID, message.id());
		Optional<ReceiveResult> stored = history(() -> this.records.findResult(message.id()));
		if (stored.isEmpty()) {
			// 완료 표시는 있는데 결과가 없다면 기록이 깨진 것이다. 이어서 처리하면 조회로 확인된다
			return resume(message);
		}
		this.events.publish(message.id(), null, Stage.RECEIVE, Result.WARN, "같은 본문 재수신: 이전 결과를 다시 보냄");
		return stored.get().asReplay(replayNotice());
	}

	/**
	 * 실패(400 제외)·처리 중 메시지는 시간과 관계없이 같은 기록을 이어서 처리한다 (D-16, D-24). 미리 배정한 번호가 있으면
	 * 같은 번호를 다시 쓴다.
	 */
	private ReceiveResult resume(ReceivedMessage message) {
		MDC.put(MDC_MESSAGE_ID, message.id());
		history(() -> {
			this.records.markProcessing(message.id());
			return null;
		});
		List<AssignedOrder> assigned = history(() -> this.records.findAssigned(message.id()));
		return process(message, assigned);
	}

	/**
	 * 저장해 둔 원본으로 파싱·조립하고 저장한다. 같은 바이트는 같은 결과라 재처리 때도 순서와 내용이 같다.
	 */
	private ReceiveResult process(ReceivedMessage message, List<AssignedOrder> alreadyAssigned) {
		OrderAssembly assembly;
		try {
			assembly = OrderAssembly.of(this.parser.parse(message.rawBody(), message.contentType()), this.valueLimit);
		}
		catch (MessageParseException ex) {
			throw failed(message, ErrorCode.XML_PARSE_ERROR, ex.getMessage(), ex);
		}

		List<AssignedOrder> assigned = alreadyAssigned;
		if (assigned.isEmpty() && !assembly.accepted().isEmpty()) {
			try {
				assigned = history(
						() -> this.records.assignOrderIds(message.id(), message.receivedAt(), assembly.accepted()));
			}
			catch (IdExhaustedException ex) {
				throw failed(message, ErrorCode.ORDER_ID_EXHAUSTED, "발급할 주문번호가 없습니다. 연계 담당자에게 문의해 주세요.", ex);
			}
		}

		List<Order> toStore = toOrders(assembly, assigned);
		boolean alreadyStored = !alreadyAssigned.isEmpty() && storedBefore(message, toStore);
		if (!alreadyStored && !toStore.isEmpty()) {
			alreadyStored = insert(message, toStore);
		}

		ReceiveResult result = ReceiveResult.of(message.id(), OffsetDateTime.ofInstant(message.receivedAt(),
				this.clock.getZone()), assembly, assigned);
		if (alreadyStored) {
			// 이번 요청이 저장한 것이 아니므로 사실대로 표시한다 (D-24)
			result = result.asReplay(replayNotice());
		}
		complete(message, result);
		publishOutcome(message, result, alreadyStored);
		return result;
	}

	/**
	 * 재처리 전 조회: 미리 배정한 번호의 행이 지난번에 이미 저장됐는가 (D-16).
	 */
	private boolean storedBefore(ReceivedMessage message, List<Order> expected) {
		return switch (compareWithStored(message, expected)) {
			case NONE -> false;
			case ALL_SAME -> true;
			case PARTIAL_OR_DIFFERENT -> throw failed(message, ErrorCode.UNEXPECTED_STATE,
					"이 메시지의 주문이 일부만 저장됐거나 내용이 다릅니다. 연계 담당자에게 문의해 주세요.", null);
		};
	}

	/**
	 * 주문 전체를 한 트랜잭션으로 INSERT한다 (D-05).
	 *
	 * @return PK 충돌이었고 다시 조회해 보니 같은 주문이 이미 있었으면 true
	 */
	private boolean insert(ReceivedMessage message, List<Order> toStore) {
		try {
			this.orders.insertAll(toStore);
			return false;
		}
		catch (DataIntegrityViolationException ex) {
			// 앞 요청이 포기한 뒤 Oracle에서 늦게 커밋됐을 수 있다. 롤백된 뒤 새 트랜잭션에서 다시 조회한다 (D-19)
			return switch (compareWithStored(message, toStore)) {
				case ALL_SAME -> true;
				case NONE -> throw failed(message, ErrorCode.ORDER_DB_REJECTED,
						"쇼핑몰 DB가 저장을 거절했습니다. 연계 담당자에게 문의해 주세요.", ex);
				case PARTIAL_OR_DIFFERENT -> throw failed(message, ErrorCode.UNEXPECTED_STATE,
						"이 메시지의 주문번호로 다른 주문이 저장돼 있습니다. 연계 담당자에게 문의해 주세요.", ex);
			};
		}
		catch (RuntimeException ex) {
			throw oracleFailure(message, ex);
		}
	}

	private Comparison compareWithStored(ReceivedMessage message, List<Order> expected) {
		List<Order> existing;
		try {
			existing = this.orders.findByIds(expected.stream().map(Order::orderId).toList());
		}
		catch (RuntimeException ex) {
			throw oracleFailure(message, ex);
		}
		if (existing.isEmpty()) {
			return Comparison.NONE;
		}
		Map<String, Order> byId = existing.stream().collect(Collectors.toMap(Order::orderId, Function.identity()));
		boolean allSame = expected.size() == existing.size()
				&& expected.stream().allMatch(o -> byId.containsKey(o.orderId()) && byId.get(o.orderId()).sameContentAs(o));
		return allSame ? Comparison.ALL_SAME : Comparison.PARTIAL_OR_DIFFERENT;
	}

	/**
	 * 연결·자원 장애는 503(재전송하면 이어서 처리), 그 외는 Oracle이 거절한 것이라 500 (D-17, D-25).
	 * 커밋 중 타임아웃은 결과 불명이다. 롤백됐다고 단정하지 않고 503으로 답해, 재전송 때 조회로 확인한다 (D-19).
	 */
	private ApiException oracleFailure(ReceivedMessage message, RuntimeException ex) {
		if (DbFailures.isConnectionOrResource(ex)) {
			return failed(message, ErrorCode.ORDER_DB_UNAVAILABLE,
					"쇼핑몰 DB에 저장하지 못했습니다. 같은 본문을 다시 보내 주세요.", ex);
		}
		return failed(message, ErrorCode.ORDER_DB_REJECTED, "쇼핑몰 DB가 저장을 거절했습니다. 연계 담당자에게 문의해 주세요.", ex);
	}

	/**
	 * 실패를 H2에 기록하고 응답용 예외를 만든다. 기록이 실패해도 응답은 그대로 나간다.
	 */
	private ApiException failed(ReceivedMessage message, ErrorCode code, String detail, Throwable cause) {
		try {
			this.records.fail(message.id(), code.status().value(), code.name(), detail);
		}
		catch (RuntimeException ex) {
			log.error("수신 실패를 H2에 기록하지 못했습니다: {}", code, ex);
		}
		String eventDetail = code + ": " + ((cause != null) ? cause.getClass().getSimpleName() + " " : "") + detail;
		this.events.publish(message.id(), null, Stage.RECEIVE,
				(code == ErrorCode.XML_PARSE_ERROR) ? Result.REJECT : Result.FAIL, eventDetail);
		return new ApiException(code, detail, message.id(), cause);
	}

	/**
	 * 쇼핑몰 DB 커밋 뒤의 실패는 200으로 답한다. 업무 데이터는 저장됐고 이력은 최선 노력이다. PROCESSING으로 남은 기록은
	 * 재전송이 오면 조회로 완료 처리된다 (D-04, D-16).
	 */
	private void complete(ReceivedMessage message, ReceiveResult result) {
		try {
			this.records.complete(message.id(), result, this.clock.instant());
		}
		catch (RuntimeException ex) {
			log.error("수신 완료를 H2에 기록하지 못했습니다. 주문은 저장됐습니다.", ex);
		}
	}

	private void publishOutcome(ReceivedMessage message, ReceiveResult result, boolean alreadyStored) {
		for (ReceiveResult.Stored stored : result.stored()) {
			this.events.publish(message.id(), stored.orderId(), Stage.RECEIVE,
					alreadyStored ? Result.SKIP : Result.SUCCESS,
					"seq=%d %s/%s%s".formatted(stored.seq(), stored.userId(), stored.itemId(),
							alreadyStored ? " 이미 저장됨" : ""));
		}
		for (ReceiveResult.Rejected rejected : result.rejected()) {
			this.events.publish(message.id(), null, Stage.RECEIVE, Result.REJECT, "seq=%d %s/%s %s%s".formatted(
					rejected.seq(), rejected.userId(), rejected.itemId(), rejected.reason(),
					(rejected.field() != null) ? "(" + rejected.field() + ")" : ""));
		}
		for (ReceiveResult.Warning warning : result.warnings()) {
			this.events.publish(message.id(), null, Stage.RECEIVE, Result.WARN,
					warning.userId() + " " + warning.reason());
		}
	}

	private List<Order> toOrders(OrderAssembly assembly, List<AssignedOrder> assigned) {
		Map<Integer, String> orderIdBySeq = assigned.stream()
			.collect(Collectors.toMap(AssignedOrder::seq, AssignedOrder::orderId));
		return assembly.accepted()
			.stream()
			.map(a -> Order.received(orderIdBySeq.get(a.seq()), a.item().userId(), a.item().itemId(),
					a.header().name(), a.header().address(), a.item().itemName(), a.item().price()))
			.toList();
	}

	private String replayNotice() {
		return "이전에 받은 같은 본문의 결과입니다. 새 주문이라면 %d분 뒤 다시 보내면 새 주문으로 처리됩니다."
			.formatted(Math.max(1, this.duplicateWindow.toMinutes()));
	}

	/**
	 * Oracle 저장 전의 H2 오류는 503이다. 채번과 중복 판단이 H2에 있어 진행할 수 없다 (D-04, D-08).
	 */
	private static <T> T history(Supplier<T> work) {
		try {
			return work.get();
		}
		catch (DataAccessException | TransactionException ex) {
			throw new ApiException(ErrorCode.HISTORY_DB_UNAVAILABLE, "처리 이력 저장소를 쓸 수 없습니다. 같은 본문을 다시 보내 주세요.",
					null, ex);
		}
	}

	private enum Comparison {

		NONE, ALL_SAME, PARTIAL_OR_DIFFERENT

	}

}
