package com.inspien.assignment.inbound;

import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.inspien.assignment.global.transaction.OrderTx;
import com.inspien.assignment.numbering.application.NumberingService;
import com.inspien.assignment.numbering.domain.IdFormat;
import com.inspien.assignment.numbering.domain.IdSequence;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.order.domain.OrderStatus;
import com.inspien.assignment.support.IntegrationTest;
import com.inspien.assignment.support.MutableClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IF-01 수신 흐름 (설계서 §7.1). 중복 판단·재처리 규칙 D-15, D-16, D-24와 응답 계약 D-17을 확인한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ReceiveOrderIntegrationTest {

	static final Charset EUC_KR = Charset.forName("EUC-KR");

	@Autowired
	MockMvc mvc;

	@Autowired
	MutableClock clock;

	@Autowired
	NumberingService numbering;

	@Autowired
	@Qualifier(OrderTx.MANAGER)
	PlatformTransactionManager orderTx;

	@MockitoSpyBean
	OrderService orders;

	@Test
	void storesValidOrdersAndReportsRejectionsAndWarnings() throws Exception {
		String body = sampleLike("A");

		MvcResult result = this.mvc.perform(post("/orders").content(body.getBytes(EUC_KR)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.traceId").isString())
			.andExpect(jsonPath("$.data.messageId").isString())
			.andExpect(jsonPath("$.data.replayed").value(false))
			.andExpect(jsonPath("$.data.notice").doesNotExist())
			.andExpect(jsonPath("$.data.summary.stored").value(3))
			.andExpect(jsonPath("$.data.summary.rejected").value(1))
			.andExpect(jsonPath("$.data.summary.warnings").value(1))
			.andExpect(jsonPath("$.data.stored[0].seq").value(1))
			.andExpect(jsonPath("$.data.stored[0].userId").value("USER02"))
			.andExpect(jsonPath("$.data.rejected[0].seq").value(4))
			.andExpect(jsonPath("$.data.rejected[0].reason").value("HEADER_NOT_FOUND"))
			.andExpect(jsonPath("$.data.warnings[0].reason").value("HEADER_WITHOUT_ITEM"))
			.andReturn();

		assertThat(receivedAt(result)).endsWith("+09:00");
		List<String> orderIds = orderIds(result);
		assertThat(this.orders.findByIds(orderIds)).hasSize(3)
			.allSatisfy(order -> assertThat(order.status()).isEqualTo(OrderStatus.RECEIVED))
			.anySatisfy(order -> assertThat(order.name()).isEqualTo("이서연" + "A"));
	}

	@Test
	void resendWithinOneMinuteOfCompletionReturnsPreviousResult() throws Exception {
		byte[] body = sampleLike("B").getBytes(EUC_KR);
		MvcResult first = this.mvc.perform(post("/orders").content(body)).andExpect(status().isOk()).andReturn();
		this.clock.advance(Duration.ofSeconds(59));

		MvcResult second = this.mvc.perform(post("/orders").content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.replayed").value(true))
			.andExpect(jsonPath("$.data.notice").isString())
			.andReturn();

		assertThat(messageId(second)).isEqualTo(messageId(first));
		assertThat(orderIds(second)).isEqualTo(orderIds(first));
		assertThat(receivedAt(second)).isEqualTo(receivedAt(first));
	}

	@Test
	void sameBodyAfterWindowIsNewOrder() throws Exception {
		byte[] body = sampleLike("C").getBytes(EUC_KR);
		MvcResult first = this.mvc.perform(post("/orders").content(body)).andExpect(status().isOk()).andReturn();
		this.clock.advance(Duration.ofSeconds(61));

		MvcResult second = this.mvc.perform(post("/orders").content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.replayed").value(false))
			.andReturn();

		assertThat(messageId(second)).isNotEqualTo(messageId(first));
		assertThat(orderIds(second)).doesNotContainAnyElementsOf(orderIds(first));
	}

	@Test
	void unreadableBodyIs400AndResendIsJudgedAgain() throws Exception {
		byte[] body = "<HEADER><NAME>홍</HEADER>".getBytes(EUC_KR);

		MvcResult first = this.mvc.perform(post("/orders").content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("XML_PARSE_ERROR"))
			.andExpect(jsonPath("$.retryable").value(false))
			.andExpect(jsonPath("$.messageId").isString())
			.andReturn();
		MvcResult second = this.mvc.perform(post("/orders").content(body))
			.andExpect(status().isBadRequest())
			.andReturn();

		// 400으로 실패한 메시지는 저장한 것도 번호도 없으므로 다음 요청은 새 메시지다 (D-24)
		assertThat(JsonPath.<String>read(second.getResponse().getContentAsString(), "$.messageId"))
			.isNotEqualTo(JsonPath.<String>read(first.getResponse().getContentAsString(), "$.messageId"));
	}

	@Test
	void oracleOutageIs503AndResendStoresWithPreassignedIds() throws Exception {
		byte[] body = sampleLike("D").getBytes(EUC_KR);
		doThrow(new CannotCreateTransactionException("down")).doCallRealMethod().when(this.orders).insertAll(anyList());

		MvcResult failed = this.mvc.perform(post("/orders").content(body))
			.andExpect(status().isServiceUnavailable())
			.andExpect(header().string("Retry-After", "30"))
			.andExpect(jsonPath("$.code").value("ORDER_DB_UNAVAILABLE"))
			.andExpect(jsonPath("$.retryable").value(true))
			.andReturn();
		String messageId = JsonPath.read(failed.getResponse().getContentAsString(), "$.messageId");

		// 재전송에 시간 제한이 없다 (D-24)
		this.clock.advance(Duration.ofHours(2));
		MvcResult resent = this.mvc.perform(post("/orders").content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.replayed").value(false))
			.andReturn();

		assertThat(messageId(resent)).isEqualTo(messageId);
		assertThat(this.orders.findByIds(orderIds(resent))).hasSize(3);
	}

	@Test
	void unknownCommitResultIsResolvedByLookupOnResend() throws Exception {
		byte[] body = sampleLike("E").getBytes(EUC_KR);
		// 커밋은 됐는데 응답(커밋 확인)을 받지 못한 경우: 별도 트랜잭션에서 커밋을 끝낸 뒤 예외를 던진다
		TransactionTemplate committed = new TransactionTemplate(this.orderTx);
		committed.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		doAnswer(invocation -> {
			committed.executeWithoutResult(status -> callReal(invocation));
			throw new RecoverableDataAccessException("commit timeout");
		}).doCallRealMethod().when(this.orders).insertAll(anyList());

		this.mvc.perform(post("/orders").content(body)).andExpect(status().isServiceUnavailable());
		MvcResult resent = this.mvc.perform(post("/orders").content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.replayed").value(true))
			.andReturn();

		assertThat(this.orders.findByIds(orderIds(resent))).hasSize(3);
	}

	@Test
	void oracleRejectionIs500() throws Exception {
		doThrow(new JpaSystemException(new RuntimeException("ORA-12899"))).when(this.orders).insertAll(anyList());

		this.mvc.perform(post("/orders").content(sampleLike("F").getBytes(EUC_KR)))
			.andExpect(status().isInternalServerError())
			.andExpect(header().doesNotExist("Retry-After"))
			.andExpect(jsonPath("$.code").value("ORDER_DB_REJECTED"))
			.andExpect(jsonPath("$.retryable").value(false));
	}

	@Test
	void differentOrderAlreadyUnderPreassignedIdIsUnexpectedState() throws Exception {
		String taken = this.numbering.next(IdSequence.ORDER);
		String next = IdFormat.format(IdFormat.parse(taken).orElseThrow() + 1);
		this.orders.insertAll(List.of(Order.received(next, "X", "X", "X", "X", "X", "1")));

		this.mvc.perform(post("/orders").content(sampleLike("G").getBytes(EUC_KR)))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("UNEXPECTED_STATE"));
	}

	@Test
	void concurrentResendWaitsAndGetsSameResult() throws Exception {
		byte[] body = sampleLike("H").getBytes(EUC_KR);
		CountDownLatch inserting = new CountDownLatch(1);
		doAnswer(invocation -> {
			inserting.countDown();
			Thread.sleep(500);
			return invocation.callRealMethod();
		}).when(this.orders).insertAll(anyList());

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<MvcResult> first = executor.submit(() -> this.mvc.perform(post("/orders").content(body)).andReturn());
			inserting.await();
			Future<MvcResult> second = executor.submit(() -> this.mvc.perform(post("/orders").content(body)).andReturn());

			List<MvcResult> results = new ArrayList<>(List.of(first.get(), second.get()));
			assertThat(results).allSatisfy(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));
			assertThat(orderIds(results.get(1))).isEqualTo(orderIds(results.get(0)));
			assertThat(JsonPath.<Boolean>read(results.get(1).getResponse().getContentAsString(), "$.data.replayed"))
				.isTrue();
		}
		finally {
			executor.shutdownNow();
		}
	}

	/**
	 * 샘플과 같은 특징의 가상 데이터. 테스트마다 다른 본문이 되도록 이름에 표시를 붙인다.
	 */
	static String sampleLike(String mark) {
		return """
				<HEADER><USER_ID>USER01</USER_ID><NAME>김민수%1$s</NAME><ADDRESS>서울시 강남구</ADDRESS></HEADER>
				<ITEM><USER_ID>USER02</USER_ID><ITEM_ID>ITEM20</ITEM_ID><ITEM_NAME>무선 마우스</ITEM_NAME><PRICE>25000</PRICE></ITEM>
				<HEADER><USER_ID>USER02</USER_ID><NAME>이서연%1$s</NAME><ADDRESS>부산시 해운대구</ADDRESS></HEADER>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM10</ITEM_ID><ITEM_NAME>노트북 거치대, 블랙</ITEM_NAME><PRICE>32000</PRICE></ITEM>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM11</ITEM_ID><ITEM_NAME>USB 허브</ITEM_NAME><PRICE>18000</PRICE></ITEM>
				<ITEM><USER_ID>USER09</USER_ID><ITEM_ID>ITEM30</ITEM_ID><ITEM_NAME>키보드</ITEM_NAME><PRICE>45000</PRICE></ITEM>
				<HEADER><USER_ID>USER03</USER_ID><NAME>박지훈%1$s</NAME><ADDRESS>대전시</ADDRESS></HEADER>
				""".formatted(mark);
	}

	static void callReal(InvocationOnMock invocation) {
		try {
			invocation.callRealMethod();
		}
		catch (Throwable ex) {
			throw new IllegalStateException(ex);
		}
	}

	static String messageId(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.messageId");
	}

	static String receivedAt(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.receivedAt");
	}

	static List<String> orderIds(MvcResult result) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), "$.data.stored[*].orderId");
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2026-10-31T05:05:12Z"), ZoneId.of("Asia/Seoul"));
		}

	}

}
