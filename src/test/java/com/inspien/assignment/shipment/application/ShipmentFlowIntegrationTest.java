package com.inspien.assignment.shipment.application;

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.CannotCreateTransactionException;

import com.inspien.assignment.global.config.AppProperties;
import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.inbound.application.ReceiveOrderService;
import com.inspien.assignment.inbound.application.ReceiveResult;
import com.inspien.assignment.numbering.application.NumberingService;
import com.inspien.assignment.numbering.domain.IdSequence;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.order.domain.OrderStatus;
import com.inspien.assignment.receipt.application.ReceiptService;
import com.inspien.assignment.retry.application.RetryService;
import com.inspien.assignment.retry.domain.Stage;
import com.inspien.assignment.shipment.domain.Shipment;
import com.inspien.assignment.support.FakeFtp;
import com.inspien.assignment.support.IntegrationTest;
import com.inspien.assignment.support.MutableClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * IF-03 배송 (설계서 §5.3, §7.2). 멱등성 규칙 D-03(보완), D-19와 실패 구분 D-10, D-22를 확인한다.
 */
@IntegrationTest
@RecordApplicationEvents
class ShipmentFlowIntegrationTest {

	static final Charset EUC_KR = Charset.forName("EUC-KR");

	@Autowired
	ReceiveOrderService receive;

	@Autowired
	ReceiptService receipts;

	@Autowired
	ShipmentService shipments;

	@Autowired
	OrderService orders;

	@Autowired
	RetryService retry;

	@Autowired
	NumberingService numbering;

	@Autowired
	AppProperties properties;

	@Autowired
	@Qualifier("shipmentDataSource")
	DataSource shipmentDataSource;

	@MockitoSpyBean
	ShipmentRegistry registry;

	@Autowired
	ApplicationEvents events;

