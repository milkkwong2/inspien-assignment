package com.inspien.assignment.receipt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.inspien.assignment.global.config.AppProperties;
import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.order.domain.OrderStatus;
import com.inspien.assignment.receipt.application.ReceiptService;
import com.inspien.assignment.stage.application.StageService;
import com.inspien.assignment.stage.domain.Stage;
import com.inspien.assignment.stage.domain.StageState;
import com.inspien.assignment.stage.domain.StageStatus;
import com.inspien.assignment.support.FakeFtp;
import com.inspien.assignment.support.IntegrationFakes;
import com.inspien.assignment.support.IntegrationTest;
import com.inspien.assignment.support.MutableClock;
import com.inspien.assignment.support.TestOrders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IF-02 영수증 한 주기 (설계서 §7.2). 실패 구분과 재시도 규칙 D-09, D-10, D-22를 확인한다. 영수증은 H2에 자기 단계의
 * 완료만 적고 STATUS는 바꾸지 않는다 (D-26).
 */
@IntegrationTest
@Import({ IntegrationFakes.class, TestOrders.class })
@RecordApplicationEvents
class ReceiptFlowIntegrationTest {

	@Autowired
	TestOrders testOrders;

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
	StageService stages;

	@Autowired
	@Qualifier("historyDataSource")
	DataSource historyDataSource;

	@Autowired
	ApplicationEvents events;

	@Test
	void uploadsOneCsvPerOrderAndCompletesReceiptStageOnly() {
		List<String> ids = receiveTwoOrders("A");

		this.receipts.runCycle();

		String name = this.properties.applicant().name();
		assertThat(this.ftp.files).containsKeys("영수증_" + name + "_" + ids.get(0) + ".csv",
				"영수증_" + name + "_" + ids.get(1) + ".csv");
		String csv = new String(this.ftp.files.get("영수증_" + name + "_" + ids.get(1) + ".csv"), StandardCharsets.UTF_8);
		// 수신 시각은 시계의 처음 시각(IntegrationFakes.START)을 한국 시각으로 적은 것이다
		assertThat(csv).startsWith("﻿ORDER_ID,USER_ID,NAME,ITEM_ID,ITEM_NAME,PRICE,RECEIVED_AT\r\n")
			.contains(ids.get(1) + ",USER01,김민수A,ITEM10,\"노트북 거치대, 블랙\",32000,2026-10-31 14:05:12\r\n");
		assertThat(this.testOrders.stageStatuses(ids, Stage.RECEIPT)).containsOnly(StageStatus.DONE);
		assertThat(this.stages.find(ids.get(0), Stage.RECEIPT).orElseThrow().completedAt())
			.isEqualTo(IntegrationFakes.START);
		assertThat(this.testOrders.statuses(ids)).as("STATUS는 마감 스케줄러 몫").containsOnly(OrderStatus.N);
	}

	@Test
	void connectionFailureSkipsCycleWithoutCountingAndRecordsTransitionsOnce() {
		List<String> ids = receiveTwoOrders("B");
		this.ftp.down = true;

		this.receipts.runCycle();
		this.receipts.runCycle();
		this.receipts.runCycle();

		assertThat(this.testOrders.stageStatuses(ids, Stage.RECEIPT)).containsOnly(StageStatus.PENDING);
		assertThat(this.stages.find(ids.get(0), Stage.RECEIPT)).as("횟수를 세지 않는다").isEmpty();
		assertThat(results(ProcessEvent.Result.CYCLE_SKIPPED)).isEqualTo(1);

		this.ftp.down = false;
		this.receipts.runCycle();

		assertThat(this.testOrders.stageStatuses(ids, Stage.RECEIPT)).containsOnly(StageStatus.DONE);
		assertThat(results(ProcessEvent.Result.CONNECTION_RECOVERED)).isEqualTo(1);
	}

	@Test
	void uploadDataConnectionFailureEndsCycleWithoutCounting() {
		List<String> ids = receiveTwoOrders("F");
		this.ftp.dataConnectionFails = true;

		this.receipts.runCycle();

		assertThat(this.testOrders.stageStatuses(ids, Stage.RECEIPT)).containsOnly(StageStatus.PENDING);
		assertThat(this.stages.find(ids.get(0), Stage.RECEIPT)).as("횟수를 세지 않는다").isEmpty();
		assertThat(this.stages.find(ids.get(1), Stage.RECEIPT)).isEmpty();
		assertThat(results(ProcessEvent.Result.FAIL)).isZero();
		assertThat(results(ProcessEvent.Result.CYCLE_SKIPPED)).isEqualTo(1);

		// 백오프가 없으므로 시계를 옮기지 않아도 다음 주기에 바로 올린다
		this.ftp.dataConnectionFails = false;
		this.receipts.runCycle();

		assertThat(this.testOrders.stageStatuses(ids, Stage.RECEIPT)).containsOnly(StageStatus.DONE);
		assertThat(results(ProcessEvent.Result.CONNECTION_RECOVERED)).isEqualTo(1);
	}

