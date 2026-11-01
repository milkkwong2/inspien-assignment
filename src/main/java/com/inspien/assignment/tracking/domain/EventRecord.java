package com.inspien.assignment.tracking.domain;

import java.time.Instant;

/**
 * 처리 이벤트 1건 (H2 PROCESS_EVENT). 추가만 한다 (D-13).
 *
 * @param id 이벤트 ID (TSID, 시간순)
 * @param occurredAt 일어난 시각
 * @param messageId 수신 메시지 ID
 * @param orderId 주문번호
 * @param stage RECEIVE, RECEIPT, SHIPMENT
 * @param result 설계서 §4.2의 RESULT 값
 * @param detail 상세
 */
public record EventRecord(String id, Instant occurredAt, String messageId, String orderId, String stage,
		String result, String detail) {

	static final int MAX_DETAIL = 2000;

	public EventRecord {
		if (detail != null && detail.length() > MAX_DETAIL) {
			detail = detail.substring(0, MAX_DETAIL);
		}
	}

}
