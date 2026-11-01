package com.inspien.assignment.retry.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 건별 실패의 지수 백오프 (D-10). 기본: 첫 시도 포함 5회, 대기 1·2·4·8분.
 *
 * @param maxAttempts 첫 시도 포함 최대 시도 횟수
 * @param initialBackoff 첫 대기
 * @param multiplier 배수
 */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, int multiplier) {

	/**
	 * @param attempts 이번 실패까지 센 횟수 (1부터)
	 */
	public boolean exhausted(int attempts) {
		return attempts >= this.maxAttempts;
	}

	/**
	 * @param attempts 이번 실패까지 센 횟수 (1부터)
	 * @return 다음 시도 시각. 한도에 닿았으면 null
	 */
	public Instant nextAttemptAt(Instant failedAt, int attempts) {
		if (exhausted(attempts)) {
			return null;
		}
		long factor = (long) Math.pow(this.multiplier, attempts - 1);
		return failedAt.plus(this.initialBackoff.multipliedBy(factor));
	}

}
