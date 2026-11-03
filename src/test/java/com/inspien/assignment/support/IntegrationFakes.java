package com.inspien.assignment.support;

import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 통합 테스트의 가짜 FTP와 고정 시계. 필요한 테스트 클래스가 {@code @Import}한다.
 *
 * <p>둘 다 테스트마다 처음 상태(장애 없음, 빈 FTP, 시각 {@link #START})로 돌아간다 ({@link ResetIntegrationFakes}). 그래서
 * 테스트는 앞 테스트가 무엇을 바꿨는지 몰라도 된다.
 */
@TestConfiguration
public class IntegrationFakes {

	/**
	 * 시계의 처음 시각. 한국 시각으로 2026-10-31 14:05:12다.
	 */
	public static final Instant START = Instant.parse("2026-10-31T05:05:12Z");

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	@Bean
	@Primary
	FakeFtp fakeFtp() {
		return new FakeFtp();
	}

	@Bean
	@Primary
	MutableClock mutableClock() {
		return new MutableClock(START, ZONE);
	}

}