	@Test
	void orderFailureBacksOffThenFailsAfterFiveAttempts() {
		List<String> ids = receiveTwoOrders("C");
		this.ftp.failUploadsContaining = ids.get(0);

		this.receipts.runCycle();
		assertThat(this.testOrders.stageStatus(ids.get(1), Stage.RECEIPT)).as("다른 주문은 막히지 않는다")
			.isEqualTo(StageStatus.DONE);

		// 백오프 중에는 시도하지 않는다
		this.receipts.runCycle();
		assertThat(failures(ids.get(0))).isEqualTo(1);

		for (int minutes : new int[] { 1, 2, 4, 8 }) {
			this.clock.advance(Duration.ofMinutes(minutes));
			this.receipts.runCycle();
		}
		assertThat(failures(ids.get(0))).isEqualTo(5);
		StageState failed = this.stages.find(ids.get(0), Stage.RECEIPT).orElseThrow();
		assertThat(failed.status()).isEqualTo(StageStatus.FAILED);
		assertThat(failed.attempts()).isEqualTo(5);
		assertThat(failed.nextAttemptAt()).as("표시와 함께 다음 시도를 지운다").isNull();

		// FAILED 단계는 시간이 지나도 고르지 않는다. 사람이 재처리 API로 다시 시작한다
		this.clock.advance(Duration.ofHours(1));
		this.receipts.runCycle();
		assertThat(failures(ids.get(0))).isEqualTo(5);
		assertThat(this.testOrders.statuses(ids)).containsOnly(OrderStatus.N);
	}

	@Test
	void partialFileIsReplacedUnderNextRevisionAndCompleteFileIsReused() {
		List<String> ids = receiveTwoOrders("D");
		String name = this.properties.applicant().name();
		this.ftp.files.put("영수증_" + name + "_" + ids.get(0) + ".csv", new byte[] { 1, 2, 3 });

		this.receipts.runCycle();

		assertThat(this.ftp.uploads).contains("영수증_" + name + "_" + ids.get(0) + "_r1.csv")
			.doesNotContain("영수증_" + name + "_" + ids.get(0) + ".csv");
		assertThat(this.testOrders.stageStatuses(ids, Stage.RECEIPT)).containsOnly(StageStatus.DONE);
		assertThat(this.stages.find(ids.get(0), Stage.RECEIPT).orElseThrow().artifact())
			.isEqualTo("영수증_" + name + "_" + ids.get(0) + "_r1.csv");
	}

	@Test
	void alreadyUploadedFileOnlyRecordsCompletion() {
		List<String> ids = receiveTwoOrders("E");
		this.receipts.runCycle();
		// 업로드 뒤 H2 완료 기록 전에 중단된 상황을 재현: 파일은 있고 영수증 단계는 PENDING
		new JdbcTemplate(this.historyDataSource).update(
				"UPDATE ORDER_STAGE SET COMPLETED_AT = NULL WHERE ORDER_ID = ? AND STAGE = 'RECEIPT'", ids.get(0));
		assertThat(this.testOrders.stageStatus(ids.get(0), Stage.RECEIPT)).isEqualTo(StageStatus.PENDING);
		int uploads = this.ftp.uploads.size();

		this.receipts.runCycle();

		assertThat(this.ftp.uploads).hasSize(uploads);
		assertThat(this.testOrders.stageStatus(ids.get(0), Stage.RECEIPT)).isEqualTo(StageStatus.DONE);
		assertThat(results(ProcessEvent.Result.SKIP)).isEqualTo(1);
	}

	@Test
	void orderWithoutReceiveRecordFailsWithoutRetry() {
		this.orders.insertAll(List.of(Order.received("Z900", "U", "I", "N", "A", "IN", "1")));

		this.receipts.runCycle();

		StageState stage = this.stages.find("Z900", Stage.RECEIPT).orElseThrow();
		assertThat(stage.status()).isEqualTo(StageStatus.FAILED);
		assertThat(stage.attempts()).isZero();
		assertThat(stage.lastError()).isEqualTo("H2에 수신 기록 없음");
		assertThat(results(ProcessEvent.Result.MISSING_RECORD)).isEqualTo(1);
	}

	private List<String> receiveTwoOrders(String mark) {
		String body = """
				<HEADER><USER_ID>USER01</USER_ID><NAME>김민수%1$s</NAME><ADDRESS>서울</ADDRESS></HEADER>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM09</ITEM_ID><ITEM_NAME>키보드</ITEM_NAME><PRICE>45000</PRICE></ITEM>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM10</ITEM_ID><ITEM_NAME>노트북 거치대, 블랙</ITEM_NAME><PRICE>32000</PRICE></ITEM>
				""".formatted(mark);
		return this.testOrders.receive(body);
	}

	private long results(ProcessEvent.Result result) {
		return this.events.stream(ProcessEvent.class).filter(event -> event.result() == result).count();
	}

	private long failures(String orderId) {
		return this.events.stream(ProcessEvent.class)
			.filter(event -> orderId.equals(event.orderId()) && event.result() == ProcessEvent.Result.FAIL)
			.count();
	}

}
