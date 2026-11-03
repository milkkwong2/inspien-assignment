package com.inspien.assignment.stage.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.inspien.assignment.global.config.SchedulerProperties;
import com.inspien.assignment.stage.domain.RetryPolicy;

/**
 * 재시도 정책을 설정으로 만들고, 스케줄러 주기와 맞는지 기동할 때 본다 (D-10, D-12).
 *
 * <p>두 설정이 다른 레코드에 있어 레코드 생성자로는 볼 수 없다. 정책을 만드는 곳이 정책이 기대는 전제를 확인한다.
 */
@Configuration(proxyBeanMethods = false)
class RetryPolicyConfig {

	/**
	 * 주기가 가장 짧은 대기(첫 대기)보다 짧아야 한다. 같거나 길면 대기가 끝난 주문도 다음 주기까지 기다려 1·2·4·8분 일정이
	 * 주기 단위로 밀린다.
	 */
	@Bean
	RetryPolicy retryPolicy(RetryProperties retry, SchedulerProperties scheduler) {
		if (scheduler.fixedDelay().compareTo(retry.initialBackoff()) >= 0) {
			throw new IllegalStateException(
					"app.scheduler.fixed-delay(%s)는 app.retry.initial-backoff(%s)보다 짧아야 합니다 (D-12)"
						.formatted(scheduler.fixedDelay(), retry.initialBackoff()));
		}
		return new RetryPolicy(retry.maxAttempts(), retry.initialBackoff(), retry.multiplier());
	}

}
