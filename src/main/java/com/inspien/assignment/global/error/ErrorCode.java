package com.inspien.assignment.global.error;

import java.time.Duration;

import org.springframework.http.HttpStatus;

/**
 * 오류 응답 계약 (설계서 §5.1, D-17). 4xx는 보낸 쪽이 고칠 문제, 5xx는 받는 쪽 문제이고,
 * 재전송으로 풀리는 것은 503뿐이다.
 */
public enum ErrorCode {

	/** 본문을 읽을 수 없음: 인코딩 오류, 허용하지 않는 문자셋, DOCTYPE, XML 형식 오류 (D-20) */
	XML_PARSE_ERROR(HttpStatus.BAD_REQUEST, null),

	/** 요청 값이 형식에 맞지 않음 (조회 API 파라미터 등) */
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, null),

	/** 조회 대상이 없음 */
	NOT_FOUND(HttpStatus.NOT_FOUND, null),

	/** 재처리할 단계가 없음: "사람이 확인" 표시가 하나도 없다. 아직 처리 중이거나 이미 끝났다 (D-26) */
	NOTHING_TO_RETRY(HttpStatus.CONFLICT, null),

	/** 미리 배정한 번호의 행이 일부만 있거나 원본과 다름 (D-16, D-19) */
	UNEXPECTED_STATE(HttpStatus.INTERNAL_SERVER_ERROR, null),

	/** 채번 소진, Z999 초과 (D-08) */
	ORDER_ID_EXHAUSTED(HttpStatus.INTERNAL_SERVER_ERROR, null),

	/** Oracle이 저장을 거절: 길이·제약 위반, 권한 없음 등 연결·자원 계열이 아닌 오류 (D-17) */
	ORDER_DB_REJECTED(HttpStatus.INTERNAL_SERVER_ERROR, null),

	/** 예상하지 못한 서버 오류 */
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, null),

	/** 쇼핑몰 Oracle 연결·자원 장애. 결과 불명 포함 (D-17, D-19) */
	ORDER_DB_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, Duration.ofSeconds(30)),

	/** H2 사용 불가 (D-04) */
	HISTORY_DB_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, Duration.ofSeconds(30)),

	/** 수신 잠금 대기 상한 초과 (D-16) */
	BUSY(HttpStatus.SERVICE_UNAVAILABLE, Duration.ofSeconds(5));

	private final HttpStatus status;

	private final Duration retryAfter;

	ErrorCode(HttpStatus status, Duration retryAfter) {
		this.status = status;
		this.retryAfter = retryAfter;
	}

	public HttpStatus status() {
		return this.status;
	}

	/**
	 * 같은 요청을 다시 보내면 풀릴 수 있는가. 503만 그렇다.
	 */
	public boolean retryable() {
		return this.retryAfter != null;
	}

	/**
	 * Retry-After 헤더 값. 재전송할 수 없는 오류면 null.
	 */
	public Duration retryAfter() {
		return this.retryAfter;
	}

}
