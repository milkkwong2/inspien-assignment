package com.inspien.assignment.order.adapter.in.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.inspien.assignment.order.application.CloseService;

/**
 * 마감 스케줄러. 영수증·배송과 같은 주기로 돌고, 스레드가 따로라 느린 FTP를 기다리지 않는다 (D-12, D-26).
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class CloseScheduler {

	private final CloseService service;

	CloseScheduler(CloseService service) {
		this.service = service;
	}

	@Scheduled(fixedDelayString = "${app.scheduler.fixed-delay}")
	void run() {
		this.service.runCycle();
	}

}
