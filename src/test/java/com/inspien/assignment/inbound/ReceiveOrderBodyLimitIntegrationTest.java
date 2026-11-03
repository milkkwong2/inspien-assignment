package com.inspien.assignment.inbound;

import java.nio.charset.Charset;
import java.util.Map;

import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.inspien.assignment.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 수신 본문 상한 (설계서 §5.1, D-17). 상한을 넘으면 H2에 저장하기 전에 413으로 거절한다. 메가바이트 본문을 만들지 않도록
 * 상한을 1KB로 줄인다. Content-Length 없이(chunked) 오는 경우는 MockMvc로 만들 수 없어 LimitedBodyReaderTest에서 본다.
 */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.inbound.max-body-size=1KB")
class ReceiveOrderBodyLimitIntegrationTest {

	static final Charset EUC_KR = Charset.forName("EUC-KR");

	static final int LIMIT = 1024;

	@Autowired
	MockMvc mvc;

	@Autowired
	@Qualifier("historyDataSource")
	DataSource historyDataSource;

	@Test
	void bodyOverLimitIs413AndNotStored() throws Exception {
		byte[] body = paddedOrder("A", LIMIT + 1);
		long before = receivedMessageCount();

		this.mvc.perform(post("/orders").content(body))
			.andExpect(status().is(HttpStatus.CONTENT_TOO_LARGE.value()))
			.andExpect(header().doesNotExist("Retry-After"))
			.andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
			.andExpect(jsonPath("$.detail").value("본문이 너무 큽니다. 최대 1KB까지 받습니다."))
			.andExpect(jsonPath("$.retryable").value(false))
			.andExpect(jsonPath("$.traceId").isString())
			.andExpect(jsonPath("$.messageId").doesNotExist());

		assertThat(receivedMessageCount()).as("원본을 저장하지 않는다").isEqualTo(before);
	}

	@Test
	void bodyAtLimitIsProcessed() throws Exception {
		byte[] body = paddedOrder("B", LIMIT);

		this.mvc.perform(post("/orders").content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.summary.stored").value(1));
	}

	/**
	 * 본문 없는 요청도 Spring이 먼저 막지 않고 서비스까지 가서 기록된다. 상한 검사가 이 동작을 바꾸지 않는다. 빈 본문은
	 * 송신자 실수라 다른 파싱 실패처럼 원본을 남기고 400으로 거절한다.
	 */
	@Test
	void emptyBodyIsRecordedAndRejectedAsParseError() throws Exception {
		long before = receivedMessageCount();

		MvcResult result = this.mvc.perform(post("/orders"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("XML_PARSE_ERROR"))
			.andExpect(jsonPath("$.detail").value("본문이 비어 있습니다. HEADER·ITEM을 담아 보내 주세요."))
			.andExpect(jsonPath("$.retryable").value(false))
			.andExpect(jsonPath("$.messageId").isString())
			.andReturn();

		assertThat(receivedMessageCount()).isEqualTo(before + 1);
		String messageId = JsonPath.read(result.getResponse().getContentAsString(), "$.messageId");
		Map<String, Object> stored = history()
			.queryForMap("SELECT STATE, RESPONSE_STATUS FROM RECEIVED_MESSAGE WHERE ID = ?", messageId);
		assertThat(stored).containsEntry("STATE", "FAILED").containsEntry("RESPONSE_STATUS", 400);
	}

	/**
	 * 주문 1건을 뒤쪽 줄바꿈으로 채워 정확히 size바이트로 만든다.
	 */
	static byte[] paddedOrder(String mark, int size) {
		String order = """
				<HEADER><USER_ID>USER01</USER_ID><NAME>김민수%1$s</NAME><ADDRESS>서울</ADDRESS></HEADER>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM10</ITEM_ID><ITEM_NAME>마우스</ITEM_NAME><PRICE>32000</PRICE></ITEM>
				""".formatted(mark);
		byte[] orderBytes = order.getBytes(EUC_KR);
		String padding = "\n".repeat(size - orderBytes.length);
		String padded = order + padding;
		byte[] body = padded.getBytes(EUC_KR);
		assertThat(body).hasSize(size);
		return body;
	}

	long receivedMessageCount() {
		return history().queryForObject("SELECT COUNT(*) FROM RECEIVED_MESSAGE", Long.class);
	}

	JdbcTemplate history() {
		return new JdbcTemplate(this.historyDataSource);
	}

}
