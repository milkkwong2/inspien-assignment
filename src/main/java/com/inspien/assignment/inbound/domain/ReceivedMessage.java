package com.inspien.assignment.inbound.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 받은 메시지 1건의 기록 (H2 RECEIVED_MESSAGE).
 *
 * @param id 메시지 ID (TSID)
 * @param key 메시지 키 (본문 SHA-256)
 * @param receivedAt 처음 도착한 시각
 * @param contentType 받은 Content-Type
 * @param rawBody 받은 바이트 그대로. 재처리 때 다시 파싱한다
 * @param state 처리 상태
 * @param responseStatus 보낸 응답 코드 (처리 중이면 null)
 * @param completedAt 완료 시각 (완료 전이면 null)
 */
public record ReceivedMessage(String id, MessageKey key, Instant receivedAt, String contentType, byte[] rawBody,
		MessageState state, Integer responseStatus, Instant completedAt) {

	/**
	 * 같은 본문이 다시 왔을 때 어떻게 처리할지 (D-24).
	 *
	 * <pre>
	 * COMPLETED, 완료 후 window 이내 (도착 시각 기준)  → 이전 결과를 다시 보냄
	 * FAILED(400 제외) 또는 PROCESSING, 시간 무관      → 같은 기록을 이어서 처리
	 * 그 외 (COMPLETED window 지남, FAILED 400)         → 새 메시지
	 * </pre>
	 *
	 * 도착 시각으로 비교한다. 잠금을 얻은 시각으로 비교하면 잠금을 기다린 재전송이 새 주문으로 처리될 수 있다.
	 */
	public Handling handlingOf(Instant arrivedAt, Duration window) {
		return switch (this.state) {
			case COMPLETED -> arrivedAt.isAfter(this.completedAt.plus(window)) ? Handling.NEW : Handling.REPLAY;
			case PROCESSING -> Handling.RESUME;
			case FAILED -> (this.responseStatus != null && this.responseStatus == 400) ? Handling.NEW : Handling.RESUME;
		};
	}

	public enum Handling {

		REPLAY, RESUME, NEW

	}

}
