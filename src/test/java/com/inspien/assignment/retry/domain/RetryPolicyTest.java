package com.inspien.assignment.retry.domain;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

	final RetryPolicy policy = new RetryPolicy(5, Duration.ofMinutes(1), 2);

	@Test
	void backsOffOneTwoFourEightMinutesThenStops() {
		Instant t = Instant.parse("2026-11-01T00:00:00Z");

		assertThat(this.policy.nextAttemptAt(t, 1)).isEqualTo(t.plusSeconds(60));
		assertThat(this.policy.nextAttemptAt(t, 2)).isEqualTo(t.plusSeconds(120));
		assertThat(this.policy.nextAttemptAt(t, 3)).isEqualTo(t.plusSeconds(240));
		assertThat(this.policy.nextAttemptAt(t, 4)).isEqualTo(t.plusSeconds(480));
		assertThat(this.policy.nextAttemptAt(t, 5)).isNull();
		assertThat(this.policy.exhausted(4)).isFalse();
		assertThat(this.policy.exhausted(5)).isTrue();
	}

}
