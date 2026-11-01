package com.inspien.assignment.receipt;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.inspien.assignment.global.config.AppProperties;
import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.inbound.application.ReceiveOrderService;
import com.inspien.assignment.inbound.application.ReceiveResult;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.order.domain.OrderStatus;
import com.inspien.assignment.receipt.application.ReceiptService;
import com.inspien.assignment.support.FakeFtp;
import com.inspien.assignment.support.IntegrationTest;
import com.inspien.assignment.support.MutableClock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IF-02 영수증 한 주기 (설계서 §7.2). 실패 구분과 재시도 규칙 D-09, D-10, D-22를 확인한다.
 */
@IntegrationTest
@RecordApplicationEvents
class ReceiptFlowIntegrationTest {

	static final Charset EUC_KR = Charset.forName("EUC-KR");

	@Autowired
	ReceiveOrderService receive;

	@Autowired
	ReceiptService receipts;

	@Autowired
	OrderService orders;

	@Autowired
	FakeFtp ftp;

	@Autowired
	MutableClock clock;

	@Autowired
	AppProperties properties;

	@Autowired
	@Qualifier("orderDataSource")
	DataSource orderDataSource;

	@Autowired
	ApplicationEvents events;

	@BeforeEach
	void resetFtp() {
		this.ftp.down = false;
		this.ftp.failUploadsContaining = null;
	}

	@Test
	void uploadsOneCsvPerOrderAndMovesToReceiptSent() {
		List<String> ids = receiveTwoOrders("A");

		this.receipts.runCycle();

		String name = this.properties.applicant().name();
		assertThat(this.ftp.files).containsKeys("영수증_" + name + "_" + ids.get(0) + ".csv",
				"영수증_" + name + "_" + ids.get(1) + ".csv");
		String csv = new String(this.ftp.files.get("영수증_" + name + "_" + ids.get(1) + ".csv"), StandardCharsets.UTF_8);
		assertThat(csv).startsWith("﻿ORDER_ID,USER_ID,NAME,ITEM_ID,ITEM_NAME,PRICE,RECEIVED_AT\r\n")
			.contains(ids.get(1) + ",USER01,김민수A,ITEM10,\"노트북 거치대, 블랙\",32000,2026-10-31 14:05:12\r\n");
		assertThat(statuses(ids)).containsOnly(OrderStatus.RECEIPT_SENT);
	}

	@Test
	void connectionFailureSkipsCycleWithoutCountingAndRecordsTransitionsOnce() {
		List<String> ids = receiveTwoOrders("B");
		this.ftp.down = true;

		this.receipts.runCycle();
		this.receipts.runCycle();
		this.receipts.runCycle();

		assertThat(statuses(ids)).containsOnly(OrderStatus.RECEIVED);
		assertThat(results(ProcessEvent.Result.CYCLE_SKIPPED)).isEqualTo(1);

		this.ftp.down = false;
		this.receipts.runCycle();

		assertThat(statuses(ids)).containsOnly(OrderStatus.RECEIPT_SENT);
		assertThat(results(ProcessEvent.Result.CONNECTION_RECOVERED)).isEqualTo(1);
	}

	@Test
	void orderFailureBacksOffThenFailsAfterFiveAttemptsAndOperatorResetRestarts() {
		List<String> ids = receiveTwoOrders("C");
		this.ftp.failUploadsContaining = ids.get(0);

		this.receipts.runCycle();
		assertThat(this.orders.findById(ids.get(1)).orElseThrow().status()).as("다른 주문은 막히지 않는다")
			.isEqualTo(OrderStatus.RECEIPT_SENT);

		// 백오프 중에는 시도하지 않는다
		this.receipts.runCycle();
		assertThat(failures(ids.get(0))).isEqualTo(1);

		for (int minutes : new int[] { 1, 2, 4, 8 }) {
			this.clock.advance(Duration.ofMinutes(minutes));
			this.receipts.runCycle();
		}
		assertThat(failures(ids.get(0))).isEqualTo(5);
		assertThat(this.orders.findById(ids.get(0)).orElseThrow().status()).isEqualTo(OrderStatus.RECEIPT_FAILED);

		// 운영자가 원인을 고치고 STATUS를 되돌린다
		this.ftp.failUploadsContaining = null;
		new JdbcTemplate(this.orderDataSource).update(
				"UPDATE ORDER_TB SET STATUS = 'RECEIVED' WHERE ORDER_ID = ? AND APPLICANT_KEY = ?", ids.get(0),
				this.properties.applicant().key());
		this.receipts.runCycle();

		assertThat(this.orders.findById(ids.get(0)).orElseThrow().status()).isEqualTo(OrderStatus.RECEIPT_SENT);
		assertThat(results(ProcessEvent.Result.OPERATOR_RESET)).isEqualTo(1);
	}

