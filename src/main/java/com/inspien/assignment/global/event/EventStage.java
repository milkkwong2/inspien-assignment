package com.inspien.assignment.global.event;

/**
 * 처리 이벤트의 구간 (PROCESS_EVENT.STAGE). 단계 진행 상태의 단계(영수증·배송)와 달리 재시도 대상이 아닌 수신·마감도 있어
 * enum이 따로다.
 *
 * <p>CLOSE는 마감(STATUS N→Y)이다 (D-26).
 */
public enum EventStage {

	RECEIVE, RECEIPT, SHIPMENT, CLOSE

}
