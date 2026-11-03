package com.inspien.assignment.global.error;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
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
	 * Spring MVC가 만드는 오류(잘못된 메서드·Content-Type, 빠진 파라미터, 응답 쓰기 실패 등)도 ApiException과 같은
	 * 형식(traceId, code, retryable)으로 응답하고 같은 규칙으로 로그를 남긴다. 상태 코드는 Spring이 정한 것을 쓴다.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ErrorCode code = codeFor(statusCode);
		logSpringError(code, statusCode, ex, request);
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			addContractFields(problem, code);
		}
		return response;
	}

	/**
	 * Spring이 고른 상태에 가까운 오류 코드. 오류 코드를 새로 만들지 않는다: 404는 NOT_FOUND, 그 밖의 4xx는
	 * INVALID_REQUEST, 5xx는 INTERNAL_ERROR. 모두 재전송으로 풀리지 않는다(retryable false).
	 */
	private static ErrorCode codeFor(HttpStatusCode status) {
		if (status.is5xxServerError()) {
			return ErrorCode.INTERNAL_ERROR;
		}
		if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
			return ErrorCode.NOT_FOUND;
		}
		return ErrorCode.INVALID_REQUEST;
	}

	/**
	 * handleApiException과 같은 규칙: 5xx는 원인을 찾도록 스택과 함께 ERROR, 4xx는 보낸 쪽 문제라 WARN 한 줄.
	 */
	private static void logSpringError(ErrorCode code, HttpStatusCode status, Exception ex, WebRequest request) {
		if (status.is5xxServerError()) {
			log.error("{} {} {}: {}", code, status.value(), requestUri(request), ex.getMessage(), ex);
		}
		else {
			log.warn("{} {} {}: {}", code, status.value(), requestUri(request), ex.getMessage());
		}
	}

	private static String requestUri(WebRequest request) {
		if (request instanceof ServletWebRequest servletRequest) {
			return servletRequest.getRequest().getRequestURI();
		}
		return request.getDescription(false);
	}

	static ResponseEntity<ProblemDetail> problem(ErrorCode code, String detail, String messageId,
			HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
		problem.setInstance(URI.create(request.getRequestURI()));
		addContractFields(problem, code);
		if (messageId != null) {
			problem.setProperty("messageId", messageId);
		}
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.status());
		if (code.retryAfter() != null) {
			builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(code.retryAfter().toSeconds()));
		}
		return builder.body(problem);
	}

	private static void addContractFields(ProblemDetail problem, ErrorCode code) {
		problem.setProperty("traceId", TraceIdFilter.current());
		problem.setProperty("code", code.name());
		problem.setProperty("retryable", code.retryable());
	}

}
