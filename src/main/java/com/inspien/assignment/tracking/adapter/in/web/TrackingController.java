package com.inspien.assignment.tracking.adapter.in.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.inspien.assignment.global.error.ApiException;
import com.inspien.assignment.global.error.ErrorCode;
import com.inspien.assignment.global.web.ApiResponse;
import com.inspien.assignment.inbound.domain.MessageState;
import com.inspien.assignment.stage.domain.StageStatus;
import com.inspien.assignment.tracking.application.TrackingQueryFacade;
import com.inspien.assignment.tracking.application.TrackingViews;

/**
 * IF-04 처리 추적 조회 API (설계서 §5.4, D-18). 읽기 전용이다.
 *
 * <p>목록: limit 기본 50, 최대 200. 다음 페이지는 after에 page.nextAfter를 넣는다. from·to는 수신 시각 기준 ISO-8601이다
 * (예: 2026-10-31T00:00:00+09:00, URL에서는 +를 %2B로). 주문 목록의 receipt·shipment는 단계 상태(PENDING·DONE·FAILED)다
 * (D-26).
 *
 * <p>receipt·shipment·state는 Spring이 enum으로 바꾼다. 비우면 조건이 없고, 모르는 값이면 Spring의 400이 INVALID_REQUEST로
 * 나간다 (GlobalExceptionHandler).
 */
@RestController
@RequestMapping("/tracking")
class TrackingController {

	static final int DEFAULT_LIMIT = 50;

	static final int MAX_LIMIT = 200;

	private final TrackingQueryFacade tracking;

	TrackingController(TrackingQueryFacade tracking) {
		this.tracking = tracking;
	}

	@GetMapping("/orders/{orderId}")
	ApiResponse<TrackingViews.OrderDetail> order(@PathVariable String orderId) {
		TrackingViews.OrderDetail result = this.tracking.order(orderId);
		return ApiResponse.of(result);
	}

	@GetMapping("/orders")
	ApiResponse<List<TrackingViews.OrderSummary>> orders(@RequestParam(required = false) StageStatus receipt,
			@RequestParam(required = false) StageStatus shipment,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
			@RequestParam(required = false) String after, @RequestParam(required = false) Integer limit) {
		Instant fromAt = instant(from);
		Instant toAt = instant(to);
		int size = limit(limit);
		TrackingViews.Page<TrackingViews.OrderSummary> result = this.tracking.orders(receipt, shipment, fromAt, toAt,
				after, size);
		return page(result);
	}

	@GetMapping("/messages/{messageId}")
	ApiResponse<TrackingViews.MessageDetail> message(@PathVariable String messageId) {
		TrackingViews.MessageDetail result = this.tracking.message(messageId);
		return ApiResponse.of(result);
	}

	@GetMapping("/messages")
	ApiResponse<List<TrackingViews.MessageSummaryView>> messages(@RequestParam(required = false) MessageState state,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
			@RequestParam(required = false) String after, @RequestParam(required = false) Integer limit) {
		Instant fromAt = instant(from);
		Instant toAt = instant(to);
		int size = limit(limit);
		TrackingViews.Page<TrackingViews.MessageSummaryView> result = this.tracking.messages(state, fromAt, toAt,
				after, size);
		return page(result);
	}

	private static <T> ApiResponse<List<T>> page(TrackingViews.Page<T> page) {
		return ApiResponse.page(page.items(), page.nextAfter(), page.limit());
	}

	private static int limit(Integer limit) {
		if (limit == null) {
			return DEFAULT_LIMIT;
		}
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new ApiException(ErrorCode.INVALID_REQUEST, "limit은 1~" + MAX_LIMIT + "이어야 합니다: " + limit);
		}
		return limit;
	}

	private static Instant instant(OffsetDateTime time) {
		return (time != null) ? time.toInstant() : null;
	}

}
