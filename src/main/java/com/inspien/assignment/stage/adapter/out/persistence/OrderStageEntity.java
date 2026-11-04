package com.inspien.assignment.stage.adapter.out.persistence;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import com.inspien.assignment.global.persistence.BaseTimeEntity;
import com.inspien.assignment.stage.domain.Stage;
import com.inspien.assignment.stage.domain.StageState;

/**
 * H2 ORDER_STAGE (schema-h2.sql). STAGE는 enum 이름(RECEIPT·SHIPMENT) 그대로 저장한다.
 */
@Entity
@Table(name = "ORDER_STAGE")
@IdClass(OrderStageEntity.Key.class)
class OrderStageEntity extends BaseTimeEntity {

	@Id
	@Column(name = "ORDER_ID")
	private String orderId;

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "STAGE")
	private Stage stage;

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

	OrderStageEntity(String orderId, Stage stage) {
		this.orderId = orderId;
		this.stage = stage;
	}

	/**
	 * 도메인이 계산한 진행 상태를 그대로 옮긴다. 키(orderId·stage)는 바꾸지 않는다.
	 */
	void apply(StageState state) {
		this.attempts = state.attempts();
		this.nextAttemptAt = state.nextAttemptAt();
		this.reachedFailed = state.reachedFailed();
		this.lastError = state.lastError();
		this.artifact = state.artifact();
		this.completedAt = state.completedAt();
	}

	String orderId() {
		return this.orderId;
	}

	Stage stage() {
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

		private Stage stage;

		Key() {
		}

		Key(String orderId, Stage stage) {
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
