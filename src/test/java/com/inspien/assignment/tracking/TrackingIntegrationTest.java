package com.inspien.assignment.tracking;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.inspien.assignment.order.application.CloseFacade;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.receipt.application.ReceiptFacade;
import com.inspien.assignment.shipment.application.ShipmentFacade;
import com.inspien.assignment.support.IntegrationFakes;
import com.inspien.assignment.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IF-04 처리 추적 (설계서 §5.4). 수신 → 영수증·배송 → 마감을 거친 주문과 메시지를 조회 API로 확인한다. 주문은 Oracle
 * STATUS(N/Y)와 단계별 상태(PENDING·DONE·FAILED)를 함께 보여 준다 (D-26).
 */
@IntegrationTest
@Import(IntegrationFakes.class)
@AutoConfigureMockMvc
class TrackingIntegrationTest {

	static final Charset EUC_KR = Charset.forName("EUC-KR");

	@Autowired
	MockMvc mvc;

	@Autowired
	ReceiptFacade receipts;

	@Autowired
	ShipmentFacade shipments;

	@Autowired
	CloseFacade close;

	@Autowired
	OrderService orders;

	@Test
	void orderDetailCombinesOracleStatusWithHistory() throws Exception {
		MvcResult received = receive("A");
		String messageId = JsonPath.read(received.getResponse().getContentAsString(), "$.data.messageId");
		String orderId = JsonPath.read(received.getResponse().getContentAsString(), "$.data.stored[0].orderId");
		this.receipts.runCycle();
		this.shipments.runCycle();
		this.close.runCycle();

		this.mvc.perform(get("/tracking/orders/{id}", orderId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.traceId").isString())
			.andExpect(jsonPath("$.data.status").value("Y"))
			.andExpect(jsonPath("$.data.receipt.status").value("DONE"))
			.andExpect(jsonPath("$.data.receipt.completedAt", startsWith("2026-10-31T14:05:12")))
			.andExpect(jsonPath("$.data.shipment.status").value("DONE"))
			.andExpect(jsonPath("$.data.messageId").value(messageId))
			.andExpect(jsonPath("$.data.receivedAt", startsWith("2026-10-31T14:05:12")))
			.andExpect(jsonPath("$.data.receipt.fileName", startsWith("영수증_")))
			.andExpect(jsonPath("$.data.receipt.attempts").value(0))
			.andExpect(jsonPath("$.data.shipment.shipmentId").isString())
			.andExpect(jsonPath("$.data.events[*].stage", hasItems("RECEIVE", "RECEIPT", "SHIPMENT", "CLOSE")))
			.andExpect(jsonPath("$.data.events[*].result", hasItems("SUCCESS")));
	}

	@Test
	void messageDetailShowsSameListsAsReceiveResponse() throws Exception {
		MvcResult received = receive("B");
		String messageId = JsonPath.read(received.getResponse().getContentAsString(), "$.data.messageId");

		this.mvc.perform(get("/tracking/messages/{id}", messageId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.state").value("COMPLETED"))
			.andExpect(jsonPath("$.data.responseStatus").value(200))
			.andExpect(jsonPath("$.data.summary.stored").value(1))
			.andExpect(jsonPath("$.data.rejected[0].reason").value("HEADER_NOT_FOUND"))
			.andExpect(jsonPath("$.data.events[*].result", hasItems("SUCCESS", "REJECT")));
	}

	@Test
	void failedMessagesCanBeFoundByState() throws Exception {
		MvcResult failed = this.mvc.perform(post("/orders").content("<HEADER>".getBytes(EUC_KR)))
			.andExpect(status().isBadRequest())
			.andReturn();
		String messageId = JsonPath.read(failed.getResponse().getContentAsString(), "$.messageId");

		this.mvc.perform(get("/tracking/messages").param("state", "FAILED"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[*].messageId", hasItems(messageId)))
			.andExpect(jsonPath("$.data[?(@.messageId == '%s')].errorCode", messageId)
				.value(contains("XML_PARSE_ERROR")))
			.andExpect(jsonPath("$.data[?(@.messageId == '%s')].responseStatus", messageId).value(contains(400)))
			.andExpect(jsonPath("$.page.limit").value(50));
		this.mvc.perform(get("/tracking/messages/{id}", messageId))
			.andExpect(jsonPath("$.data.errorCode").value("XML_PARSE_ERROR"))
			.andExpect(jsonPath("$.data.responseStatus").value(400));
	}

	@Test
	void messageListAppliesOnlyGivenConditions() throws Exception {
		String first = JsonPath.read(receive("M").getResponse().getContentAsString(), "$.data.messageId");
		String second = JsonPath.read(receive("N").getResponse().getContentAsString(), "$.data.messageId");
		// 테스트 시계가 고정이라 두 메시지의 수신 시각이 같다
		String receivedAt = "2026-10-31T14:05:12+09:00";

		MvcResult afterFirst = this.mvc
			.perform(get("/tracking/messages").param("state", "COMPLETED").param("after", first))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[*].messageId", hasItems(second)))
			.andReturn();
		List<String> ids = JsonPath.read(afterFirst.getResponse().getContentAsString(), "$.data[*].messageId");
		assertThat(ids).as("커서 뒤만").allSatisfy(id -> assertThat(id).isGreaterThan(first));
		List<Map<String, Object>> completed = JsonPath.read(afterFirst.getResponse().getContentAsString(),
				"$.data[?(@.messageId == '" + second + "')]");
		assertThat(completed).singleElement().satisfies(item -> {
			assertThat(item).containsEntry("state", "COMPLETED").containsEntry("responseStatus", 200);
			assertThat(item.get("errorCode")).as("완료 메시지는 오류 코드가 없다").isNull();
		});

		this.mvc.perform(get("/tracking/messages").param("from", receivedAt).param("after", first))
			.andExpect(jsonPath("$.data[*].messageId", hasItems(second)));
		MvcResult beforeReceived = this.mvc.perform(get("/tracking/messages").param("to", receivedAt))
			.andExpect(status().isOk())
			.andReturn();
		List<String> none = JsonPath.read(beforeReceived.getResponse().getContentAsString(), "$.data[*].messageId");
		assertThat(none).as("to는 그 시각을 포함하지 않는다").doesNotContain(first, second);
	}

	@Test
	void orderWithoutHistoryFollowsOracleY() throws Exception {
		// H2를 잃은 뒤의 끝난 주문: Y를 따른다. 이력만 없다 (설계서 §4.2)
		this.orders.insertAll(List.of(Order.received("Z901", "U", "I", "N", "A", "IN", "1")));
		this.orders.close("Z901");

		this.mvc.perform(get("/tracking/orders/{id}", "Z901"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("Y"))
			.andExpect(jsonPath("$.data.receipt.status").value("DONE"))
			.andExpect(jsonPath("$.data.shipment.status").value("DONE"))
			.andExpect(jsonPath("$.data.messageId").doesNotExist());
	}

	@Test
	void orderListFiltersByStageBeforePaging() throws Exception {
		String done = JsonPath.read(receive("E").getResponse().getContentAsString(), "$.data.stored[0].orderId");
		this.receipts.runCycle();
		String pending = JsonPath.read(receive("F").getResponse().getContentAsString(), "$.data.stored[0].orderId");

		// 앞 주문(영수증 DONE)을 먼저 자르고 걸렀다면 이 페이지가 비었을 것이다
		this.mvc
			.perform(get("/tracking/orders").param("receipt", "PENDING")
				.param("limit", "1")
				.param("after", previous(done)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].orderId").value(pending))
			.andExpect(jsonPath("$.data[0].status").value("N"))
			.andExpect(jsonPath("$.data[0].receipt").value("PENDING"))
			.andExpect(jsonPath("$.data[0].shipment").value("PENDING"))
			.andExpect(jsonPath("$.page.nextAfter").doesNotExist());
		this.mvc
			.perform(get("/tracking/orders").param("receipt", "DONE")
				.param("shipment", "PENDING")
				.param("after", previous(done)))
			.andExpect(jsonPath("$.data[*].orderId", hasItems(done)));
	}

	@Test
	void orderListShowsOnlyInProgressOrders() throws Exception {
		// 끝난 주문 둘: 처리를 다 거친 주문과 H2 기록이 없는 주문
		String finished = JsonPath.read(receive("G").getResponse().getContentAsString(), "$.data.stored[0].orderId");
		this.receipts.runCycle();
		this.shipments.runCycle();
		this.close.runCycle();
		this.orders.insertAll(List.of(Order.received("Z902", "U", "I", "N", "A", "IN", "1")));
		this.orders.close("Z902");
		String inProgress = JsonPath.read(receive("H").getResponse().getContentAsString(), "$.data.stored[0].orderId");

		MvcResult all = this.mvc.perform(get("/tracking/orders").param("after", previous(finished)).param("limit", "200"))
			.andExpect(status().isOk())
			.andReturn();
		List<String> listed = JsonPath.read(all.getResponse().getContentAsString(), "$.data[*].orderId");
		assertThat(listed).contains(inProgress).doesNotContain(finished, "Z902");
		MvcResult done = this.mvc
			.perform(get("/tracking/orders").param("receipt", "DONE")
				.param("shipment", "DONE")
				.param("after", previous(finished))
				.param("limit", "200"))
			.andExpect(status().isOk())
			.andReturn();
		List<String> doneListed = JsonPath.read(done.getResponse().getContentAsString(), "$.data[*].orderId");
		assertThat(doneListed).as("끝난 주문은 DONE 조건으로도 목록에 없다").doesNotContain(finished, "Z902");

		// 끝난 주문은 주문번호로 본다
		this.mvc.perform(get("/tracking/orders/{id}", finished))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("Y"))
			.andExpect(jsonPath("$.data.receipt.status").value("DONE"))
			.andExpect(jsonPath("$.data.shipment.status").value("DONE"));
		this.mvc.perform(get("/tracking/orders/{id}", "Z902"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("Y"))
			.andExpect(jsonPath("$.data.receipt.status").value("DONE"));
	}

	@Test
	void orderListPagesWithCursor() throws Exception {
		receive("C");
		receive("D");

		MvcResult first = this.mvc.perform(get("/tracking/orders").param("shipment", "PENDING").param("limit", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.page.nextAfter").isString())
			.andReturn();
		String after = JsonPath.read(first.getResponse().getContentAsString(), "$.page.nextAfter");

		MvcResult second = this.mvc
			.perform(get("/tracking/orders").param("shipment", "PENDING").param("limit", "1").param("after", after))
			.andExpect(status().isOk())
			.andReturn();
		List<String> ids = JsonPath.read(second.getResponse().getContentAsString(), "$.data[*].orderId");
		assertThat(ids).hasSize(1).allSatisfy(id -> assertThat(id).isGreaterThan(after));
	}

	@Test
	void rejectsBadParametersAndUnknownIds() throws Exception {
		this.mvc.perform(get("/tracking/orders").param("limit", "201"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		this.mvc.perform(get("/tracking/orders").param("receipt", "SHIPPED"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		this.mvc.perform(get("/tracking/orders/{id}", "Z998"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		// 다른 지원자의 주문은 보이지 않는다
		this.mvc.perform(get("/tracking/orders/{id}", "A150")).andExpect(status().isNotFound());
	}

	/**
	 * 이 주문번호 바로 앞의 커서. 같은 글자 안에서만 쓴다.
	 */
	static String previous(String orderId) {
		return orderId.charAt(0) + "%03d".formatted(Integer.parseInt(orderId.substring(1)) - 1);
	}

	MvcResult receive(String mark) throws Exception {
		String body = """
				<HEADER><USER_ID>USER01</USER_ID><NAME>김민수%1$s</NAME><ADDRESS>서울</ADDRESS></HEADER>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM10</ITEM_ID><ITEM_NAME>마우스</ITEM_NAME><PRICE>32000</PRICE></ITEM>
				<ITEM><USER_ID>USER09</USER_ID><ITEM_ID>ITEM30</ITEM_ID><ITEM_NAME>키보드</ITEM_NAME><PRICE>45000</PRICE></ITEM>
				""".formatted(mark);
		return this.mvc.perform(post("/orders").content(body.getBytes(EUC_KR))).andExpect(status().isOk()).andReturn();
	}

}
