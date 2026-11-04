package com.inspien.assignment.tracking.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.inspien.assignment.global.transaction.HistoryTx;
import com.inspien.assignment.tracking.application.port.EventStore;
import com.inspien.assignment.tracking.domain.EventRecord;

/**
 * 처리 이벤트(PROCESS_EVENT)를 읽는다 (D-13). 다른 H2 읽기처럼 읽기 전용 트랜잭션 안에서 읽는다. 조회 Facade는 DB에 이
 * 빈으로만 닿는다. 남기는 길은 {@link EventRecordService}다.
 */
@Service
class EventQueryService {

	private final EventStore store;

	EventQueryService(EventStore store) {
		this.store = store;
	}

	/**
	 * 시간순.
	 */
	@HistoryTx(readOnly = true)
	List<EventRecord> findByOrderId(String orderId) {
		return this.store.findByOrderId(orderId);
	}

	/**
	 * 시간순.
	 */
	@HistoryTx(readOnly = true)
	List<EventRecord> findByMessageId(String messageId) {
		return this.store.findByMessageId(messageId);
	}

}
