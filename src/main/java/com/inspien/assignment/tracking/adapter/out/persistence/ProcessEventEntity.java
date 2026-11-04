package com.inspien.assignment.tracking.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.inspien.assignment.global.event.EventStage;
import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.global.persistence.BaseTimeEntity;

/**
 * H2 PROCESS_EVENT (schema-h2.sql). STAGE·RESULT는 enum 이름 그대로 저장한다.
 */
@Entity
@Table(name = "PROCESS_EVENT")
class ProcessEventEntity extends BaseTimeEntity {

	@Id
	@Column(name = "ID")
	private String id;

	@Column(name = "OCCURRED_AT")
	private Instant occurredAt;

	@Column(name = "MESSAGE_ID")
	private String messageId;

	@Column(name = "ORDER_ID")
	private String orderId;

	@Enumerated(EnumType.STRING)
	@Column(name = "STAGE")
	private EventStage stage;

	@Enumerated(EnumType.STRING)
	@Column(name = "RESULT")
	private ProcessEvent.Result result;

	@Column(name = "DETAIL")
	private String detail;

	protected ProcessEventEntity() {
	}

	ProcessEventEntity(String id, Instant occurredAt, String messageId, String orderId, EventStage stage,
			ProcessEvent.Result result, String detail) {
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

	EventStage stage() {
		return this.stage;
	}

	ProcessEvent.Result result() {
		return this.result;
	}

	String detail() {
		return this.detail;
	}

}
