package com.inspien.assignment.global.event;

import java.time.Instant;

/**
 * 처리 이벤트 1건 (D-13, D-22). 각 도메인이 Spring 이벤트로 발행하고 tracking이 받아 H2 PROCESS_EVENT에 남긴다.
 *
 * <p>이벤트로 발행하는 이유: tracking은 조회 API 때문에 inbound·order를 읽는다. inbound가 tracking을 직접 부르면 서로
 * 부르는 순환이 된다. 이력은 최선 노력이라, 기록이 실패해도 업무 처리를 막지 않는다 (D-04).
 *
 * @param occurredAt 일어난 시각
 * @param messageId 수신 메시지 ID (주기 이벤트는 없음)
 * @param orderId 주문번호 (거부·주기 이벤트는 없음)
 * @param stage 단계
 * @param result 결과
 * @param detail 사람이 읽는 상세
 */
public record ProcessEvent(Instant occurredAt, String messageId, String orderId, Stage stage, Result result,
		String detail) {

	/**
	 * 기록에 실패했을 때 로그에 남길 요약. 무엇이 빠졌는지 찾아갈 식별자만 담는다. detail에는 쇼핑몰 회원 ID나 상대 시스템의
	 * 오류 문구가 들어갈 수 있어 로그에 남기지 않는다.
	 */
	public String logSummary() {
		return "stage=" + this.stage + ", result=" + this.result + ", messageId=" + this.messageId + ", orderId="
				+ this.orderId + ", occurredAt=" + this.occurredAt;
	}

	/**
	 * CLOSE는 마감(STATUS N→Y)이다 (D-26).
	 */
	public enum Stage {

		RECEIVE, RECEIPT, SHIPMENT, CLOSE

	}

	/**
	 * 설계서 §4.2의 PROCESS_EVENT.RESULT 값.
	 */
	public enum Result {

		SUCCESS, FAIL, SKIP, REJECT, WARN, OPERATOR_RESET, CYCLE_SKIPPED, CONNECTION_RECOVERED, MISSING_RECORD

	}

}
