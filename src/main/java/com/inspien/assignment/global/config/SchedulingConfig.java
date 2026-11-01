package com.inspien.assignment.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 영수증·배송 스케줄러. 스레드는 1개라 둘이 같은 스레드에서 차례로 돈다 (D-12, spring.task.scheduling.pool.size).
 * 단일 인스턴스에서는 fixedDelay만으로 겹침이 없다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {

}
