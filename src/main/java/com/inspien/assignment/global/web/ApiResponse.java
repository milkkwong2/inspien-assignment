package com.inspien.assignment.global.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 성공 응답. 공통 요소(traceId, page)는 바깥, 엔드포인트별 필드는 data 안에 둔다 (D-18).
 *
 * <p>성공·실패는 HTTP 상태 코드로만 말하므로 success나 결과 코드 필드를 두지 않는다. 공통 필드는 이 팩토리에서만 채운다.
 *
 * @param <T> data 형식
 * @param traceId 로그 MDC와 같은 값
 * @param data 엔드포인트별 응답
 * @param page 목록 응답의 다음 페이지 정보 (단건이면 없음)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String traceId, T data, Page page) {

	public static <T> ApiResponse<T> of(T data) {
		return new ApiResponse<>(TraceIdFilter.current(), data, null);
	}

	public static <T> ApiResponse<List<T>> page(List<T> data, String nextAfter, int limit) {
		return new ApiResponse<>(TraceIdFilter.current(), data, new Page(nextAfter, limit));
	}

	/**
	 * 커서 페이징. 다음 페이지는 after에 nextAfter를 넣어 요청한다.
	 *
	 * @param nextAfter 다음 페이지 커서 (마지막 페이지면 없음)
	 * @param limit 이번 요청의 건수 상한
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Page(String nextAfter, int limit) {
	}

}
