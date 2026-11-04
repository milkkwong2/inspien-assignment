package com.inspien.assignment.shipment.application.port;

import java.util.List;
import java.util.Optional;

import com.inspien.assignment.shipment.domain.Shipment;

/**
 * 운송회사 Oracle SHIPMENT_TB. 구현은 모든 쿼리 조건과 INSERT 값에 내 APPLICANT_KEY를 붙인다 (D-01, D-25).
 *
 * <p>실패 약속: Oracle 포트의 실패는 Spring DB 예외 계층(DataAccessException·TransactionException)이다. 포트가 예외를
 * 따로 정의하지 않는다. 연결·자원 장애와 거절의 분류는 트랜잭션 바깥(Facade)에서 DbFailures로 한다. 커밋 때의 예외는
 * 트랜잭션 밖에서만 보이고, 연결이 끊기면 롤백 예외가 원래 예외를 덮어 트랜잭션 안에서는 원인을 알 수 없다 (D-25). FTP는
 * Spring에 같은 추상화가 없어 포트가 예외를 직접 정의한다(ReceiptUploader).
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

	/**
	 * 내 APPLICANT_KEY 범위의 배송번호 전부. 기동 시 채번 동기화가 쓴다 (D-08).
	 */
	List<String> issuedIds();

}
