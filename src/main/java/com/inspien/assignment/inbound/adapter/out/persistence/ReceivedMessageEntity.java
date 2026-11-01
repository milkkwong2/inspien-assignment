package com.inspien.assignment.inbound.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import com.inspien.assignment.global.persistence.BaseTimeEntity;

/**
 * H2 RECEIVED_MESSAGE (schema-h2.sql).
 */
@Entity
@Table(name = "RECEIVED_MESSAGE")
class ReceivedMessageEntity extends BaseTimeEntity {

	@Id
	@Column(name = "ID")
	private String id;

	@Column(name = "MESSAGE_KEY")
	private String messageKey;

	@Column(name = "RECEIVED_AT")
	private Instant receivedAt;

	@Column(name = "CONTENT_TYPE")
	private String contentType;

	@Lob
	@Basic(fetch = FetchType.LAZY)
	@Column(name = "RAW_BODY")
	private byte[] rawBody;

	@Column(name = "STATE")
	private String state;

	@Column(name = "RESPONSE_STATUS")
	private Integer responseStatus;

	@Lob
	@Column(name = "RESPONSE_BODY")
	private String responseBody;

	@Column(name = "COMPLETED_AT")
	private Instant completedAt;

	protected ReceivedMessageEntity() {
	}

	ReceivedMessageEntity(String id, String messageKey, Instant receivedAt, String contentType, byte[] rawBody,
			String state) {
		this.id = id;
		this.messageKey = messageKey;
		this.receivedAt = receivedAt;
		this.contentType = contentType;
		this.rawBody = rawBody;
		this.state = state;
	}

	void processing() {
		this.state = "PROCESSING";
	}

	void finish(String state, int responseStatus, String responseBody, Instant completedAt) {
		this.state = state;
		this.responseStatus = responseStatus;
		this.responseBody = responseBody;
		this.completedAt = completedAt;
	}

	String id() {
		return this.id;
	}

	String messageKey() {
		return this.messageKey;
	}

	Instant receivedAt() {
		return this.receivedAt;
	}

	String contentType() {
		return this.contentType;
	}

	byte[] rawBody() {
		return this.rawBody;
	}

	String state() {
		return this.state;
	}

	Integer responseStatus() {
		return this.responseStatus;
	}

	String responseBody() {
		return this.responseBody;
	}

	Instant completedAt() {
		return this.completedAt;
	}

}
