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
 * IF-04 처리 추적 조회 (설계서 §5.4, D-13). 읽기만 한다. 현재 상태는 H2에 두지 않고 Oracle STATUS와 H2 이력을 합친다.
 *
 * <p>상태를 바꾸는 API는 없다. API가 쓰기까지 하면 Oracle과 H2를 함께 바꾸는 이중 쓰기가 된다. 되돌리기는 운영자가
 * Oracle STATUS로 한다 (D-10).
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
		return new OrderDetail(order.orderId(), order.status().name(), assigned.map(AssignedOrder::messageId).orElse(null),
				assigned.map(a -> time(a.receivedAt())).orElse(null), order.userId(), order.itemId(),
				stage(orderId, Stage.RECEIPT), stage(orderId, Stage.SHIPMENT), eventsOfOrder(orderId));
	}

	/**
	 * 주문 목록. Oracle STATUS로 대상을 고른 뒤 H2 수신 시각으로 거른다. 커서는 ORDER_ID다. 고정 길이라 문자열 순서가 번호
	 * 순서이고, 조회 중 새 주문이 들어와도 결과가 밀리지 않는다.
	 */
	public Page<OrderSummary> orders(OrderStatus status, Instant from, Instant to, String after, int limit) {
		List<String> ids = (status != null) ? this.orders.findIdsByStatus(status) : this.orders.findAllIds();
		List<String> candidates = ids.stream().filter(id -> after == null || id.compareTo(after) > 0).toList();
		Map<String, AssignedOrder> assigned = this.inbound.assignments(candidates);
		if (from != null || to != null) {
			candidates = candidates.stream().filter(id -> inRange(assigned.get(id), from, to)).toList();
		}
		boolean hasMore = candidates.size() > limit;
		List<String> pageIds = hasMore ? candidates.subList(0, limit) : candidates;
		List<OrderSummary> items = new ArrayList<>();
		for (Order order : this.orders.findByIds(pageIds)) {
			AssignedOrder a = assigned.get(order.orderId());
			items.add(new OrderSummary(order.orderId(), order.status().name(), (a != null) ? a.messageId() : null,
					(a != null) ? time(a.receivedAt()) : null, lastError(order)));
		}
		return new Page<>(items, hasMore ? pageIds.getLast() : null, limit);
	}

	public MessageDetail message(String messageId) {
		MessageSummary m = this.inbound.message(messageId)
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "메시지가 없습니다: " + messageId));
		ReceiveResult r = m.result();
		return new MessageDetail(m.messageId(), m.state().name(), m.responseStatus(), time(m.receivedAt()),
				time(m.completedAt()), m.errorCode(), m.errorDetail(), (r != null) ? r.summary() : null,
				(r != null) ? r.stored() : null, (r != null) ? r.rejected() : null, (r != null) ? r.warnings() : null,
				eventsOfMessage(messageId));
	}

	/**
	 * 메시지 목록. 재전송이 오지 않아 PROCESSING·FAILED로 남은 메시지를 사람이 찾는 데 쓴다 (D-04). 커서는 메시지 ID(TSID,
	 * 시간순)다.
	 */
	public Page<MessageSummaryView> messages(MessageState state, Instant from, Instant to, String after, int limit) {
		List<MessageSummary> found = this.inbound.messages(state, from, to, after, limit + 1);
		boolean hasMore = found.size() > limit;
		List<MessageSummary> page = hasMore ? found.subList(0, limit) : found;
		List<MessageSummaryView> items = page.stream()
			.map(m -> new MessageSummaryView(m.messageId(), m.state().name(), m.responseStatus(), time(m.receivedAt()),
					m.errorCode()))
			.toList();
		return new Page<>(items, hasMore ? page.getLast().messageId() : null, limit);
	}

	private StageView stage(String orderId, Stage stage) {
		return this.retry.find(orderId, stage)
			.map(s -> new StageView(s.attempts(), time(s.nextAttemptAt()), s.lastError(),
					(stage == Stage.RECEIPT) ? s.artifact() : null, (stage == Stage.SHIPMENT) ? s.artifact() : null))
			.orElse(null);
	}

	/**
	 * 지금 단계의 마지막 오류. 영수증 단계면 영수증, 배송 단계면 배송 오류를 본다.
	 */
	private String lastError(Order order) {
		Stage stage = switch (order.status()) {
			case RECEIVED, RECEIPT_FAILED -> Stage.RECEIPT;
			case RECEIPT_SENT, SHIPMENT_FAILED -> Stage.SHIPMENT;
			case SHIPPED -> null;
		};
		if (stage == null) {
			return null;
		}
		return this.retry.find(order.orderId(), stage).map(StageState::lastError).orElse(null);
	}

	private List<EventView> eventsOfOrder(String orderId) {
		return this.events.findByOrderId(orderId).stream().map(this::view).toList();
	}

	private List<EventView> eventsOfMessage(String messageId) {
		return this.events.findByMessageId(messageId).stream().map(this::view).toList();
	}

	private EventView view(EventRecord e) {
		return new EventView(time(e.occurredAt()), e.stage(), e.result(), e.detail(), e.messageId(), e.orderId());
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
