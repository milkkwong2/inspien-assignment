package com.inspien.assignment.bootstrap;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * 과제 API 응답의 접속 정보(ORDER_TB_CONN, SHIPMENT_TB_CONN, FTP_CONN) 복호화.
 *
 * <p>명세: AES-128 / ECB / PKCS5Padding. Key는 휴대폰 번호 문자열의 UTF-8 바이트를 SHA-1로 해싱한 뒤
 * 앞 16바이트를 쓴다. 암호문은 Base64로 인코딩되어 온다.
 *
 * <p>ECB 모드는 같은 평문 블록이 항상 같은 암호문 블록이 되어 패턴이 드러나므로 일반적으로 권장되지 않는다.
 * 여기서는 과제 명세를 그대로 따른다.
 */
public final class ConnInfoDecryptor {

	private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

	private static final int KEY_LENGTH_BYTES = 16;

	private final SecretKeySpec key;

	/**
	 * @param phoneNumber 과제 API 요청에 보낸 휴대폰 번호 그대로 (예: "010-1234-5678", 하이픈 포함)
	 */
	public ConnInfoDecryptor(String phoneNumber) {
		this.key = deriveKey(phoneNumber);
	}

	static SecretKeySpec deriveKey(String phoneNumber) {
		Objects.requireNonNull(phoneNumber, "phoneNumber");
		byte[] digest = sha1(phoneNumber.getBytes(StandardCharsets.UTF_8));
		return new SecretKeySpec(Arrays.copyOf(digest, KEY_LENGTH_BYTES), "AES");
	}

	public String decrypt(String base64CipherText) {
		Objects.requireNonNull(base64CipherText, "base64CipherText");
		byte[] cipherBytes = decodeBase64(base64CipherText);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, this.key);
			return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException ex) {
			// 패딩 오류는 대부분 키 불일치(요청 때와 다른 전화번호)에서 온다
			throw new BootstrapException("접속 정보 복호화 실패: 요청에 사용한 전화번호와 같은지 확인하세요", ex);
		}
	}

	private static byte[] decodeBase64(String value) {
		try {
			// 응답에 줄바꿈이 섞여 와도 처리할 수 있게 공백만 제거하고, 그 외 잘못된 문자는 오류로 본다
			return Base64.getDecoder().decode(value.replaceAll("\\s", ""));
		}
		catch (IllegalArgumentException ex) {
			throw new BootstrapException("접속 정보가 올바른 Base64 문자열이 아닙니다", ex);
		}
	}

	private static byte[] sha1(byte[] input) {
		try {
			return MessageDigest.getInstance("SHA-1").digest(input);
		}
		catch (NoSuchAlgorithmException ex) {
			// 모든 JDK에 SHA-1 구현이 포함되어 있으므로 발생하지 않는다
			throw new IllegalStateException(ex);
		}
	}

}
