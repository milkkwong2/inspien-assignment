package com.inspien.assignment.tracking.adapter.in.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;

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
import com.inspien.assignment.retry.domain.StageStatus;
import com.inspien.assignment.tracking.application.TrackingQueryService;
import com.inspien.assignment.tracking.application.TrackingViews;

/**
 * IF-04 처리 추적 조회 API (설계서 §5.4, D-18). 읽기 전용이다.
 *
 * <p>목록: limit 기본 50, 최대 200. 다음 페이지는 after에 page.nextAfter를 넣는다. from·to는 수신 시각 기준 ISO-8601이다
 * (예: 2026-10-31T00:00:00+09:00, URL에서는 +를 %2B로). 주문 목록의 receipt·shipment는 단계 상태(PENDING·DONE·FAILED)다
 * (D-26).
 */
@RestController
@RequestMapping("/tracking")
class TrackingController {

	static final int DEFAULT_LIMIT = 50;

	static final int MAX_LIMIT = 200;

	private final TrackingQueryService tracking;

	TrackingController(TrackingQueryService tracking) {
		this.tracking = tracking;
	}

	@GetMapping("/orders/{orderId}")
	ApiResponse<TrackingViews.OrderDetail> order(@PathVariable String orderId) {
		return ApiResponse.of(this.tracking.order(orderId));
	}

	@GetMapping("/orders")
	ApiResponse<List<TrackingViews.OrderSummary>> orders(@RequestParam(required = false) String receipt,
			@RequestParam(required = false) String shipment,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
			@RequestParam(required = false) String after, @RequestParam(required = false) Integer limit) {
		StageStatus receiptStatus = parse(receipt, StageStatus::parse, "receipt");
		StageStatus shipmentStatus = parse(shipment, StageStatus::parse, "shipment");
		return page(this.tracking.orders(receiptStatus, shipmentStatus, instant(from), instant(to), after,
				limit(limit)));
	}

	@GetMapping("/messages/{messageId}")
	ApiResponse<TrackingViews.MessageDetail> message(@PathVariable String messageId) {
		return ApiResponse.of(this.tracking.message(messageId));
	}

	@GetMapping("/messages")
	ApiResponse<List<TrackingViews.MessageSummaryView>> messages(@RequestParam(required = false) String state,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
			@RequestParam(required = false) String after, @RequestParam(required = false) Integer limit) {
		MessageState parsed = parse(state, s -> {
			for (MessageState value : MessageState.values()) {
				if (value.name().equals(s)) {
					return value;
				}
			}
			return null;
		}, "state");
		return page(this.tracking.messages(parsed, instant(from), instant(to), after, limit(limit)));
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

	private static <T> T parse(String value, Function<String, T> parser, String name) {
		if (value == null || value.isBlank()) {
			return null;
		}
		T parsed = parser.apply(value);
		if (parsed == null) {
			throw new ApiException(ErrorCode.INVALID_REQUEST, name + " 값을 알 수 없습니다: " + value);
		}
		return parsed;
	}

	private static Instant instant(OffsetDateTime time) {
		return (time != null) ? time.toInstant() : null;
	}

}
