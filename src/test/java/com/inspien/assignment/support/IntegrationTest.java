package com.inspien.assignment.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * 컨테이너 Oracle과 임시 H2로 앱 전체를 띄우는 통합 테스트.
 *
 * <p>테스트 클래스마다 새 컨텍스트를 띄우고 클래스가 끝나면 닫는다. Oracle 행은 지울 수 없어(DELETE 권한 없음) 컨텍스트마다
 * 다른 지원자 키와 빈 H2를 쓰는데({@link IntegrationTestInitializer}), 스케줄러 한 주기는 그 키의 N 주문을 모두 처리하고
 * 채번은 그 H2에서 이어진다. 두 클래스가 컨텍스트를 함께 쓰면 한 클래스가 받은 주문을 다른 클래스의 주기가 처리하고, 첫
 * 번호도 앞 클래스에 따라 달라져 결과가 실행 순서에 묶인다. 설정이 같은 클래스는 Spring 컨텍스트 캐시가 한 컨텍스트로 합치므로
 * 격리를 우연에 맡기지 않고 여기서 명시한다.
 *
 * <p>클래스 안의 테스트는 컨텍스트(지원자 키, DB)를 함께 쓰므로 주문을 서로 다르게 만든다. {@link IntegrationFakes}의 가짜 FTP와
 * 시계는 테스트마다 처음 상태로 돌아간다 ({@link ResetIntegrationFakes}).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@ActiveProfiles("local")
@ContextConfiguration(initializers = IntegrationTestInitializer.class)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@ExtendWith(ResetIntegrationFakes.class)
public @interface IntegrationTest {

}
