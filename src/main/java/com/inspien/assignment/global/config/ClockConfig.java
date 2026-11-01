package com.inspien.assignment.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각은 한국 시간 기준이다 (수신 시각 응답, 영수증 수신일시). 테스트에서 시각을 바꿀 수 있게 빈으로 둔다.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

	static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	@Bean
	Clock clock() {
		return Clock.system(KOREA);
	}

}
