package com.inspien.assignment.order.domain;

/**
 * ORDER_TB.STATUS 값: 연계 완료 플래그 (설계서 §6, D-26). 제공 DB의 CHECK 제약이 N·Y만 받는다.
 *
 * <pre>
 * N ──마감 스케줄러 (영수증 DONE 그리고 배송 DONE)──▶ Y
 * </pre>
 *
 * <p>단계별 진행(영수증·배송)은 여기 없고 H2 ORDER_STAGE에 있다. Y는 두 단계를 합친 요약이라 쓰는 곳은 마감 스케줄러
 * 하나다.
 */
public enum OrderStatus {

	/** 연계 진행 중. 수신할 때 저장한다 */
	N,

	/** 영수증과 배송이 모두 끝남. 작업 대상에서 빠진다 */
	Y;

	/**
	 * DB 값을 읽는다. N·Y가 아니면 예외다. 내 키의 행에는 생길 수 없는 값이라, 조용히 대상에서 빼지 않고 멈춘다.
	 */
	public static OrderStatus fromDb(String value) {
		for (OrderStatus status : values()) {
			if (status.name().equals(value)) {
				return status;
			}
		}
		throw new IllegalStateException("알 수 없는 STATUS 값: " + value);
	}

}
