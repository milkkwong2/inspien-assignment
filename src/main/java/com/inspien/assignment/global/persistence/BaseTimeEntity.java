package com.inspien.assignment.global.persistence;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * H2 엔티티 공통 시각 필드 (D-27). 행이 언제 생기고 마지막으로 언제 바뀌었나를 모든 테이블에서 같은 이름으로 본다.
 *
 * <p>업무 시각(RECEIVED_AT, COMPLETED_AT, OCCURRED_AT 등)과 다르다. 업무 시각은 앱의 Clock으로 채우고 판단에 쓰고, 이
 * 필드는 JVM 시각으로 채우며 판단에 쓰지 않는다. 벌크 UPDATE는 콜백이 돌지 않으므로 UPDATED_AT을 직접 넣는다.
 */
@MappedSuperclass
public abstract class BaseTimeEntity {

	@Column(name = "CREATED_AT", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "UPDATED_AT", nullable = false)
	private OffsetDateTime updatedAt;

	@PrePersist
	void onCreate() {
		OffsetDateTime now = OffsetDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = OffsetDateTime.now();
	}

	public OffsetDateTime createdAt() {
		return this.createdAt;
	}

	public OffsetDateTime updatedAt() {
		return this.updatedAt;
	}

}
