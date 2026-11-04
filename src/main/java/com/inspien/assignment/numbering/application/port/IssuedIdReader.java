package com.inspien.assignment.numbering.application.port;

import java.util.List;

import com.inspien.assignment.numbering.domain.IdSequence;

/**
 * 이미 쓴 번호를 읽는다. 기동 시 시퀀스를 Oracle과 맞출 때 쓴다 (D-08, D-22).
 *
 * <p>구현은 그 테이블을 맡은 도메인의 트랜잭션 Service에 있다(ORDER_TB는 order의 OrderService, SHIPMENT_TB는 shipment의
 * ShipmentRecordService). 채번기가 그 도메인들을 부르면 shipment와 서로 부르는 순환이 되므로 포트를 둔다 (D-21). 구현이
 * Service라 읽기는 그 Oracle의 읽기 전용 트랜잭션에서 돈다.
 */
public interface IssuedIdReader {

	/**
	 * 내 APPLICANT_KEY 범위에서 이미 쓴 번호 전부. 공용 테이블 전체가 아니다. 어느 시퀀스의 번호인지와 함께 한 번에 돌려줘,
	 * 구현의 공개 메서드가 트랜잭션 읽기 하나가 되게 한다.
	 */
	IssuedIds issuedIds();

	/**
	 * @param sequence 이 번호들을 발급한 시퀀스
	 * @param ids 이미 쓴 번호. 형식이 아닌 값이 섞여 있을 수 있다
	 */
	record IssuedIds(IdSequence sequence, List<String> ids) {
	}

}
