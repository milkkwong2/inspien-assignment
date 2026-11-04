package com.inspien.assignment.shipment.adapter.in.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.inspien.assignment.shipment.application.ShipmentFacade;

/**
 * IF-03 배송 스케줄러. 영수증 스케줄러와 스레드가 따로라 느린 FTP를 기다리지 않는다 (D-12, D-26).
 */
@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class ShipmentScheduler {

	private final ShipmentFacade facade;

	ShipmentScheduler(ShipmentFacade facade) {
		this.facade = facade;
	}

	@Scheduled(fixedDelayString = "${app.scheduler.fixed-delay}")
	void run() {
		this.facade.runCycle();
	}

}
