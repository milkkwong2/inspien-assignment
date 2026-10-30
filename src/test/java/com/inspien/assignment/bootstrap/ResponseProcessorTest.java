package com.inspien.assignment.bootstrap;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Cipher;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseProcessorTest {

	static final String PHONE = "010-1234-5678";

	static final String SAMPLE_XML = "<?xml version=\"1.0\" encoding=\"EUC-KR\"?><ORDER><NAME>홍길동</NAME></ORDER>";

	private final ResponseProcessor processor = new ResponseProcessor(new ConnInfoDecryptor(PHONE));

	@Test
	void decryptsStringFieldsAndParsesJsonPlainText() {
		ResponseProcessor.Result result = this.processor.process(response(
				quoted(encrypt("{\"HOST\":\"db1\",\"PORT\":\"1521\"}")),
				quoted(encrypt("{\"HOST\":\"db2\"}")),
				quoted(encrypt("ftp://user@ftp.example.com"))));

		assertThat(result.applicantKey()).isEqualTo("KEY-1234");
		JsonNode connection = result.connection();
		assertThat(connection.get("ORDER_TB_CONN").get("HOST").asString()).isEqualTo("db1");
		assertThat(connection.get("SHIPMENT_TB_CONN").get("HOST").asString()).isEqualTo("db2");
		assertThat(connection.get("FTP_CONN").asString()).isEqualTo("ftp://user@ftp.example.com");
		assertThat(result.connectionJson()).contains("\"APPLICANT_KEY\" : \"KEY-1234\"");
	}

	@Test
	void decryptsEachValueWhenFieldIsObject() {
		String objectField = "{\"HOST\":" + quoted(encrypt("ftp.example.com")) + ",\"PORT\":21}";

		ResponseProcessor.Result result = this.processor.process(response(objectField, objectField, objectField));

		JsonNode ftp = result.connection().get("FTP_CONN");
		assertThat(ftp.get("HOST").asString()).isEqualTo("ftp.example.com");
		assertThat(ftp.get("PORT").asInt()).isEqualTo(21);
	}

	@Test
	void keepsSampleDataAsOriginalEucKrBytes() {
		ResponseProcessor.Result result = this.processor.process(response(quoted(encrypt("a")),
				quoted(encrypt("b")), quoted(encrypt("c"))));

		assertThat(result.sampleOrder()).isEqualTo(SAMPLE_XML.getBytes(SampleDataDecoder.EUC_KR));
	}

	@Test
	void reportsMissingFields() {
		assertThatThrownBy(() -> this.processor.process("{\"APPLICANT_KEY\":\"K\",\"FTP_CONN\":null}"))
			.isInstanceOf(BootstrapException.class)
			.hasMessageContaining("ORDER_TB_CONN")
			.hasMessageContaining("SHIPMENT_TB_CONN")
			.hasMessageContaining("FTP_CONN")
			.hasMessageContaining("SAMPLE_DATA");
	}

	@Test
	void rejectsNonJsonResponse() {
		assertThatThrownBy(() -> this.processor.process("<html>error</html>"))
			.isInstanceOf(BootstrapException.class)
			.hasMessageContaining("raw-response.json");
	}

	static String response(String orderConn, String shipmentConn, String ftpConn) {
		String sample = Base64.getEncoder().encodeToString(SAMPLE_XML.getBytes(SampleDataDecoder.EUC_KR));
		return """
				{"APPLICANT_KEY":"KEY-1234","ORDER_TB_CONN":%s,"SHIPMENT_TB_CONN":%s,"FTP_CONN":%s,"SAMPLE_DATA":"%s"}"""
			.formatted(orderConn, shipmentConn, ftpConn, sample);
	}

	static String quoted(String value) {
		return "\"" + value + "\"";
	}

	/**
	 * 테스트 데이터 생성용 암호화. 복호화 규칙 자체는 ConnInfoDecryptorTest에서 openssl 결과로 따로 검증한다.
	 */
	static String encrypt(String plainText) {
		try {
			Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE, ConnInfoDecryptor.deriveKey(PHONE));
			return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
