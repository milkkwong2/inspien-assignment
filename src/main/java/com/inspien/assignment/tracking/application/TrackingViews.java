package com.inspien.assignment.tracking.application;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.inspien.assignment.inbound.application.ReceiveResult;

/**
 * 조회 API와 재처리 API 응답의 data (설계서 §5.4). 필드 이름은 camelCase, 시각은 +09:00, 값이 없는 필드는 생략한다.
 */
public final class TrackingViews {

	private TrackingViews() {
	}

	/**
	 * 이 주문 어디까지 갔나: 연계 완료 플래그 STATUS(Oracle N/Y) + 단계별 상태(H2) + 이벤트 (D-26).
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OrderDetail(String orderId, String status, String messageId, OffsetDateTime receivedAt,
			String userId, String itemId, StageView receipt, StageView shipment, List<EventView> events) {
	}

	/**
	 * 목록 한 줄. lastError는 끝나지 않은 단계의 마지막 오류다 (영수증 먼저).
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OrderSummary(String orderId, String status, String receipt, String shipment, String messageId,
			OffsetDateTime receivedAt, String lastError) {
	}

	/**
	 * 단계 하나의 상태(PENDING·DONE·FAILED)와 재시도 정보. 영수증은 fileName, 배송은 shipmentId가 결과물이다.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record StageView(String status, int attempts, OffsetDateTime nextAttemptAt, OffsetDateTime completedAt,
			String lastError, String fileName, String shipmentId) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EventView(OffsetDateTime occurredAt, String stage, String result, String detail, String messageId,
			String orderId) {
	}

	/**
	 * 보낸 메시지가 어떻게 됐나. 완료면 수신 응답과 같은 목록, 실패면 오류 코드와 detail.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record MessageDetail(String messageId, String state, Integer responseStatus, OffsetDateTime receivedAt,
			OffsetDateTime completedAt, String errorCode, String errorDetail, ReceiveResult.Summary summary,
			List<ReceiveResult.Stored> stored, List<ReceiveResult.Rejected> rejected,
			List<ReceiveResult.Warning> warnings, List<EventView> events) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record MessageSummaryView(String messageId, String state, Integer responseStatus,
			OffsetDateTime receivedAt, String errorCode) {
	}

	/**
	 * 재처리 결과: 다시 시작한 단계 (RECEIPT, SHIPMENT).
	 */
	public record RetryView(String orderId, List<String> restarted) {
	}

	/**
	 * 목록 한 페이지. nextAfter가 있으면 다음 페이지가 있다.
	 */
	public record Page<T>(List<T> items, String nextAfter, int limit) {
	}

}
