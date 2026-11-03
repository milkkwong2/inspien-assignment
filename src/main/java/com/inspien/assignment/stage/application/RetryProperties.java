package com.inspien.assignment.stage.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.inspien.assignment.global.config.PropertyChecks;

/**
 * 건별 실패 재시도 (D-10). 대기는 initialBackoff × multiplier^(n-1): 1·2·4·8분.
 *
 * <p>배수가 1 이상이어야 첫 대기가 가장 짧은 대기다. 스케줄러 주기와의 관계는 {@link RetryPolicyConfig}가 본다 (D-12).
 *
 * @param maxAttempts 첫 시도 포함 최대 시도 횟수
 * @param initialBackoff 첫 대기
 * @param multiplier 배수
 */
@ConfigurationProperties("app.retry")
public record RetryProperties(int maxAttempts, Duration initialBackoff, int multiplier) {

	public RetryProperties {
		PropertyChecks.positive("app.retry.max-attempts", maxAttempts);
		PropertyChecks.positive("app.retry.initial-backoff", initialBackoff);
		PropertyChecks.positive("app.retry.multiplier", multiplier);
	}

}
