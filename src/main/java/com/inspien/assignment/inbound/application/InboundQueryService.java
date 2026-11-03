package com.inspien.assignment.inbound.application;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.inspien.assignment.global.transaction.HistoryTx;
import com.inspien.assignment.inbound.application.port.MessageStore;
import com.inspien.assignment.inbound.domain.AssignedOrder;
import com.inspien.assignment.inbound.domain.MessageState;

/**
 * 다른 도메인(영수증, 배송, 조회)이 수신 기록을 읽는 길. RECEIVED_MESSAGE·MESSAGE_ORDER는 inbound가 맡는다 (D-21).
 */
@Service
public class InboundQueryService {

	private final MessageStore store;

	public InboundQueryService(MessageStore store) {
		this.store = store;
	}

	/**
	 * 주문의 수신일시. H2에 수신 기록이 없으면 비어 있다. 수신일시를 지어내지 않는다 (D-22).
	 */
	@HistoryTx(readOnly = true)
	public Optional<Instant> receivedAt(String orderId) {
		return assignment(orderId).map(AssignedOrder::receivedAt);
	}

	@HistoryTx(readOnly = true)
	public Optional<AssignedOrder> assignment(String orderId) {
		return this.store.findAssignedByOrderIds(List.of(orderId))
			.stream()
			.findFirst();
	}

	@HistoryTx(readOnly = true)
	public Map<String, AssignedOrder> assignments(Collection<String> orderIds) {
		if (orderIds.isEmpty()) {
			return Map.of();
		}
		return this.store.findAssignedByOrderIds(orderIds)
			.stream()
			.collect(Collectors.toMap(AssignedOrder::orderId, Function.identity()));
	}

	@HistoryTx(readOnly = true)
	public Optional<MessageSummary> message(String messageId) {
		return this.store.findSummary(messageId);
	}

	@HistoryTx(readOnly = true)
	public List<MessageListItem> messages(MessageState state, Instant from, Instant to, String after, int limit) {
		return this.store.findListItems(state, from, to, after, limit);
	}

}
