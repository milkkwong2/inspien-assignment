package com.inspien.assignment.inbound.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.inspien.assignment.global.error.ApiException;
import com.inspien.assignment.global.error.ErrorCode;

/**
 * 수신 Content-Type 헤더 길이 상한 (설계서 §5.1, D-17).
 *
 * <p>Content-Type은 다시 파싱할 때 문자셋을 정하려고 원본과 함께 H2에 저장한다 (D-20). 컬럼보다 긴 값은 저장이 실패해
 * H2 장애처럼 503이 나가고, 송신자가 같은 요청을 다시 보내도 계속 503이다. 그래서 수신 잠금을 잡기 전, H2에 저장하기
 * 전에 400으로 거절한다. 거절한 요청은 기록이 없어 조회 API에 나오지 않고 로그(WARN)에만 남는다.
 *
 * <p>잘라서 저장하지 않는다. 자르면 charset 파라미터가 떨어져, 다시 파싱할 때 처음과 다른 문자셋으로 읽을 수 있다. 같은
 * 바이트는 같은 결과여야 한다 (D-16).
 */
final class ContentTypeLimit {

	private static final Logger log = LoggerFactory.getLogger(ContentTypeLimit.class);

	/**
	 * schema-h2.sql의 {@code RECEIVED_MESSAGE.CONTENT_TYPE VARCHAR(255)}와 같아야 한다. 컬럼을 바꾸면 이 값도 바꾼다.
	 *
	 * <p>단위는 글자다. H2 VARCHAR 길이는 바이트가 아니라 Java 문자열 길이(UTF-16 단위)로 세므로 {@link String#length()}와
	 * 비교한다. Tomcat은 헤더 바이트를 ISO-8859-1로 읽어 바이트 하나가 글자 하나이므로, 받은 헤더 바이트 수와도 같다.
	 */
	static final int MAX_LENGTH = 255;

	private static final String TOO_LONG_DETAIL = "Content-Type 헤더가 너무 깁니다. 최대 %d자까지 받습니다.".formatted(MAX_LENGTH);

	private ContentTypeLimit() {
	}

	/**
	 * @param contentType Content-Type 헤더 값. 없으면 null (그대로 받는다)
	 * @throws ApiException 상한을 넘으면 INVALID_REQUEST
	 */
	static void check(String contentType) {
		if (contentType == null || contentType.length() <= MAX_LENGTH) {
			return;
		}
		int receivedLength = contentType.length();
		log.warn("Content-Type 헤더가 상한 {}자를 넘어 저장하지 않고 거절합니다: {}자", MAX_LENGTH, receivedLength);
		throw new ApiException(ErrorCode.INVALID_REQUEST, TOO_LONG_DETAIL);
	}

}
