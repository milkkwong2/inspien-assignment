package com.inspien.assignment.shipment.application;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.inspien.assignment.global.transaction.ShipmentTx;
import com.inspien.assignment.shipment.application.port.ShipmentStore;
import com.inspien.assignment.shipment.domain.Shipment;

/**
 * 운송회사 DB의 트랜잭션 경계. 배송 서비스는 트랜잭션 없이 이 빈의 짧은 트랜잭션을 부른다. PK 충돌 뒤의 다시 조회는
 * 롤백된 INSERT와 다른 새 트랜잭션이어야 한다 (D-19).
 */
@Component
class ShipmentRegistry {

	private final ShipmentStore store;

	ShipmentRegistry(ShipmentStore store) {
		this.store = store;
	}

	@ShipmentTx(readOnly = true)
	Optional<Shipment> findByOrderId(String orderId) {
		return this.store.findByOrderId(orderId);
	}

	@ShipmentTx(readOnly = true)
	Optional<Shipment> findById(String shipmentId) {
		return this.store.findById(shipmentId);
	}

	@ShipmentTx
	void insert(Shipment shipment) {
		this.store.insert(shipment);
	}

}
