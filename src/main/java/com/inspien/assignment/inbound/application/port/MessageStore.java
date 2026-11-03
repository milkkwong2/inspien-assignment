package com.inspien.assignment.inbound.application.port;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.inspien.assignment.inbound.application.MessageListItem;
import com.inspien.assignment.inbound.application.MessageSummary;
import com.inspien.assignment.inbound.application.ReceiveResult;
import com.inspien.assignment.inbound.domain.AssignedOrder;
import com.inspien.assignment.inbound.domain.MessageKey;
import com.inspien.assignment.inbound.domain.MessageState;
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

	Optional<MessageSummary> findSummary(String messageId);

	/**
	 * 메시지 목록, 메시지 ID(시간순) 오름차순. 목록에 보여 줄 칸만 읽는다. 받은 원문과 수신 결과는 읽지 않는다.
	 *
	 * @param state 상태 (null이면 전부)
	 * @param from 수신 시각 하한, 포함 (null이면 없음)
	 * @param to 수신 시각 상한, 제외 (null이면 없음)
	 * @param after 이 메시지 ID 다음부터 (null이면 처음부터)
	 * @param limit 최대 건수
	 */
	List<MessageListItem> findListItems(MessageState state, Instant from, Instant to, String after, int limit);

}
