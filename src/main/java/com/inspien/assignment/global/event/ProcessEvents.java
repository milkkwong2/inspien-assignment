package com.inspien.assignment.global.event;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.inspien.assignment.global.event.ProcessEvent.Result;
import com.inspien.assignment.global.event.ProcessEvent.Stage;

/**
 * 처리 이벤트 발행. 이력은 최선 노력이라 발행이 실패해도 업무 처리를 막지 않는다 (D-04).
 */
@Component
public class ProcessEvents {

	private static final Logger log = LoggerFactory.getLogger(ProcessEvents.class);

	private final ApplicationEventPublisher publisher;

	private final Clock clock;

	public ProcessEvents(ApplicationEventPublisher publisher, Clock clock) {
		this.publisher = publisher;
		this.clock = clock;
	}

	public void publish(String messageId, String orderId, Stage stage, Result result, String detail) {
		ProcessEvent event = new ProcessEvent(this.clock.instant(), messageId, orderId, stage, result, detail);
		try {
			this.publisher.publishEvent(event);
		}
		catch (RuntimeException ex) {
			log.warn("처리 이벤트를 남기지 못했습니다: {}", event, ex);
		}
	}

}
