package com.inspien.assignment.global.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 영수증·배송·마감 스케줄러 (D-12, D-26).
 *
 * <p>세 도메인(receipt, shipment, order)이 주기당 건수를 읽고, stage가 주기를 재시도 간격과 견준다. stage는 다른 도메인을
 * 모르므로 (D-21) 모두가 볼 수 있는 global에 둔다. 주기 도구 패키지 cycle에 두면 stage가 볼 수 없다. 실행 여부와 주기는
 * {@link SchedulingConfig}와 각 스케줄러가 같은 키로 읽는다.
 *
 * @param enabled 스케줄러 실행 여부 (테스트에서 끈다). {@code @ConditionalOnProperty}가 키로 직접 읽는다
 * @param fixedDelay 주기. 가장 짧은 재시도 간격보다 짧아야 한다. 이 관계는 재시도 정책을 만들 때 검사한다 (stage)
 * @param batchSize 한 주기 최대 처리 건수
 */
@ConfigurationProperties("app.scheduler")
public record SchedulerProperties(boolean enabled, Duration fixedDelay, int batchSize) {

	public SchedulerProperties {
		PropertyChecks.positive("app.scheduler.fixed-delay", fixedDelay);
		PropertyChecks.positive("app.scheduler.batch-size", batchSize);
	}

}
