package com.inspien.assignment.numbering.application.port;

import java.util.List;

import com.inspien.assignment.numbering.domain.IdSequence;

/**
 * 이미 쓴 번호를 읽는다. 기동 시 시퀀스를 Oracle과 맞출 때 쓴다 (D-08, D-22).
 *
 * <p>구현은 그 테이블을 맡은 도메인의 Oracle 어댑터에 있다(ORDER_TB는 order, SHIPMENT_TB는 shipment). 채번기가 그
 * 도메인들을 부르면 shipment와 서로 부르는 순환이 되므로 포트를 둔다 (D-21).
 */
public interface IssuedIdReader {

	IdSequence sequence();

	/**
	 * 내 APPLICANT_KEY 범위에서 이미 쓴 번호 전부. 공용 테이블 전체가 아니다.
	 */
	List<String> issuedIds();

}
