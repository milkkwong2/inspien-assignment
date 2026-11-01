package com.inspien.assignment.tracking.application.port;

import java.util.List;

import com.inspien.assignment.tracking.domain.EventRecord;

/**
 * H2 PROCESS_EVENT.
 */
public interface EventStore {

	void insert(EventRecord event);

	/**
	 * 시간순. 한 주문의 이벤트는 많아야 수십 건이라 페이징하지 않는다.
	 */
	List<EventRecord> findByOrderId(String orderId);

	List<EventRecord> findByMessageId(String messageId);

}
