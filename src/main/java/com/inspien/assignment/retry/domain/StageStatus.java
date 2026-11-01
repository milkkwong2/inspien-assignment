package com.inspien.assignment.retry.domain;

/**
 * 단계 상태 (설계서 §6, D-26). 저장하지 않고 ORDER_STAGE의 COMPLETED_AT·REACHED_FAILED에서 계산한다.
 *
 * <pre>
 * PENDING ──업로드·등록 성공──▶ DONE
 *    │  ▲
 *    ▼  └──재처리 API
 * FAILED  (5회째 실패, 또는 수신 기록 없음)
 * </pre>
 */
public enum StageStatus {

	/** 처리 대기, 또는 백오프 중. 그 단계의 스케줄러가 처리한다 */
	PENDING,

	/** 업로드·등록 완료 */
	DONE,

	/** 자동 처리 멈춤. 사람이 확인한다 */
	FAILED;

	/**
	 * DB 값이 아니라 조회 파라미터를 읽는다. 모르는 값이면 null.
	 */
	public static StageStatus parse(String value) {
		for (StageStatus status : values()) {
			if (status.name().equals(value)) {
				return status;
			}
		}
		return null;
	}

}
