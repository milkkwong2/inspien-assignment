package com.inspien.assignment.inbound.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.f4b6a3.tsid.TsidCreator;

import org.springframework.stereotype.Component;

import com.inspien.assignment.global.transaction.HistoryTx;
import com.inspien.assignment.inbound.application.port.MessageStore;
import com.inspien.assignment.inbound.domain.AssignedOrder;
import com.inspien.assignment.inbound.domain.MessageKey;
import com.inspien.assignment.inbound.domain.MessageState;
import com.inspien.assignment.inbound.domain.OrderAssembly;
import com.inspien.assignment.inbound.domain.ReceivedMessage;
import com.inspien.assignment.numbering.application.NumberingService;
import com.inspien.assignment.numbering.domain.IdSequence;

/**
 * 수신 흐름의 H2 트랜잭션 경계 (D-16, D-19). 수신 서비스는 트랜잭션 없이 이 빈의 짧은 트랜잭션들을 차례로 부른다.
 * 같은 클래스 안의 호출은 트랜잭션 프록시를 거치지 않으므로 경계를 별도 빈으로 둔다.
 */
@Component
class InboundRecords {

	private final MessageStore store;

	private final NumberingService numbering;

	InboundRecords(MessageStore store, NumberingService numbering) {
		this.store = store;
		this.numbering = numbering;
	}

	@HistoryTx(readOnly = true)
	Optional<ReceivedMessage> findLatest(MessageKey key) {
		return this.store.findLatestByKey(key);
	}

	/**
	 * 원본을 받은 바이트 그대로 저장한다. 인코딩 해석이 틀렸을 때도 원본으로 다시 판단할 수 있다.
	 */
	@HistoryTx
	ReceivedMessage start(MessageKey key, Instant arrivedAt, String contentType, byte[] body) {
		ReceivedMessage message = new ReceivedMessage(TsidCreator.getTsid().toString(), key, arrivedAt, contentType,
				body, MessageState.PROCESSING, null, null);
		this.store.insert(message);
		return message;
	}

	@HistoryTx
	void markProcessing(String messageId) {
		this.store.markProcessing(messageId);
	}

	@HistoryTx(readOnly = true)
	List<AssignedOrder> findAssigned(String messageId) {
		return this.store.findAssigned(messageId);
	}

	/**
	 * 주문번호 미리 배정. 발급과 MESSAGE_ORDER 기록이 같은 H2 트랜잭션이다 (D-08). 미리 배정한 번호는 다시 나가지 않는다.
	 *
	 * @throws com.inspien.assignment.numbering.domain.IdExhaustedException Z999를 넘으면 (기록은 롤백)
	 */
	@HistoryTx
	List<AssignedOrder> assignOrderIds(String messageId, Instant receivedAt, List<OrderAssembly.Accepted> accepted) {
		List<AssignedOrder> assigned = new ArrayList<>();
		for (OrderAssembly.Accepted a : accepted) {
			String orderId = this.numbering.next(IdSequence.ORDER);
			assigned.add(new AssignedOrder(orderId, messageId, a.seq(), a.item().userId(), a.item().itemId(),
					receivedAt));
		}
		this.store.insertAssigned(assigned);
		return assigned;
	}

	@HistoryTx
	void complete(String messageId, ReceiveResult result, Instant completedAt) {
		this.store.complete(messageId, result, completedAt);
	}

	@HistoryTx
	void fail(String messageId, int status, String code, String detail) {
		this.store.fail(messageId, status, code, detail);
	}

	@HistoryTx(readOnly = true)
	Optional<ReceiveResult> findResult(String messageId) {
		return this.store.findResult(messageId);
	}

}
