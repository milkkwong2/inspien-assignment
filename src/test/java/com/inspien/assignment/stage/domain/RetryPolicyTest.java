package com.inspien.assignment.stage.domain;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

	final RetryPolicy policy = new RetryPolicy(5, Duration.ofMinutes(1), 2);

	@Test
	void backsOffOneTwoFourEightMinutesThenStops() {
		Instant firstFailure = Instant.parse("2026-11-01T00:00:00Z");

		assertThat(this.policy.nextAttemptAt(firstFailure, 1)).isEqualTo(firstFailure.plusSeconds(60));
		assertThat(this.policy.nextAttemptAt(firstFailure, 2)).isEqualTo(firstFailure.plusSeconds(120));
		assertThat(this.policy.nextAttemptAt(firstFailure, 3)).isEqualTo(firstFailure.plusSeconds(240));
		assertThat(this.policy.nextAttemptAt(firstFailure, 4)).isEqualTo(firstFailure.plusSeconds(480));
		assertThat(this.policy.nextAttemptAt(firstFailure, 5)).isNull();
		assertThat(this.policy.exhausted(4)).isFalse();
		assertThat(this.policy.exhausted(5)).isTrue();
	}

}
