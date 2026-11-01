package com.inspien.assignment.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.UUID;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * local 프로필의 접속 대상을 테스트용 컨테이너 Oracle과 임시 H2로 바꾼다.
 *
 * <p>Oracle 행은 지울 수 없으므로(DELETE 권한 없음) 컨텍스트마다 다른 지원자 키를 써서 테스트끼리 데이터가 섞이지 않게
 * 한다. 공용 테이블에서 다른 지원자가 되는 셈이다.
 */
class IntegrationTestInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		String url = LocalOracle.jdbcUrl();
		TestPropertyValues
			.of("app.order-db.url=" + url, "app.shipment-db.url=" + url, "app.history-db.path=" + tempH2Path(),
					"app.applicant.key=IT-" + UUID.randomUUID().toString().substring(0, 8),
					// 스케줄러는 테스트가 직접 한 주기씩 돌린다
					"app.scheduler.enabled=false")
			.applyTo(context);
	}

	private static String tempH2Path() {
		try {
			return Files.createTempDirectory("history-").resolve("history").toString();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
