package com.inspien.assignment.tracking.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * H2 PROCESS_EVENT (schema-h2.sql).
 */
@Entity
@Table(name = "PROCESS_EVENT")
class ProcessEventEntity {

	@Id
	@Column(name = "ID")
	private String id;

	@Column(name = "OCCURRED_AT")
	private Instant occurredAt;

	@Column(name = "MESSAGE_ID")
	private String messageId;

	@Column(name = "ORDER_ID")
	private String orderId;

	@Column(name = "STAGE")
	private String stage;

	@Column(name = "RESULT")
	private String result;

	@Column(name = "DETAIL")
	private String detail;

	protected ProcessEventEntity() {
	}

	ProcessEventEntity(String id, Instant occurredAt, String messageId, String orderId, String stage, String result,
			String detail) {
		this.id = id;
		this.occurredAt = occurredAt;
		this.messageId = messageId;
		this.orderId = orderId;
		this.stage = stage;
		this.result = result;
		this.detail = detail;
	}

	String id() {
		return this.id;
	}

	Instant occurredAt() {
		return this.occurredAt;
	}

	String messageId() {
		return this.messageId;
	}

	String orderId() {
		return this.orderId;
	}

	String stage() {
		return this.stage;
	}

	String result() {
		return this.result;
	}

	String detail() {
		return this.detail;
	}

}
