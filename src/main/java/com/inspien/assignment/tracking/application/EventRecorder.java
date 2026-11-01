package com.inspien.assignment.tracking.application;

import com.github.f4b6a3.tsid.TsidCreator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.global.transaction.HistoryTx;
import com.inspien.assignment.tracking.application.port.EventStore;
import com.inspien.assignment.tracking.domain.EventRecord;

/**
 * 처리 이벤트를 H2에 남긴다 (D-13).
 *
 * <p>항상 새 트랜잭션이다. 이벤트를 발행한 쪽의 H2 트랜잭션에 참여하면, 기록 실패가 그 트랜잭션까지 롤백시키고 발행한
 * 쪽이 롤백되면 이벤트도 사라진다. 이력은 업무 처리와 따로 커밋된다 (D-04).
 */
@Service
public class EventRecorder {

	private final EventStore store;

	public EventRecorder(EventStore store) {
		this.store = store;
	}

	@HistoryTx(propagation = Propagation.REQUIRES_NEW)
	public void record(ProcessEvent event) {
		this.store.insert(new EventRecord(TsidCreator.getTsid().toString(), event.occurredAt(), event.messageId(),
				event.orderId(), event.stage().name(), event.result().name(), event.detail()));
	}

}
