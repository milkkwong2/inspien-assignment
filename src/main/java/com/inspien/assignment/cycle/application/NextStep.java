package com.inspien.assignment.cycle.application;

/**
 * 영수증·배송 주기에서 주문 하나를 처리한 뒤 할 일 (D-22).
 *
 * <p>성공·실패와는 다르다. 그 주문만의 실패(건별 실패)는 기록하고 다음 주문으로 넘어간다. 연결이 끊기거나 우리 쪽
 * 저장소(쇼핑몰 Oracle, H2)가 안 되면 남은 주문도 같은 이유로 실패하므로 그 주기를 끝낸다.
 */
public enum NextStep {

	/** 다음 주문으로 넘어간다 */
	NEXT_ORDER,

	/** 이번 주기를 끝낸다. 남은 주문은 다음 주기에 처리한다 */
	END_CYCLE

}