	@Test
	void partialFileIsReplacedUnderNextRevisionAndCompleteFileIsReused() {
		List<String> ids = receiveTwoOrders("D");
		String name = this.properties.applicant().name();
		this.ftp.files.put("영수증_" + name + "_" + ids.get(0) + ".csv", new byte[] { 1, 2, 3 });

		this.receipts.runCycle();

		assertThat(this.ftp.uploads).contains("영수증_" + name + "_" + ids.get(0) + "_r1.csv")
			.doesNotContain("영수증_" + name + "_" + ids.get(0) + ".csv");
		assertThat(statuses(ids)).containsOnly(OrderStatus.RECEIPT_SENT);
	}

	@Test
	void alreadyUploadedFileOnlyUpdatesStatus() {
		List<String> ids = receiveTwoOrders("E");
		this.receipts.runCycle();
		// STATUS 갱신 전에 중단된 상황을 재현: 파일은 있고 STATUS는 RECEIVED
		new JdbcTemplate(this.orderDataSource).update(
				"UPDATE ORDER_TB SET STATUS = 'RECEIVED' WHERE ORDER_ID = ? AND APPLICANT_KEY = ?", ids.get(0),
				this.properties.applicant().key());
		int uploads = this.ftp.uploads.size();

		this.receipts.runCycle();

		assertThat(this.ftp.uploads).hasSize(uploads);
		assertThat(this.orders.findById(ids.get(0)).orElseThrow().status()).isEqualTo(OrderStatus.RECEIPT_SENT);
	}

	@Test
	void orderWithoutReceiveRecordFailsWithoutRetry() {
		this.orders.insertAll(List.of(Order.received("Z900", "U", "I", "N", "A", "IN", "1")));

		this.receipts.runCycle();

		assertThat(this.orders.findById("Z900").orElseThrow().status()).isEqualTo(OrderStatus.RECEIPT_FAILED);
		assertThat(results(ProcessEvent.Result.MISSING_RECORD)).isEqualTo(1);
	}

	private List<String> receiveTwoOrders(String mark) {
		String body = """
				<HEADER><USER_ID>USER01</USER_ID><NAME>김민수%1$s</NAME><ADDRESS>서울</ADDRESS></HEADER>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM09</ITEM_ID><ITEM_NAME>키보드</ITEM_NAME><PRICE>45000</PRICE></ITEM>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM10</ITEM_ID><ITEM_NAME>노트북 거치대, 블랙</ITEM_NAME><PRICE>32000</PRICE></ITEM>
				""".formatted(mark);
		ReceiveResult result = this.receive.receive(body.getBytes(EUC_KR), null);
		return result.stored().stream().map(ReceiveResult.Stored::orderId).toList();
	}

	private List<OrderStatus> statuses(List<String> ids) {
		return this.orders.findByIds(ids).stream().map(Order::status).toList();
	}

	private long results(ProcessEvent.Result result) {
		return this.events.stream(ProcessEvent.class).filter(e -> e.result() == result).count();
	}

	private long failures(String orderId) {
		return this.events.stream(ProcessEvent.class)
			.filter(e -> orderId.equals(e.orderId()) && e.result() == ProcessEvent.Result.FAIL)
			.count();
	}

	@TestConfiguration
	static class Fakes {

		@Bean
		@Primary
		FakeFtp fakeFtp() {
			return new FakeFtp();
		}

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(Instant.parse("2026-10-31T05:05:12Z"), ZoneId.of("Asia/Seoul"));
		}

	}

}
