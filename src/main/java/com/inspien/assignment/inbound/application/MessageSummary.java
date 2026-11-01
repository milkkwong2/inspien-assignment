package com.inspien.assignment.inbound.application;

import java.time.Instant;

import com.inspien.assignment.inbound.domain.MessageState;

/**
 * 조회 API에 보여 줄 수신 메시지 요약 (D-13).
 *
 * @param messageId 메시지 ID
 * @param state 처리 상태
 * @param responseStatus 보낸 응답 코드 (처리 중이면 null)
 * @param receivedAt 처음 받은 시각
 * @param completedAt 완료 시각
 * @param errorCode 실패했으면 오류 코드
 * @param errorDetail 실패했으면 송신자에게 보낸 detail
 * @param result 완료했으면 수신 결과 (저장·거부·경고 목록)
 */
public record MessageSummary(String messageId, MessageState state, Integer responseStatus, Instant receivedAt,
		Instant completedAt, String errorCode, String errorDetail, ReceiveResult result) {

}
