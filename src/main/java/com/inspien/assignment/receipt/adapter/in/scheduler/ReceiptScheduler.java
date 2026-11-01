package com.inspien.assignment.receipt.adapter.in.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.inspien.assignment.receipt.application.ReceiptService;

/**
 * IF-02 영수증 스케줄러. fixedDelay라 앞 주기가 끝나야 다음 주기가 시작해 겹치지 않는다 (D-12).
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class ReceiptScheduler {

	private final ReceiptService service;

	ReceiptScheduler(ReceiptService service) {
		this.service = service;
	}

	@Scheduled(fixedDelayString = "${app.scheduler.fixed-delay}")
	void run() {
		this.service.runCycle();
	}

}
