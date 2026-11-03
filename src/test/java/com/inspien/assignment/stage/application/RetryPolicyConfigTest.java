package com.inspien.assignment.stage.application;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.inspien.assignment.global.config.SchedulerProperties;
import com.inspien.assignment.stage.domain.RetryPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스케줄러 주기는 가장 짧은 재시도 대기보다 짧아야 한다 (D-12). 두 값은 다른 설정 레코드에 있어 정책을 만들 때 본다.
 */
class RetryPolicyConfigTest {

	final RetryProperties retry = new RetryProperties(5, Duration.ofMinutes(1), 2);

	@Test
	void buildsPolicyWhenCycleIsShorterThanFirstBackoff() {
		RetryPolicy policy = new RetryPolicyConfig().retryPolicy(this.retry, scheduler(Duration.ofSeconds(30)));

		assertThat(policy).isEqualTo(new RetryPolicy(5, Duration.ofMinutes(1), 2));
	}

	@Test
	void failsWhenCycleIsNotShorterThanFirstBackoff() {
		assertThatThrownBy(() -> new RetryPolicyConfig().retryPolicy(this.retry, scheduler(Duration.ofMinutes(1))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("app.scheduler.fixed-delay")
			.hasMessageContaining("app.retry.initial-backoff");
	}

	private static SchedulerProperties scheduler(Duration fixedDelay) {
		return new SchedulerProperties(true, fixedDelay, 100);
	}

}
