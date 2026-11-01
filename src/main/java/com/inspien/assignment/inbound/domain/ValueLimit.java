package com.inspien.assignment.inbound.domain;

import java.nio.charset.Charset;

/**
 * ORDER_TB 컬럼 길이 한도. VARCHAR2(100)의 길이 단위는 바이트로 가정한다 (D-07, 설계서 §11).
 *
 * @param dbCharset DB가 값을 저장하는 문자셋 (바이트 수를 셀 때 쓴다)
 * @param maxBytes 최대 바이트 수
 */
public record ValueLimit(Charset dbCharset, int maxBytes) {

	public boolean exceeds(String value) {
		return value != null && value.getBytes(this.dbCharset).length > this.maxBytes;
	}

}
