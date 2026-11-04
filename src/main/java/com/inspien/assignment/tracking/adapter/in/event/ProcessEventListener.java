package com.inspien.assignment.tracking.adapter.in.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.tracking.application.EventRecordService;

/**
 * 각 도메인이 발행한 처리 이벤트를 받아 기록한다. 이벤트로 받는 이유: tracking은 조회 때문에 inbound·order를 읽으므로,
 * 그쪽이 tracking을 직접 부르면 순환이 된다 (D-21).
 *
 * <p>이력은 최선 노력이다. 기록이 실패해도 예외를 발행한 쪽으로 넘기지 않는다 (D-04).
 */
@Component
class ProcessEventListener {

	private static final Logger log = LoggerFactory.getLogger(ProcessEventListener.class);

	private final EventRecordService events;

	ProcessEventListener(EventRecordService events) {
		this.events = events;
	}

	@EventListener
	void on(ProcessEvent event) {
		try {
			this.events.record(event);
		}
		catch (RuntimeException ex) {
			log.warn("처리 이벤트를 H2에 남기지 못했습니다: {}", event.logSummary(), ex);
		}
	}

}
