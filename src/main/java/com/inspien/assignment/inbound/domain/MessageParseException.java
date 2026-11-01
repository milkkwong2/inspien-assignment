package com.inspien.assignment.inbound.domain;

/**
 * 본문을 주문 메시지로 읽을 수 없다: 읽을 수 없는 바이트, 허용하지 않는 문자셋, DOCTYPE, XML 형식 오류 (D-20).
 * 같은 바이트는 다시 보내도 같은 결과라 400으로 답한다 (D-17).
 *
 * <p>메시지는 송신자가 읽는 문장이다. 원인과 위치를 적는다.
 */
public class MessageParseException extends RuntimeException {

	public MessageParseException(String message) {
		super(message);
	}

	public MessageParseException(String message, Throwable cause) {
		super(message, cause);
	}

}
