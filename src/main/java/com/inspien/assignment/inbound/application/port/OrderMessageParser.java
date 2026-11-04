package com.inspien.assignment.inbound.application.port;

import com.inspien.assignment.inbound.domain.MessageParseException;
import com.inspien.assignment.inbound.domain.OrderMessage;

/**
 * 받은 본문을 주문 메시지로 읽는다. 재처리 때 수신 Facade가 저장해 둔 원본을 다시 파싱하므로 포트로 둔다.
 * 송신 형식이 JSON으로 바뀌면 구현만 추가한다 (D-21).
 */
public interface OrderMessageParser {

	/**
	 * @param body 받은 바이트 그대로
	 * @param contentType Content-Type 헤더 (없으면 null). charset만 쓴다
	 * @throws MessageParseException 읽을 수 없으면
	 */
	OrderMessage parse(byte[] body, String contentType);

}
