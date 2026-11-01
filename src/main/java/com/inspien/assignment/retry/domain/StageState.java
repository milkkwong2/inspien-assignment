package com.inspien.assignment.retry.domain;

import java.time.Instant;

/**
 * 주문 × 단계의 재시도 상태 (H2 ORDER_STAGE). 진행 상태 자체는 Oracle STATUS가 기준이고, 여기는 횟수와 다음 시도
 * 시각만 둔다 (D-04, D-10).
 *
 * @param orderId 주문번호
 * @param stage 단계
 * @param attempts 건별 실패 횟수
 * @param nextAttemptAt 다음 시도 시각 (없으면 바로)
 * @param reachedFailed *_FAILED까지 갔었는가. 다시 대상에 나타나면 운영자가 되돌린 것이다
 * @param lastError 마지막 오류
 * @param artifact 결과물: 영수증 파일명 또는 미리 배정한 SHIPMENT_ID
 * @param updatedAt 마지막 갱신
 */
public record StageState(String orderId, Stage stage, int attempts, Instant nextAttemptAt, boolean reachedFailed,
		String lastError, String artifact, Instant updatedAt) {

	public static StageState initial(String orderId, Stage stage, Instant now) {
		return new StageState(orderId, stage, 0, null, false, null, null, now);
	}

	public boolean waitingUntilAfter(Instant now) {
		return this.nextAttemptAt != null && this.nextAttemptAt.isAfter(now);
	}

	/**
	 * 운영자 되돌림: 횟수를 0부터 다시 센다. 미리 배정한 결과물(SHIPMENT_ID)은 그대로 둔다.
	 */
	public StageState resetByOperator(Instant now) {
		return new StageState(this.orderId, this.stage, 0, null, false, null, this.artifact, now);
	}

	public StageState failed(String error, Instant nextAttemptAt, Instant now) {
		return new StageState(this.orderId, this.stage, this.attempts + 1, nextAttemptAt, this.reachedFailed, error,
				this.artifact, now);
	}

	public StageState succeeded(String artifact, Instant now) {
		return new StageState(this.orderId, this.stage, this.attempts, null, false, null,
				(artifact != null) ? artifact : this.artifact, now);
	}

	public StageState withArtifact(String artifact, Instant now) {
		return new StageState(this.orderId, this.stage, this.attempts, this.nextAttemptAt, this.reachedFailed,
				this.lastError, artifact, now);
	}

}
