package com.inspien.assignment.inbound.application;

import java.time.Instant;

import com.inspien.assignment.inbound.domain.MessageState;

/**
 * 조회 API 메시지 목록의 한 줄 (D-13). 목록에 보여 줄 칸만 담는다. 수신 결과와 detail은 상세({@link MessageSummary})에서 본다.
 *
 * @param messageId 메시지 ID
 * @param state 처리 상태
 * @param responseStatus 보낸 응답 코드 (처리 중이면 null)
 * @param receivedAt 처음 받은 시각
 * @param errorCode 실패했으면 오류 코드
 */
public record MessageListItem(String messageId, MessageState state, Integer responseStatus, Instant receivedAt,
		String errorCode) {

}
