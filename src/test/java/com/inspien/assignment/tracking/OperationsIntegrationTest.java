package com.inspien.assignment.tracking;

import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.List;

import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.receipt.application.ReceiptFacade;
import com.inspien.assignment.shipment.application.ShipmentFacade;
import com.inspien.assignment.stage.application.StageService;
import com.inspien.assignment.stage.domain.Stage;
import com.inspien.assignment.stage.domain.StageState;
import com.inspien.assignment.stage.domain.StageStatus;
import com.inspien.assignment.support.IntegrationFakes;
import com.inspien.assignment.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 운영자 재처리 API (설계서 §5.4, D-26). H2의 "사람이 확인" 표시와 횟수만 조건부로 지우고, 완료 시각과 결과물은 둔다.
 */
@IntegrationTest
@Import(IntegrationFakes.class)
@AutoConfigureMockMvc
@RecordApplicationEvents
class OperationsIntegrationTest {

	static final Charset EUC_KR = Charset.forName("EUC-KR");

	@Autowired
	MockMvc mvc;

	@Autowired
	ReceiptFacade receipts;

	@Autowired
	ShipmentFacade shipments;

	@Autowired
	OrderService orders;

	@Autowired
	StageService stages;

	@Autowired
	@Qualifier("historyDataSource")
	DataSource historyDataSource;

	@Autowired
	ApplicationEvents events;

	@Test
	void restartsFailedStageFromZeroAndSchedulerPicksItUp() throws Exception {
		String orderId = receive("A");
		for (int i = 0; i < 5; i++) {
			this.stages.recordFailure(orderId, Stage.RECEIPT, "업로드 권한 오류");
		}
		assertThat(stage(orderId, Stage.RECEIPT).status()).isEqualTo(StageStatus.FAILED);

		this.mvc.perform(post("/operations/orders/{id}/retry", orderId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.orderId").value(orderId))
			.andExpect(jsonPath("$.data.restarted", contains("RECEIPT")));

		StageState restarted = stage(orderId, Stage.RECEIPT);
		assertThat(restarted.status()).isEqualTo(StageStatus.PENDING);
		assertThat(restarted.attempts()).isZero();
		assertThat(restarted.nextAttemptAt()).isNull();
		assertThat(restarted.lastError()).as("원인 확인용으로 남긴다").isEqualTo("업로드 권한 오류");
		assertThat(this.events.stream(ProcessEvent.class)
			.filter(event -> orderId.equals(event.orderId()) && event.result() == ProcessEvent.Result.OPERATOR_RESET)
			.count()).isEqualTo(1);

		this.receipts.runCycle();
		assertThat(stage(orderId, Stage.RECEIPT).status()).isEqualTo(StageStatus.DONE);
	}

	@Test
	void restartsEveryFailedStageAndKeepsPreassignedShipmentId() throws Exception {
		// 수신 기록이 없는 주문: 두 단계 모두 재시도 없이 FAILED
		this.orders.insertAll(List.of(Order.received("Z930", "U", "I", "N", "A", "IN", "1")));
		String shipmentId = this.stages.artifactOrAssign("Z930", Stage.SHIPMENT, () -> "Z930");
		this.receipts.runCycle();
		this.shipments.runCycle();
		OffsetDateTime before = updatedAt("Z930", Stage.SHIPMENT);

		this.mvc.perform(post("/operations/orders/{id}/retry", "Z930"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.restarted", contains("RECEIPT", "SHIPMENT")));

		assertThat(stage("Z930", Stage.SHIPMENT).artifact()).isEqualTo(shipmentId);
		assertThat(updatedAt("Z930", Stage.SHIPMENT)).as("벌크 UPDATE도 UPDATED_AT을 바꾼다").isAfter(before);
	}

	@Test
	void conflictsWhenNothingIsFailedAndHidesOtherApplicants() throws Exception {
		String pending = receive("B");

		this.mvc.perform(post("/operations/orders/{id}/retry", pending))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NOTHING_TO_RETRY"));
		this.mvc.perform(post("/operations/orders/{id}/retry", "Z998"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		// 다른 지원자의 주문은 없는 주문이다
		this.mvc.perform(post("/operations/orders/{id}/retry", "A150")).andExpect(status().isNotFound());
	}

	private String receive(String mark) throws Exception {
		String body = """
				<HEADER><USER_ID>USER01</USER_ID><NAME>김민수%1$s</NAME><ADDRESS>서울</ADDRESS></HEADER>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM10</ITEM_ID><ITEM_NAME>마우스</ITEM_NAME><PRICE>32000</PRICE></ITEM>
				""".formatted(mark);
		String response = this.mvc.perform(post("/orders").content(body.getBytes(EUC_KR)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.data.stored[0].orderId");
	}

	private StageState stage(String orderId, Stage stage) {
		return this.stages.find(orderId, stage).orElseThrow();
	}

	private OffsetDateTime updatedAt(String orderId, Stage stage) {
		return new JdbcTemplate(this.historyDataSource).queryForObject(
				"SELECT UPDATED_AT FROM ORDER_STAGE WHERE ORDER_ID = ? AND STAGE = ?", OffsetDateTime.class, orderId,
				stage.name());
	}

}
