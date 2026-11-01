package com.inspien.assignment.order.domain;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ORDER_TB.STATUS 값. 작업 큐는 이 컬럼 하나다 (설계서 §6, D-04, D-06).
 *
 * <pre>
 * RECEIVED ──영수증──▶ RECEIPT_SENT ──배송──▶ SHIPPED
 *    │                     │
 *    ▼                     ▼
 * RECEIPT_FAILED       SHIPMENT_FAILED     (사람이 확인. 운영자가 앞 상태로 되돌린다)
 * </pre>
 */
public enum OrderStatus {

	/** 주문 저장됨, 영수증 대기 */
	RECEIVED,

	/** 영수증 업로드됨, 배송 대기 */
	RECEIPT_SENT,

	/** 배송 등록됨, 완료 */
	SHIPPED,

	/** 영수증 자동 처리 멈춤 (재시도 한도 초과, 또는 수신 기록 없음) */
	RECEIPT_FAILED,

	/** 배송 자동 처리 멈춤 (재시도 한도 초과, 또는 수신 기록 없음) */
	SHIPMENT_FAILED;

	/**
	 * 앱이 하는 전이. 운영자 되돌림(*_FAILED → 앞 상태)은 사람이 Oracle에서 직접 하므로 여기 없다.
	 */
	private static final Map<OrderStatus, Set<OrderStatus>> APP_TRANSITIONS = Map.of(RECEIVED,
			Set.of(RECEIPT_SENT, RECEIPT_FAILED), RECEIPT_SENT, Set.of(SHIPPED, SHIPMENT_FAILED));

	public boolean canMoveTo(OrderStatus next) {
		return APP_TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
	}

	/**
	 * DB 값을 읽는다. 운영자가 손으로 넣은 값처럼 알 수 없는 값이면 비어 있다.
	 */
	public static Optional<OrderStatus> parse(String value) {
		for (OrderStatus status : values()) {
			if (status.name().equals(value)) {
				return Optional.of(status);
			}
		}
		return Optional.empty();
	}

}
