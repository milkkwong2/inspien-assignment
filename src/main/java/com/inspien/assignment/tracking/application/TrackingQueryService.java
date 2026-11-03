package com.inspien.assignment.tracking.application;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.inspien.assignment.global.error.ApiException;
import com.inspien.assignment.global.error.ErrorCode;
import com.inspien.assignment.inbound.application.InboundQueryService;
import com.inspien.assignment.inbound.application.MessageListItem;
import com.inspien.assignment.inbound.application.MessageSummary;
import com.inspien.assignment.inbound.application.ReceiveResult;
import com.inspien.assignment.inbound.domain.AssignedOrder;
import com.inspien.assignment.inbound.domain.MessageState;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.order.domain.OrderStatus;
import com.inspien.assignment.retry.application.RetryService;
import com.inspien.assignment.retry.domain.Stage;
import com.inspien.assignment.retry.domain.StageState;
import com.inspien.assignment.retry.domain.StageStatus;
import com.inspien.assignment.tracking.application.TrackingViews.EventView;
import com.inspien.assignment.tracking.application.TrackingViews.MessageDetail;
import com.inspien.assignment.tracking.application.TrackingViews.MessageSummaryView;
import com.inspien.assignment.tracking.application.TrackingViews.OrderDetail;
import com.inspien.assignment.tracking.application.TrackingViews.OrderSummary;
import com.inspien.assignment.tracking.application.TrackingViews.Page;
import com.inspien.assignment.tracking.application.TrackingViews.StageView;
import com.inspien.assignment.tracking.application.port.EventStore;
import com.inspien.assignment.tracking.domain.EventRecord;

/**
 * IF-04 처리 추적 조회 (설계서 §5.4, D-13, D-26). 읽기만 한다. Oracle STATUS(연계 완료 N/Y)와 H2 단계별 상태·이력을
 * 합친다. 주문 목록은 진행 중(N) 주문만 보여 주고, 끝난(Y) 주문은 주문번호로 상세를 본다.
 *
 * <p>둘이 어긋나면 정해 둔 쪽을 따른다. STATUS=Y인데 H2 기록이 없으면(H2 유실) 끝난 주문이라 두 단계를 DONE으로 보여 준다.
 * H2 조회가 실패하면 예외가 그대로 올라가 오류로 응답한다. "기록 없음"으로 바꿔 보여 주지 않는다.
 */
@Service
public class TrackingQueryService {

	private final OrderService orders;

	private final InboundQueryService inbound;

	private final RetryService retry;

	private final EventStore events;

	private final Clock clock;

	public TrackingQueryService(OrderService orders, InboundQueryService inbound, RetryService retry,
			EventStore events, Clock clock) {
		this.orders = orders;
		this.inbound = inbound;
		this.retry = retry;
		this.events = events;
		this.clock = clock;
	}

