package com.inspien.assignment.global.error;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.inspien.assignment.global.web.TraceIdFilter;

/**
 * 오류 응답을 한 곳에서 만든다: RFC 9457 Problem Details + traceId, code, retryable, messageId, 503이면
 * Retry-After 헤더 (D-17, D-18).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
		ErrorCode code = ex.code();
		if (code.status().is5xxServerError()) {
			log.error("{} {}: {}", code, request.getRequestURI(), ex.getMessage(), ex.getCause());
		}
		else {
			log.warn("{} {}: {}", code, request.getRequestURI(), ex.getMessage());
		}
		return problem(code, ex.getMessage(), ex.messageId(), request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("처리하지 못한 오류 {}", request.getRequestURI(), ex);
		return problem(ErrorCode.INTERNAL_ERROR, "서버 오류가 발생했습니다. 연계 담당자에게 문의해 주세요.", null, request);
	}

	/**
	 * Spring MVC가 만드는 오류(잘못된 메서드, 경로 등)에도 traceId를 붙인다.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			problem.setProperty("traceId", TraceIdFilter.current());
		}
		return response;
	}

	static ResponseEntity<ProblemDetail> problem(ErrorCode code, String detail, String messageId,
			HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("traceId", TraceIdFilter.current());
		problem.setProperty("code", code.name());
		problem.setProperty("retryable", code.retryable());
		if (messageId != null) {
			problem.setProperty("messageId", messageId);
		}
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.status());
		if (code.retryAfter() != null) {
			builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(code.retryAfter().toSeconds()));
		}
		return builder.body(problem);
	}

}
