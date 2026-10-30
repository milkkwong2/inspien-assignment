package com.inspien.assignment.bootstrap;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnInfoDecryptorTest {

	private static final String PHONE = "010-1234-5678";

	// 기대값은 Java 구현과 독립적으로 만들었다.
	// key: python hashlib.sha1("010-1234-5678".encode()).hexdigest()[:32]
	private static final String EXPECTED_KEY_HEX = "658dd4d695d829188d6b27ed3358a00c";

	// cipherText: openssl enc -aes-128-ecb -K <EXPECTED_KEY_HEX> -nosalt -base64 -A
	private static final String PLAIN_TEXT = "{\"HOST\":\"db.example.com\",\"PORT\":\"1521\",\"USER\":\"테스트\"}";

	private static final String CIPHER_TEXT = "0UuoLqurtIBp+4tdeJSu/jzGLgWXbTbtI2lbfg0qMO45s44/97LKfhYXTunFgpApIuCfILQFw9AzPpzJhQsEOg==";

	@Test
	void deriveKeyUsesFirst16BytesOfSha1() {
		byte[] key = ConnInfoDecryptor.deriveKey(PHONE).getEncoded();

		assertThat(HexFormat.of().formatHex(key)).isEqualTo(EXPECTED_KEY_HEX);
	}

	@Test
	void deriveKeyTreatsHyphensAsPartOfTheKey() {
		byte[] withHyphens = ConnInfoDecryptor.deriveKey("010-1234-5678").getEncoded();
		byte[] withoutHyphens = ConnInfoDecryptor.deriveKey("01012345678").getEncoded();

		assertThat(withHyphens).isNotEqualTo(withoutHyphens);
	}

	@Test
	void decryptReturnsPlainText() {
		assertThat(new ConnInfoDecryptor(PHONE).decrypt(CIPHER_TEXT)).isEqualTo(PLAIN_TEXT);
	}

	@Test
	void decryptIgnoresLineBreaksInBase64() {
		String wrapped = CIPHER_TEXT.substring(0, 20) + "\r\n" + CIPHER_TEXT.substring(20);

		assertThat(new ConnInfoDecryptor(PHONE).decrypt(wrapped)).isEqualTo(PLAIN_TEXT);
	}

	@Test
	void decryptWithWrongPhoneNumberFails() {
		assertThatThrownBy(() -> new ConnInfoDecryptor("010-9999-9999").decrypt(CIPHER_TEXT))
			.isInstanceOf(BootstrapException.class)
			.hasMessageContaining("전화번호");
	}

	@Test
	void decryptRejectsInvalidBase64() {
		assertThatThrownBy(() -> new ConnInfoDecryptor(PHONE).decrypt("not*base64"))
			.isInstanceOf(BootstrapException.class)
			.hasMessageContaining("Base64");
	}

}
