package com.inspien.assignment.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 영수증·배송·마감 스케줄러. 스레드는 3개라 스케줄러마다 하나씩 돈다 (D-12, D-26, spring.task.scheduling.pool.size).
 * 단일 인스턴스에서는 fixedDelay만으로 같은 스케줄러끼리 겹치지 않는다. 서로 다른 스케줄러는 동시에 돌아도 부딪히지 않는다:
 * 영수증과 배송은 H2에서 서로 다른 행을 쓰고, 마감은 H2를 읽기만 하며 Y는 WHERE STATUS = 'N' 조건부로 쓴다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {

}
