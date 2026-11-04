package com.inspien.assignment.global.error;

import java.time.Duration;

import org.springframework.http.HttpStatus;

/**
 * 오류 응답 계약 (설계서 §5.1, D-17). 4xx는 보낸 쪽이 고칠 문제, 5xx는 받는 쪽 문제이고,
 * 재전송으로 풀리는 것은 503뿐이다.
 *
 * <p>HTTP 상태·retryable·Retry-After를 코드와 함께 둔다. D-17 계약 표가 이 enum 한 곳에 있다 (D-18). 웹 어댑터에서
 * 따로 번역하지 않는 이유:
 * <ul>
 * <li>쓰는 곳은 HTTP로 불리는 Facade(수신, 조회, 운영자 재처리)뿐이다. 스케줄러·기동 쪽은 쓰지 않는다 (ArchitectureTest
 * 가 강제).</li>
 * <li>수신은 내보낸 상태 코드와 코드를 H2 수신 기록에 남기고, 같은 본문이 다시 오면 그 값으로 처리를 가른다(400이면 새
 * 메시지, 그 밖의 실패는 이어서 처리) (D-05, D-24). 조회 API도 그 값을 보여 준다. 여기서 상태 코드는 표현이 아니라
 * 저장되는 업무 데이터다.</li>
 * </ul>
 * MQ처럼 두 번째 전송 방식이 생기면 다시 본다 (D-18 2026-11-04 보완).
 */
public enum ErrorCode {

	/** 본문을 읽을 수 없음: 인코딩 오류, 허용하지 않는 문자셋, DOCTYPE, XML 형식 오류 (D-20) */
	XML_PARSE_ERROR(HttpStatus.BAD_REQUEST, null),

	/** 요청 값이 형식에 맞지 않음 (조회 API 파라미터 등) */
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, null),

	/** 조회 대상이 없음 */
	NOT_FOUND(HttpStatus.NOT_FOUND, null),

	/** 수신 본문이 상한(app.inbound.max-body-size)을 넘음. 저장하기 전에 거절해 기록이 남지 않는다 (D-17) */
	PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, null),

	/** 재처리할 단계가 없음: "사람이 확인" 표시가 하나도 없다. 아직 처리 중이거나 이미 끝났다 (D-26) */
	NOTHING_TO_RETRY(HttpStatus.CONFLICT, null),

	/** 미리 배정한 번호의 행이 일부만 있거나 원본과 다름 (D-16, D-19) */
	UNEXPECTED_STATE(HttpStatus.INTERNAL_SERVER_ERROR, null),

	/** 채번 소진, Z999 초과 (D-08) */
	ORDER_ID_EXHAUSTED(HttpStatus.INTERNAL_SERVER_ERROR, null),

	/** Oracle이 저장을 거절: 길이·제약 위반, 권한 없음 등 연결·자원 계열이 아닌 오류 (D-17) */
	ORDER_DB_REJECTED(HttpStatus.INTERNAL_SERVER_ERROR, null),

	/** 예상하지 못한 서버 오류. DB 예외가 아닌 오류로 코드 결함일 수 있다 (D-17) */
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
