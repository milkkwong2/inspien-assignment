package com.inspien.assignment.inbound.adapter.in.web;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.inspien.assignment.global.web.ApiResponse;
import com.inspien.assignment.inbound.application.ReceiveOrderService;
import com.inspien.assignment.inbound.application.ReceiveResult;

/**
 * IF-01 주문 수신 API: POST /orders (설계서 §5.1, D-18).
 *
 * <p>consumes를 걸지 않고 본문을 바이트로 받는다. 그래야 Spring이 먼저 415를 내거나 문자열로 디코딩하지 않는다.
 * 인코딩 판단은 파서가 한다 (D-20).
 */
@RestController
class OrderReceiveController {

	private final ReceiveOrderService service;

	OrderReceiveController(ReceiveOrderService service) {
		this.service = service;
	}

	@PostMapping("/orders")
	ApiResponse<ReceiveResult> receive(@RequestBody(required = false) byte[] body,
			@RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType) {
		// 빈 요청도 받아 파싱 실패(400)로 기록한다. required=true면 Spring이 먼저 400을 내 기록이 남지 않는다
		byte[] received = (body != null) ? body : new byte[0];
		ReceiveResult result = this.service.receive(received, contentType);
		return ApiResponse.of(result);
	}

}
