package com.inspien.assignment.inbound;

import java.nio.charset.Charset;

import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.inspien.assignment.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Content-Type 헤더 길이 상한 (설계서 §5.1, D-17). H2 CONTENT_TYPE 컬럼(VARCHAR(255))보다 긴 헤더는 저장하기 전에
 * 400으로 거절한다. 저장했다면 INSERT가 실패해 H2 장애(503)로 보이고 재전송해도 계속 503이다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ReceiveOrderContentTypeLimitIntegrationTest {

	static final Charset EUC_KR = Charset.forName("EUC-KR");

	static final int COLUMN_LENGTH = 255;

	static final String CHARSET_PREFIX = "application/xml; charset=EUC-KR; x=";

	@Autowired
	MockMvc mvc;

	@Autowired
	@Qualifier("historyDataSource")
	DataSource historyDataSource;

	@Test
	void contentTypeOverColumnLengthIs400AndNotStored() throws Exception {
		String contentType = contentTypeOfLength(COLUMN_LENGTH + 1);
		byte[] body = order("C");
		long before = receivedMessageCount();

		this.mvc.perform(post("/orders").header(HttpHeaders.CONTENT_TYPE, contentType).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(header().doesNotExist("Retry-After"))
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
			.andExpect(jsonPath("$.detail").value("Content-Type 헤더가 너무 깁니다. 최대 255자까지 받습니다."))
			.andExpect(jsonPath("$.retryable").value(false))
			.andExpect(jsonPath("$.messageId").doesNotExist());

		assertThat(receivedMessageCount()).as("원본을 저장하지 않는다").isEqualTo(before);
	}

	/**
	 * 컬럼 길이와 같은 헤더는 받고, 자르지 않고 그대로 저장한다. 헤더의 charset(EUC-KR)으로 본문을 읽는다.
	 */
	@Test
	void contentTypeAtColumnLengthIsAcceptedAndStoredAsIs() throws Exception {
		String contentType = contentTypeOfLength(COLUMN_LENGTH);
		byte[] body = order("D");

		MvcResult result = this.mvc.perform(post("/orders").header(HttpHeaders.CONTENT_TYPE, contentType).content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.summary.stored").value(1))
			.andReturn();

		String messageId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.messageId");
		String stored = history()
			.queryForObject("SELECT CONTENT_TYPE FROM RECEIVED_MESSAGE WHERE ID = ?", String.class, messageId);
		assertThat(stored).isEqualTo(contentType);
	}

	/**
	 * charset=EUC-KR을 앞에 두고 뒤를 의미 없는 파라미터로 채워 정확히 length자로 만든다.
	 */
	static String contentTypeOfLength(int length) {
		String padding = "a".repeat(length - CHARSET_PREFIX.length());
		String contentType = CHARSET_PREFIX + padding;
		assertThat(contentType).hasSize(length);
		return contentType;
	}

	/**
	 * 다른 테스트와 본문이 겹쳐 이전 결과를 다시 받지 않도록 이름에 표시를 붙인다.
	 */
	static byte[] order(String mark) {
		String order = """
				<HEADER><USER_ID>USER01</USER_ID><NAME>김민수%1$s</NAME><ADDRESS>서울</ADDRESS></HEADER>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM10</ITEM_ID><ITEM_NAME>마우스</ITEM_NAME>
				<PRICE>32000</PRICE></ITEM>
				""".formatted(mark);
		return order.getBytes(EUC_KR);
	}

	long receivedMessageCount() {
		return history().queryForObject("SELECT COUNT(*) FROM RECEIVED_MESSAGE", Long.class);
	}

	JdbcTemplate history() {
		return new JdbcTemplate(this.historyDataSource);
	}

}
