package com.inspien.assignment.inbound.domain;

/**
 * RECEIVED_MESSAGE.STATE (D-16).
 */
public enum MessageState {

	/** 처리 중. 같은 메시지 키의 잠금을 얻은 뒤에도 보이면 이전 처리가 중간에 중단된 것이다 */
	PROCESSING,

	/** 저장과 응답 기록까지 끝남 */
	COMPLETED,

	/** 실패. 응답 코드를 함께 기록한다 */
	FAILED;

	/**
	 * 조회 파라미터를 읽는다. 모르는 값이면 null.
	 */
	public static MessageState parse(String value) {
		for (MessageState state : values()) {
			if (state.name().equals(value)) {
				return state;
			}
		}
		return null;
	}

}
