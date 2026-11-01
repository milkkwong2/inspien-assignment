package com.inspien.assignment.inbound.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 메시지 키: 받은 본문 바이트의 SHA-256 (D-15, D-20). 인코딩 해석과 관계없이 바이트로 계산한다.
 *
 * <p>유일하지 않다. 완료 후 1분이 지나면 같은 값이 새 메시지로 다시 올 수 있다 (D-24).
 */
public record MessageKey(String value) {

	public static MessageKey of(byte[] body) {
		try {
			return new MessageKey(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)));
		}
		catch (NoSuchAlgorithmException ex) {
			// 모든 JDK에 SHA-256이 있다
			throw new IllegalStateException(ex);
		}
	}

}
