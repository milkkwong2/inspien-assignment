package com.inspien.assignment.global.error;

/**
 * API 응답으로 나가는 오류. 전역 예외 처리기가 Problem Details로 바꾼다 (D-17, D-18).
 *
 * <p>HTTP로 불리는 Facade(ReceiveOrderFacade, TrackingQueryFacade, OperationsFacade)와 웹 어댑터만 던진다. 스케줄러·기동이
 * 부르는 코드는 이 예외를 쓰지 않는다. 받아 줄 HTTP 응답이 없기 때문이다 (ArchitectureTest가 강제). 수신은 이 예외의 상태
 * 코드를 수신 기록에 남겨 같은 본문이 다시 올 때 처리를 가른다 (D-05, D-24). 계약 표는 {@link ErrorCode} 한 곳에 있다
 * (D-18).
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	private final String messageId;

	/**
	 * @param code 오류 코드
	 * @param detail 응답의 detail. 송신자가 읽는 문장이라 내부 정보(SQL, 접속 정보)를 넣지 않는다
	 * @param messageId 수신 메시지 ID. 원본을 저장하기 전에 실패했으면 null
	 * @param cause 원인 (로그용)
	 */
	public ApiException(ErrorCode code, String detail, String messageId, Throwable cause) {
		super(detail, cause);
		this.code = code;
		this.messageId = messageId;
	}

	public ApiException(ErrorCode code, String detail) {
		this(code, detail, null, null);
	}

	public ErrorCode code() {
		return this.code;
	}

	public String messageId() {
		return this.messageId;
	}

}
