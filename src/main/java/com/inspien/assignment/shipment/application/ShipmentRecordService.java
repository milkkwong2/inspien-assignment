package com.inspien.assignment.shipment.application;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.inspien.assignment.global.transaction.ShipmentTx;
import com.inspien.assignment.numbering.application.port.IssuedIdReader;
import com.inspien.assignment.numbering.domain.IdSequence;
import com.inspien.assignment.shipment.application.port.ShipmentStore;
import com.inspien.assignment.shipment.domain.Shipment;

/**
 * 배송 기록(운송회사 DB SHIPMENT_TB)의 짧은 트랜잭션. {@link ShipmentFacade}가 트랜잭션 없이 이 빈의 트랜잭션을 부른다.
 * PK 충돌 뒤의 다시 조회는 롤백된 INSERT와 다른 새 트랜잭션이어야 한다 (D-19).
 *
 * <p>채번기의 포트 {@link IssuedIdReader}도 여기서 구현한다. 채번기는 shipment를 모르고(D-21), 기동 시 동기화의 Oracle
 * 읽기는 다른 읽기처럼 운송회사 DB의 읽기 전용 트랜잭션에서 돈다.
 */
@Service
class ShipmentRecordService implements IssuedIdReader {

	private final ShipmentStore store;

	ShipmentRecordService(ShipmentStore store) {
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

	@Override
	@ShipmentTx(readOnly = true)
	public IssuedIds issuedIds() {
		return new IssuedIds(IdSequence.SHIPMENT, this.store.issuedIds());
	}

}
