package com.inspien.assignment.inbound.application.port;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.inspien.assignment.inbound.application.ReceiveResult;
import com.inspien.assignment.inbound.domain.AssignedOrder;
import com.inspien.assignment.inbound.domain.MessageKey;
import com.inspien.assignment.inbound.domain.ReceivedMessage;

/**
 * H2 RECEIVED_MESSAGE·MESSAGE_ORDER. 두 테이블은 inbound가 맡는다 (D-21).
 */
public interface MessageStore {

	/**
	 * 같은 메시지 키의 가장 최근 기록.
	 */
	Optional<ReceivedMessage> findLatestByKey(MessageKey key);

	void insert(ReceivedMessage message);

	void markProcessing(String messageId);

	void complete(String messageId, ReceiveResult result, Instant completedAt);

	/**
	 * @param code 오류 코드 (조회 API가 보여 준다)
	 * @param detail 송신자에게 보낸 detail
	 */
	void fail(String messageId, int status, String code, String detail);

	Optional<ReceiveResult> findResult(String messageId);

	List<AssignedOrder> findAssigned(String messageId);

	List<AssignedOrder> findAssignedByOrderIds(Collection<String> orderIds);

	void insertAssigned(List<AssignedOrder> orders);

}
