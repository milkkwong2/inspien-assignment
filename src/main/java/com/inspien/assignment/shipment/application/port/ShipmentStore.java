package com.inspien.assignment.shipment.application.port;

import java.util.Optional;

import com.inspien.assignment.shipment.domain.Shipment;

/**
 * 운송회사 Oracle SHIPMENT_TB. 구현은 모든 쿼리 조건과 INSERT 값에 내 APPLICANT_KEY를 붙인다 (D-01, D-25).
 */
public interface ShipmentStore {

	/**
	 * SHIPMENT_TB에는 ORDER_ID 유일 제약이 없으므로 INSERT 전에 같은 주문의 배송이 있는지 본다 (D-03).
	 */
	Optional<Shipment> findByOrderId(String orderId);

	Optional<Shipment> findById(String shipmentId);

	/**
	 * 같은 PK가 있으면 덮어쓰지 않고 DataIntegrityViolationException으로 실패한다.
	 */
	void insert(Shipment shipment);

}