	@Test
	void registersShipmentWithMyKeyAndMovesToShipped() {
		List<String> ids = receivedAndReceipted("A");

		this.shipments.runCycle();

		assertThat(statuses(ids)).containsOnly(OrderStatus.SHIPPED);
		JdbcTemplate jdbc = new JdbcTemplate(this.shipmentDataSource);
		assertThat(jdbc.queryForList("SELECT ORDER_ID FROM SHIPMENT_TB WHERE APPLICANT_KEY = ?", String.class,
				this.properties.applicant().key()))
			.containsAll(ids);
		// 다른 지원자의 SHIPMENT_ID A001과 겹쳐도 내 키로 따로 들어간다
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SHIPMENT_TB WHERE APPLICANT_KEY = 'OTHER-APPLICANT-1'",
				Integer.class))
			.isEqualTo(1);
	}

	@Test
	void existingShipmentForOrderOnlyUpdatesStatus() {
		List<String> ids = receivedAndReceipted("B");
		this.shipments.runCycle();
		// INSERT 후 STATUS 갱신 전에 중단된 상황
		resetStatus(ids.get(0), "RECEIPT_SENT");

		this.shipments.runCycle();

		assertThat(this.orders.findById(ids.get(0)).orElseThrow().status()).isEqualTo(OrderStatus.SHIPPED);
		assertThat(shipmentCount(ids.get(0))).isEqualTo(1);
	}

	@Test
	void lateCommitOfPreviousAttemptEndsAsOneShipment() {
		List<String> ids = receivedAndReceipted("C");
		String orderId = ids.get(0);
		// 앞 시도: 미리 배정한 번호로 INSERT가 늦게 커밋돼, 다음 시도의 조회에는 보이지 않았다
		String shipmentId = this.retry.artifactOrAssign(orderId, Stage.SHIPMENT,
				() -> this.numbering.next(IdSequence.SHIPMENT));
		Order order = this.orders.findById(orderId).orElseThrow();
		this.registry.insert(Shipment.of(shipmentId, order));
		doReturn(Optional.empty()).when(this.registry).findByOrderId(orderId);

		this.shipments.runCycle();

		assertThat(this.orders.findById(orderId).orElseThrow().status()).isEqualTo(OrderStatus.SHIPPED);
		assertThat(shipmentCount(orderId)).isEqualTo(1);
	}

	@Test
	void differentShipmentUnderPreassignedIdFailsWithoutRetry() {
		List<String> ids = receivedAndReceipted("D");
		String orderId = ids.get(0);
		String shipmentId = this.retry.artifactOrAssign(orderId, Stage.SHIPMENT,
				() -> this.numbering.next(IdSequence.SHIPMENT));
		this.registry.insert(new Shipment(shipmentId, "X999", "OTHER", "다른 주소"));

		this.shipments.runCycle();

		assertThat(this.orders.findById(orderId).orElseThrow().status()).isEqualTo(OrderStatus.SHIPMENT_FAILED);
	}

	@Test
	void shipmentDbOutageSkipsCycleWithoutCounting() {
		List<String> ids = receivedAndReceipted("E");
		doThrow(new CannotCreateTransactionException("down")).when(this.registry).findByOrderId(anyString());

		this.shipments.runCycle();
		this.shipments.runCycle();

		assertThat(statuses(ids)).containsOnly(OrderStatus.RECEIPT_SENT);
		assertThat(this.retry.find(ids.get(0), Stage.SHIPMENT).orElseThrow().attempts()).isZero();
		assertThat(count(ProcessEvent.Result.CYCLE_SKIPPED)).isEqualTo(1);
	}

	@Test
	void rejectedInsertIsCountedPerOrder() {
		List<String> ids = receivedAndReceipted("F");
		doThrow(new JpaSystemException(new RuntimeException("ORA-12899"))).when(this.registry).insert(any());

		this.shipments.runCycle();

		assertThat(this.retry.find(ids.get(0), Stage.SHIPMENT).orElseThrow().attempts()).isEqualTo(1);
		assertThat(this.retry.find(ids.get(1), Stage.SHIPMENT).orElseThrow().attempts()).as("다른 주문은 계속 시도")
			.isEqualTo(1);
		assertThat(statuses(ids)).containsOnly(OrderStatus.RECEIPT_SENT);
	}

	private List<String> receivedAndReceipted(String mark) {
		String body = """
				<HEADER><USER_ID>USER01</USER_ID><NAME>김민수%1$s</NAME><ADDRESS>서울 %1$s</ADDRESS></HEADER>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM09</ITEM_ID><ITEM_NAME>키보드</ITEM_NAME><PRICE>45000</PRICE></ITEM>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM10</ITEM_ID><ITEM_NAME>마우스</ITEM_NAME><PRICE>32000</PRICE></ITEM>
				""".formatted(mark);
		ReceiveResult result = this.receive.receive(body.getBytes(EUC_KR), null);
		List<String> ids = result.stored().stream().map(ReceiveResult.Stored::orderId).toList();
		this.receipts.runCycle();
		assertThat(statuses(ids)).containsOnly(OrderStatus.RECEIPT_SENT);
		return ids;
	}

	private void resetStatus(String orderId, String status) {
		new JdbcTemplate(this.shipmentDataSource).update(
				"UPDATE ORDER_TB SET STATUS = ? WHERE ORDER_ID = ? AND APPLICANT_KEY = ?", status, orderId,
				this.properties.applicant().key());
	}

	private int shipmentCount(String orderId) {
		return new JdbcTemplate(this.shipmentDataSource).queryForObject(
				"SELECT COUNT(*) FROM SHIPMENT_TB WHERE ORDER_ID = ? AND APPLICANT_KEY = ?", Integer.class, orderId,
				this.properties.applicant().key());
	}

	private List<OrderStatus> statuses(List<String> ids) {
		return this.orders.findByIds(ids).stream().map(Order::status).toList();
	}

	private long count(ProcessEvent.Result result) {
		return this.events.stream(ProcessEvent.class).filter(e -> e.result() == result).count();
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
