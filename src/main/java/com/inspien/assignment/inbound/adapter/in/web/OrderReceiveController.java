package com.inspien.assignment.inbound.adapter.in.web;

import java.io.IOException;
import java.io.InputStream;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.inspien.assignment.global.web.ApiResponse;
import com.inspien.assignment.inbound.application.InboundProperties;
import com.inspien.assignment.inbound.application.ReceiveOrderFacade;
import com.inspien.assignment.inbound.application.ReceiveResult;

/**
 * IF-01 주문 수신 API: POST /orders (설계서 §5.1, D-18).
 *
 * <p>consumes를 걸지 않고 본문을 바이트로 받는다. 그래야 Spring이 먼저 415를 내거나 문자열로 디코딩하지 않는다.
 * 인코딩 판단은 파서가 한다 (D-20).
 *
 * <p>본문은 스트림에서 상한까지만 읽는다. 상한을 넘으면 수신 Facade에 넘기기 전에 413으로 거절해 저장도 잠금도 하지
 * 않는다 (D-17, {@link LimitedBodyReader}). Content-Type 헤더도 H2 컬럼보다 길면 같은 자리에서 400으로 거절한다
 * ({@link ContentTypeLimit}).
 */
@RestController
class OrderReceiveController {

	private final ReceiveOrderFacade facade;

	private final LimitedBodyReader bodyReader;

	OrderReceiveController(ReceiveOrderFacade facade, InboundProperties inbound) {
		this.facade = facade;
		this.bodyReader = new LimitedBodyReader(inbound.maxBodySize());
	}

	@PostMapping("/orders")
	ApiResponse<ReceiveResult> receive(HttpServletRequest request,
			@RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType) throws IOException {
		ContentTypeLimit.check(contentType);
		InputStream body = request.getInputStream();
		long declaredLength = request.getContentLengthLong();
		// 빈 요청도 빈 배열로 받아 파싱 실패(400)로 기록한다. @RequestBody(required = true)처럼 빈 본문을 막으면
		// Spring이 먼저 400을 내 기록이 남지 않는다
		byte[] received = this.bodyReader.read(body, declaredLength);
		ReceiveResult result = this.facade.receive(received, contentType);
		return ApiResponse.of(result);
	}

}
