package com.inspien.assignment.inbound.adapter.in.web;

import org.junit.jupiter.api.Test;

import com.inspien.assignment.global.error.ApiException;
import com.inspien.assignment.global.error.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Content-Type 헤더 길이 상한 (설계서 §5.1, D-17). H2 CONTENT_TYPE 컬럼(VARCHAR(255))을 넘으면 저장 전에 거절한다.
 */
class ContentTypeLimitTest {

	static final String PREFIX = "application/xml; charset=EUC-KR; x=";

	@Test
	void headerAtLimitPasses() {
		String atLimit = headerOfLength(ContentTypeLimit.MAX_LENGTH);

		assertThatCode(() -> ContentTypeLimit.check(atLimit)).doesNotThrowAnyException();
	}

	@Test
	void headerOverLimitIsRejected() {
		String overLimit = headerOfLength(ContentTypeLimit.MAX_LENGTH + 1);

		assertThatThrownBy(() -> ContentTypeLimit.check(overLimit))
			.isInstanceOfSatisfying(ApiException.class,
					ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_REQUEST))
			.hasMessage("Content-Type 헤더가 너무 깁니다. 최대 255자까지 받습니다.");
	}

	@Test
	void missingHeaderPasses() {
		assertThatCode(() -> ContentTypeLimit.check(null)).doesNotThrowAnyException();
	}

	static String headerOfLength(int length) {
		String padding = "a".repeat(length - PREFIX.length());
		String header = PREFIX + padding;
		assertThat(header).hasSize(length);
		return header;
	}

}
