package com.inspien.assignment.shipment.adapter.in.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.inspien.assignment.shipment.application.ShipmentService;

/**
 * IF-03 배송 스케줄러. 영수증 스케줄러와 같은 스레드에서 차례로 돈다 (D-12).
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class ShipmentScheduler {

	private final ShipmentService service;

	ShipmentScheduler(ShipmentService service) {
		this.service = service;
	}

	@Scheduled(fixedDelayString = "${app.scheduler.fixed-delay}")
	void run() {
		this.service.runCycle();
	}

}
