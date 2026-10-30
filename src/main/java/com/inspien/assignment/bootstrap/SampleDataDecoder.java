package com.inspien.assignment.bootstrap;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Objects;

/**
 * 과제 API 응답의 SAMPLE_DATA(Base64로 인코딩된 EUC-KR XML) 디코딩.
 */
public final class SampleDataDecoder {

	public static final Charset EUC_KR = Charset.forName("EUC-KR");

	private SampleDataDecoder() {
	}

	/**
	 * Base64를 풀어 원본 XML 바이트(EUC-KR)를 돌려준다. XML 선언의 encoding과 맞도록 파일에는 이 바이트를 그대로 저장한다.
	 */
	public static byte[] decodeBytes(String base64) {
		Objects.requireNonNull(base64, "base64");
		try {
			return Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
		}
		catch (IllegalArgumentException ex) {
			throw new BootstrapException("SAMPLE_DATA가 올바른 Base64 문자열이 아닙니다", ex);
		}
	}

	public static String decodeToString(String base64) {
		return new String(decodeBytes(base64), EUC_KR);
	}

}
