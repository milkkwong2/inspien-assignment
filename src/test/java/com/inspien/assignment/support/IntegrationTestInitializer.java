package com.inspien.assignment.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * local 프로필의 접속 대상을 테스트용 컨테이너 Oracle과 임시 H2로 바꾼다.
 */
class IntegrationTestInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	@Override
	public void initialize(ConfigurableApplicationContext context) {
		String url = LocalOracle.jdbcUrl();
		TestPropertyValues
			.of("app.order-db.url=" + url, "app.shipment-db.url=" + url, "app.history-db.path=" + tempH2Path())
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
