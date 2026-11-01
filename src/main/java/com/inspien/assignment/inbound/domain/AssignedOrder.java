package com.inspien.assignment.inbound.domain;

import java.time.Instant;

/**
 * 메시지 안 ITEM에 미리 배정한 주문번호 (H2 MESSAGE_ORDER).
 *
 * <p>Oracle에 넣기 전에 H2에 적어 둔다. 재처리 때 새 번호를 따지 않고 같은 번호를 다시 써서, 지난번에 실제로 저장됐는지
 * 조회로 확인할 수 있게 한다 (D-05, D-16).
 *
 * @param orderId 주문번호
 * @param messageId 메시지 ID
 * @param seq 메시지 안 ITEM 순서
 * @param userId ITEM의 USER_ID
 * @param itemId ITEM의 ITEM_ID
 * @param receivedAt 메시지를 처음 받은 시각. 영수증의 수신일시가 된다 (D-09)
 */
public record AssignedOrder(String orderId, String messageId, int seq, String userId, String itemId,
		Instant receivedAt) {

}
