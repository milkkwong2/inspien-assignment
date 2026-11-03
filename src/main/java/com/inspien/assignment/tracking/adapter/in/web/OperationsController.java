package com.inspien.assignment.tracking.adapter.in.web;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.inspien.assignment.global.web.ApiResponse;
import com.inspien.assignment.tracking.application.OperationsService;
import com.inspien.assignment.tracking.application.TrackingViews;

/**
 * 운영자 재처리 API (설계서 §5.4, D-26). FAILED 단계를 모두 다시 시작한다. 단계를 받지 않는다.
 *
 * <p>200: 다시 시작한 단계 목록. 404: 내 주문이 아님. 409: FAILED 단계가 없음. 인증은 범위 밖이다. 바꾸는 것이 재시도
 * 상태뿐이라 잘못 불러도 자동 처리가 한 번 더 도는 데 그친다.
 */
@RestController
@RequestMapping("/operations")
class OperationsController {

	private final OperationsService operations;

	OperationsController(OperationsService operations) {
		this.operations = operations;
	}

	@PostMapping("/orders/{orderId}/retry")
	ApiResponse<TrackingViews.RetryView> retry(@PathVariable String orderId) {
		TrackingViews.RetryView result = this.operations.retry(orderId);
		return ApiResponse.of(result);
	}

}
