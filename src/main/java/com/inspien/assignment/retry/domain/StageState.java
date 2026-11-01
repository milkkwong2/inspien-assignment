package com.inspien.assignment.retry.domain;

import java.time.Instant;

/**
 * 주문 × 단계의 진행 상태 (H2 ORDER_STAGE). 이력이 아니라 진행 상태다. 단계 완료와 "사람이 확인"은 Oracle에 담을 곳이
 * 없어 여기에만 있다 (D-26).
 *
 * @param orderId 주문번호
 * @param stage 단계
 * @param attempts 건별 실패 횟수
 * @param nextAttemptAt 다음 시도 시각 (없으면 바로)
 * @param reachedFailed 사람이 확인: 자동 처리 멈춤
 * @param lastError 마지막 오류
 * @param artifact 결과물: 영수증 파일명 또는 미리 배정한 SHIPMENT_ID
 * @param completedAt 단계 완료 시각
 */
public record StageState(String orderId, Stage stage, int attempts, Instant nextAttemptAt, boolean reachedFailed,
		String lastError, String artifact, Instant completedAt) {

	public static StageState initial(String orderId, Stage stage) {
		return new StageState(orderId, stage, 0, null, false, null, null, null);
	}

	/**
	 * 행이 없으면 PENDING이다.
	 */
	public static StageStatus statusOf(StageState state) {
		return (state != null) ? state.status() : StageStatus.PENDING;
	}

	public StageStatus status() {
		if (this.completedAt != null) {
			return StageStatus.DONE;
		}
		return this.reachedFailed ? StageStatus.FAILED : StageStatus.PENDING;
	}

	/**
	 * 이번 주기에 처리할 차례인가: PENDING이고 백오프 중이 아니다.
	 */
	public boolean dueAt(Instant now) {
		return status() == StageStatus.PENDING && (this.nextAttemptAt == null || !this.nextAttemptAt.isAfter(now));
	}

	/**
	 * 건별 실패 1회. 한도에 닿았으면 다음 시도 없이 표시까지 한 번에 바꾼다 (D-26).
	 */
	public StageState failed(String error, Instant nextAttemptAt, boolean exhausted) {
		return new StageState(this.orderId, this.stage, this.attempts + 1, exhausted ? null : nextAttemptAt, exhausted,
				error, this.artifact, null);
	}

	/**
	 * 다시 해도 결과가 같은 실패: 횟수를 세지 않고 바로 사람에게 넘긴다.
	 */
	public StageState markedFailed(String error) {
		return new StageState(this.orderId, this.stage, this.attempts, null, true, error, this.artifact, null);
	}

	public StageState completed(String artifact, Instant at) {
		return new StageState(this.orderId, this.stage, this.attempts, null, false, null,
				(artifact != null) ? artifact : this.artifact, at);
	}

	public StageState withArtifact(String artifact) {
		return new StageState(this.orderId, this.stage, this.attempts, this.nextAttemptAt, this.reachedFailed,
				this.lastError, artifact, this.completedAt);
	}

}
