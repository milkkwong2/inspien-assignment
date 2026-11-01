package com.inspien.assignment.inbound.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.inspien.assignment.global.persistence.BaseTimeEntity;

/**
 * H2 MESSAGE_ORDER (schema-h2.sql).
 */
@Entity
@Table(name = "MESSAGE_ORDER")
class MessageOrderEntity extends BaseTimeEntity {

	@Id
	@Column(name = "ORDER_ID")
	private String orderId;

	@Column(name = "MESSAGE_ID")
	private String messageId;

	@Column(name = "SEQ")
	private int seq;

	@Column(name = "USER_ID")
	private String userId;

	@Column(name = "ITEM_ID")
	private String itemId;

	@Column(name = "RECEIVED_AT")
	private Instant receivedAt;

	protected MessageOrderEntity() {
	}

	MessageOrderEntity(String orderId, String messageId, int seq, String userId, String itemId, Instant receivedAt) {
		this.orderId = orderId;
		this.messageId = messageId;
		this.seq = seq;
		this.userId = userId;
		this.itemId = itemId;
		this.receivedAt = receivedAt;
	}

	String orderId() {
		return this.orderId;
	}

	String messageId() {
		return this.messageId;
	}

	int seq() {
		return this.seq;
	}

	String userId() {
		return this.userId;
	}

	String itemId() {
		return this.itemId;
	}

	Instant receivedAt() {
		return this.receivedAt;
	}

}
