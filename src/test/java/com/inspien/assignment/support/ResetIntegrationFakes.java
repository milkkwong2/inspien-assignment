package com.inspien.assignment.support;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 테스트마다 {@link IntegrationFakes}의 가짜 FTP와 시계를 처음 상태로 되돌린다. 클래스 안의 테스트는 컨텍스트를 함께 쓰므로,
 * 앞 테스트가 켠 장애 표시·올린 파일·옮긴 시각이 다음 테스트로 넘어가지 않게 한다. 가짜가 없는 컨텍스트에서는 하는 일이
 * 없다.
 */
class ResetIntegrationFakes implements BeforeEachCallback {

	@Override
	public void beforeEach(ExtensionContext extensionContext) {
		ApplicationContext context = SpringExtension.getApplicationContext(extensionContext);
		context.getBeanProvider(FakeFtp.class).ifAvailable(FakeFtp::reset);
		context.getBeanProvider(MutableClock.class).ifAvailable(MutableClock::reset);
	}

}
