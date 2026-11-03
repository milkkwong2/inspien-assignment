package com.inspien.assignment.retry.adapter.out.persistence;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import com.inspien.assignment.global.persistence.BaseTimeEntity;

/**
 * H2 ORDER_STAGE (schema-h2.sql).
 */
@Entity
@Table(name = "ORDER_STAGE")
@IdClass(OrderStageEntity.Key.class)
class OrderStageEntity extends BaseTimeEntity {

	@Id
	@Column(name = "ORDER_ID")
	private String orderId;

	@Id
	@Column(name = "STAGE")
	private String stage;

	@Column(name = "ATTEMPTS")
	private int attempts;

	@Column(name = "NEXT_ATTEMPT_AT")
	private Instant nextAttemptAt;

	@Column(name = "REACHED_FAILED")
	private boolean reachedFailed;

	@Column(name = "LAST_ERROR")
	private String lastError;

	@Column(name = "ARTIFACT")
	private String artifact;

	@Column(name = "COMPLETED_AT")
	private Instant completedAt;

	protected OrderStageEntity() {
	}

	OrderStageEntity(String orderId, String stage) {
		this.orderId = orderId;
		this.stage = stage;
	}

	void update(int attempts, Instant nextAttemptAt, boolean reachedFailed, String lastError, String artifact,
			Instant completedAt) {
		this.attempts = attempts;
		this.nextAttemptAt = nextAttemptAt;
		this.reachedFailed = reachedFailed;
		this.lastError = lastError;
		this.artifact = artifact;
		this.completedAt = completedAt;
	}

	String orderId() {
		return this.orderId;
	}

	String stage() {
		return this.stage;
	}

	int attempts() {
		return this.attempts;
	}

	Instant nextAttemptAt() {
		return this.nextAttemptAt;
	}

	boolean reachedFailed() {
		return this.reachedFailed;
	}

	String lastError() {
		return this.lastError;
	}

	String artifact() {
		return this.artifact;
	}

	Instant completedAt() {
		return this.completedAt;
	}

	static class Key implements Serializable {

		private String orderId;

		private String stage;

		Key() {
		}

		Key(String orderId, String stage) {
			this.orderId = orderId;
			this.stage = stage;
		}

		@Override
		public boolean equals(Object object) {
			return object instanceof Key other && Objects.equals(this.orderId, other.orderId)
					&& Objects.equals(this.stage, other.stage);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.orderId, this.stage);
		}

	}

}
