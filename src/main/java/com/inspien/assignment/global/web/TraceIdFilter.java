package com.inspien.assignment.global.web;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 traceId를 만들어 로그 MDC에 넣는다. 같은 값이 응답 본문에도 들어간다 (D-18).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

	public static final String MDC_KEY = "traceId";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		MDC.put(MDC_KEY, newTraceId());
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(MDC_KEY);
		}
	}

	/**
	 * 현재 요청의 traceId. 요청 밖(스케줄러 등)에서는 null.
	 */
	public static String current() {
		return MDC.get(MDC_KEY);
	}

	static String newTraceId() {
		return "%016x".formatted(ThreadLocalRandom.current().nextLong());
	}

}