	public OrderDetail order(String orderId) {
		Order order = this.orders.findById(orderId)
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "주문이 없습니다: " + orderId));
		Optional<AssignedOrder> assigned = this.inbound.assignment(orderId);
		String messageId = assigned.map(AssignedOrder::messageId).orElse(null);
		OffsetDateTime receivedAt = assigned.map(assignment -> time(assignment.receivedAt())).orElse(null);
		StageState receiptState = this.retry.find(orderId, Stage.RECEIPT).orElse(null);
		StageState shipmentState = this.retry.find(orderId, Stage.SHIPMENT).orElse(null);
		StageView receipt = receiptView(order.status(), receiptState);
		StageView shipment = shipmentView(order.status(), shipmentState);
		List<EventView> events = eventsOfOrder(orderId);
		return new OrderDetail(order.orderId(), order.status().name(), messageId, receivedAt, order.userId(),
				order.itemId(), receipt, shipment, events);
	}

	/**
	 * 진행 중(STATUS=N) 주문 목록. 막힌 주문을 찾는 데 쓴다. 끝난(Y) 주문은 쌓이기만 해서 목록에 넣으면 한 페이지를 위해
	 * 전부 읽게 되므로 넣지 않는다. 끝난 주문은 주문번호로 상세를 본다 ({@link #order(String)}).
	 *
	 * <p>커서(after)는 Oracle 조회에 넣는다({@code ORDER_ID > after}). 단계별 상태와 수신 시각은 H2에 있어 합친 뒤에 거르고, 거른
	 * 다음에 limit으로 자른다. 자른 뒤에 거르면 한 페이지가 비거나 다음 커서가 틀린다. 커서는 ORDER_ID다. 고정 길이라 문자열
	 * 순서가 번호 순서이고, 조회 중 새 주문이 들어와도 결과가 밀리지 않는다.
	 *
	 * @param receipt 영수증 단계 상태 조건 (없으면 전부)
	 * @param shipment 배송 단계 상태 조건 (없으면 전부)
	 */
	public Page<OrderSummary> orders(StageStatus receipt, StageStatus shipment, Instant from, Instant to, String after,
			int limit) {
		List<String> candidates = inProgressIds(after);
		Map<Stage, Map<String, StageState>> stages = this.retry.findAll(candidates);
		Map<String, AssignedOrder> assigned = this.inbound.assignments(candidates);
		List<String> matched = candidates.stream()
			.filter(id -> matches(receipt, stages.get(Stage.RECEIPT).get(id)))
			.filter(id -> matches(shipment, stages.get(Stage.SHIPMENT).get(id)))
			.filter(id -> (from == null && to == null) || inRange(assigned.get(id), from, to))
			.toList();
		boolean hasMore = matched.size() > limit;
		List<String> pageIds = hasMore ? matched.subList(0, limit) : matched;
		List<OrderSummary> items = new ArrayList<>();
		for (String id : pageIds) {
			StageState receiptState = stages.get(Stage.RECEIPT).get(id);
			StageState shipmentState = stages.get(Stage.SHIPMENT).get(id);
			items.add(toOrderSummary(id, OrderStatus.N, receiptState, shipmentState, assigned.get(id)));
		}
		String nextAfter = hasMore ? pageIds.getLast() : null;
		return new Page<>(items, nextAfter, limit);
	}

	/**
	 * 목록 후보: 진행 중(N) 주문번호, 오름차순. 첫 페이지는 전부, 다음 페이지는 after 뒤부터.
	 */
	private List<String> inProgressIds(String after) {
		if (after == null) {
			return this.orders.findIdsByStatus(OrderStatus.N);
		}
		return this.orders.findIdsByStatusAfter(OrderStatus.N, after);
	}

	private OrderSummary toOrderSummary(String orderId, OrderStatus status, StageState receiptState,
			StageState shipmentState, AssignedOrder assignment) {
		String receiptStatus = stageStatus(status, receiptState).name();
		String shipmentStatus = stageStatus(status, shipmentState).name();
		String messageId = (assignment != null) ? assignment.messageId() : null;
		OffsetDateTime receivedAt = (assignment != null) ? time(assignment.receivedAt()) : null;
		String lastError = lastError(status, receiptState, shipmentState);
		return new OrderSummary(orderId, status.name(), receiptStatus, shipmentStatus, messageId, receivedAt,
				lastError);
	}

	public MessageDetail message(String messageId) {
		MessageSummary message = this.inbound.message(messageId)
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "메시지가 없습니다: " + messageId));
		// 완료된 메시지만 수신 결과가 있다. 실패면 오류 코드와 detail만 있다
		ReceiveResult result = message.result();
		ReceiveResult.Summary summary = (result != null) ? result.summary() : null;
		List<ReceiveResult.Stored> stored = (result != null) ? result.stored() : null;
		List<ReceiveResult.Rejected> rejected = (result != null) ? result.rejected() : null;
		List<ReceiveResult.Warning> warnings = (result != null) ? result.warnings() : null;
		List<EventView> events = eventsOfMessage(messageId);
		return new MessageDetail(message.messageId(), message.state().name(), message.responseStatus(),
				time(message.receivedAt()), time(message.completedAt()), message.errorCode(), message.errorDetail(),
				summary, stored, rejected, warnings, events);
	}

	/**
	 * 메시지 목록. 재전송이 오지 않아 PROCESSING·FAILED로 남은 메시지를 사람이 찾는 데 쓴다 (D-04). 커서는 메시지 ID(TSID,
	 * 시간순)다.
	 */
	public Page<MessageSummaryView> messages(MessageState state, Instant from, Instant to, String after, int limit) {
		List<MessageListItem> found = this.inbound.messages(state, from, to, after, limit + 1);
		boolean hasMore = found.size() > limit;
		List<MessageListItem> page = hasMore ? found.subList(0, limit) : found;
		List<MessageSummaryView> items = page.stream()
			.map(this::summaryView)
			.toList();
		String nextAfter = hasMore ? page.getLast().messageId() : null;
		return new Page<>(items, nextAfter, limit);
	}

	/**
	 * 영수증 단계. 결과물 칸(ARTIFACT)에는 영수증 파일명이 들어 있다.
	 */
	private StageView receiptView(OrderStatus status, StageState state) {
		String fileName = (state != null) ? state.artifact() : null;
		return stageView(status, state, fileName, null);
	}

	/**
	 * 배송 단계. 결과물 칸(ARTIFACT)에는 미리 배정한 SHIPMENT_ID가 들어 있다.
	 */
	private StageView shipmentView(OrderStatus status, StageState state) {
		String shipmentId = (state != null) ? state.artifact() : null;
		return stageView(status, state, null, shipmentId);
	}

	private StageView stageView(OrderStatus status, StageState state, String fileName, String shipmentId) {
		String derived = stageStatus(status, state).name();
		if (state == null) {
			return new StageView(derived, 0, null, null, null, null, null);
		}
		return new StageView(derived, state.attempts(), time(state.nextAttemptAt()), time(state.completedAt()),
				state.lastError(), fileName, shipmentId);
	}

	/**
	 * 단계 상태. Y면 H2 기록과 관계없이 DONE이다 (H2 유실이면 이력만 없다).
	 */
	private static StageStatus stageStatus(OrderStatus status, StageState state) {
		return (status == OrderStatus.Y) ? StageStatus.DONE : StageState.statusOf(state);
	}

	/**
	 * 목록은 N 주문만 다루므로 단계 상태는 H2 기록에서 바로 나온다.
	 */
	private static boolean matches(StageStatus wanted, StageState state) {
		return wanted == null || StageState.statusOf(state) == wanted;
	}

	/**
	 * 끝나지 않은 단계의 마지막 오류. 영수증을 먼저 본다.
	 */
	private static String lastError(OrderStatus status, StageState... states) {
		for (StageState state : states) {
			if (state != null && stageStatus(status, state) != StageStatus.DONE && state.lastError() != null) {
				return state.lastError();
			}
		}
		return null;
	}

	private List<EventView> eventsOfOrder(String orderId) {
		return this.events.findByOrderId(orderId)
			.stream()
			.map(this::view)
			.toList();
	}

	private List<EventView> eventsOfMessage(String messageId) {
		return this.events.findByMessageId(messageId)
			.stream()
			.map(this::view)
			.toList();
	}

	private EventView view(EventRecord event) {
		return new EventView(time(event.occurredAt()), event.stage(), event.result(), event.detail(),
				event.messageId(), event.orderId());
	}

	private MessageSummaryView summaryView(MessageListItem message) {
		return new MessageSummaryView(message.messageId(), message.state().name(), message.responseStatus(),
				time(message.receivedAt()), message.errorCode());
	}

	private static boolean inRange(AssignedOrder assigned, Instant from, Instant to) {
		if (assigned == null) {
			return false;
		}
		Instant at = assigned.receivedAt();
		return (from == null || !at.isBefore(from)) && (to == null || at.isBefore(to));
	}

	private OffsetDateTime time(Instant instant) {
		return (instant != null) ? instant.atZone(this.clock.getZone()).toOffsetDateTime() : null;
	}

}
