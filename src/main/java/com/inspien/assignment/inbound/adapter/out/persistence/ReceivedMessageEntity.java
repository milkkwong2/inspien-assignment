package com.inspien.assignment.inbound.adapter.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import com.inspien.assignment.global.persistence.BaseTimeEntity;
import com.inspien.assignment.inbound.domain.MessageState;

/**
 * H2 RECEIVED_MESSAGE (schema-h2.sql). STATE는 enum 이름(PROCESSING·COMPLETED·FAILED) 그대로 저장한다.
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

	/**
	 * 받은 원문(최대 1MB). 엔티티를 읽으면 함께 읽힌다. 지연 로딩은 바이트코드 향상이 있어야 하는데 켜지 않았다. 그래서 메시지
	 * 목록은 필요한 칸만 프로젝션으로 읽어 이 칸을 피한다.
	 */
	@Lob
	@Column(name = "RAW_BODY")
	private byte[] rawBody;

	@Enumerated(EnumType.STRING)
	@Column(name = "STATE")
	private MessageState state;

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
			MessageState state) {
		this.id = id;
		this.messageKey = messageKey;
		this.receivedAt = receivedAt;
		this.contentType = contentType;
		this.rawBody = rawBody;
		this.state = state;
	}

	void processing() {
		this.state = MessageState.PROCESSING;
	}

	/**
	 * 저장과 응답 기록까지 끝남. 완료 시각은 중복 판단 1분 창의 기준이다 (D-24).
	 */
	void complete(int responseStatus, String responseBody, Instant completedAt) {
		this.state = MessageState.COMPLETED;
		this.responseStatus = responseStatus;
		this.responseBody = responseBody;
		this.completedAt = completedAt;
	}

	/**
	 * 실패. 완료 시각은 비운다: 실패한 메시지는 시간 창 없이 이어서 처리한다 (D-24).
	 */
	void fail(int responseStatus, String responseBody) {
		this.state = MessageState.FAILED;
		this.responseStatus = responseStatus;
		this.responseBody = responseBody;
		this.completedAt = null;
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

	MessageState state() {
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
