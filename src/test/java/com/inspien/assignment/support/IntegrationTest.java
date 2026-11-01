package com.inspien.assignment.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * 컨테이너 Oracle과 임시 H2로 앱 전체를 띄우는 통합 테스트.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@ActiveProfiles("local")
@ContextConfiguration(initializers = IntegrationTestInitializer.class)
public @interface IntegrationTest {

}
