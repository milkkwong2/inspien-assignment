package com.inspien.assignment.inbound.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.inspien.assignment.global.transaction.HistoryTx;
import com.inspien.assignment.inbound.application.port.MessageStore;
import com.inspien.assignment.inbound.domain.AssignedOrder;

/**
 * 영수증·배송이 수신 기록을 읽는 길. RECEIVED_MESSAGE·MESSAGE_ORDER는 inbound가 맡는다 (D-21).
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
		return this.store.findAssignedByOrderIds(List.of(orderId)).stream().findFirst().map(AssignedOrder::receivedAt);
	}

}
