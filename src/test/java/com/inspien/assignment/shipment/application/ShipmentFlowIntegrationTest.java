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
import com.inspien.assignment.retry.application.RetryService;
import com.inspien.assignment.retry.domain.Stage;
import com.inspien.assignment.retry.domain.StageState;
import com.inspien.assignment.retry.domain.StageStatus;
import com.inspien.assignment.shipment.domain.Shipment;
import com.inspien.assignment.support.FakeFtp;
import com.inspien.assignment.support.IntegrationTest;
import com.inspien.assignment.support.MutableClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/**
 * IF-03 배송 (설계서 §5.3, §7.2). 멱등성 규칙 D-03(보완), D-19와 실패 구분 D-10, D-22를 확인한다. 배송은 영수증을
 * 기다리지 않고, H2에 자기 단계의 완료만 적는다 (D-26).
 */
@IntegrationTest
@RecordApplicationEvents
class ShipmentFlowIntegrationTest {

	static final Charset EUC_KR = Charset.forName("EUC-KR");

	@Autowired
	ReceiveOrderService receive;

	@Autowired
	ShipmentService shipments;

	@Autowired
	OrderService orders;

	@MockitoSpyBean
	RetryService retry;

	@Autowired
	NumberingService numbering;

	@Autowired
	AppProperties properties;

	@Autowired
	@Qualifier("shipmentDataSource")
	DataSource shipmentDataSource;

	@Autowired
	@Qualifier("historyDataSource")
	DataSource historyDataSource;

	@MockitoSpyBean
	ShipmentRegistry registry;

	@Autowired
	ApplicationEvents events;

	@Test
	void registersShipmentWithMyKeyWithoutWaitingForReceipt() {
		List<String> ids = received("A");

		this.shipments.runCycle();

		assertThat(shipmentStatuses(ids)).containsOnly(StageStatus.DONE);
		assertThat(this.retry.find(ids.get(0), Stage.RECEIPT)).as("영수증은 아직 시작도 안 했다").isEmpty();
		assertThat(statuses(ids)).as("STATUS는 마감 스케줄러 몫").containsOnly(OrderStatus.N);
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
	void existingShipmentForOrderOnlyRecordsCompletion() {
		List<String> ids = received("B");
		this.shipments.runCycle();
		// INSERT 후 H2 완료 기록 전에 중단된 상황
		new JdbcTemplate(this.historyDataSource).update(
				"UPDATE ORDER_STAGE SET COMPLETED_AT = NULL WHERE ORDER_ID = ? AND STAGE = 'SHIPMENT'", ids.get(0));
		assertThat(shipmentStatus(ids.get(0))).isEqualTo(StageStatus.PENDING);

		this.shipments.runCycle();

		assertThat(shipmentStatus(ids.get(0))).isEqualTo(StageStatus.DONE);
		assertThat(shipmentCount(ids.get(0))).isEqualTo(1);
	}

	@Test
	void lateCommitOfPreviousAttemptEndsAsOneShipment() {
		List<String> ids = received("C");
		String orderId = ids.get(0);
		// 앞 시도: 미리 배정한 번호로 INSERT가 늦게 커밋돼, 다음 시도의 조회에는 보이지 않았다
		String shipmentId = this.retry.artifactOrAssign(orderId, Stage.SHIPMENT,
				() -> this.numbering.next(IdSequence.SHIPMENT));
		Order order = this.orders.findById(orderId).orElseThrow();
		this.registry.insert(Shipment.of(shipmentId, order));
		doReturn(Optional.empty()).when(this.registry).findByOrderId(orderId);

		this.shipments.runCycle();

		assertThat(shipmentStatus(orderId)).isEqualTo(StageStatus.DONE);
		assertThat(shipmentCount(orderId)).isEqualTo(1);
	}

	@Test
	void differentShipmentUnderPreassignedIdFailsWithoutRetry() {
		List<String> ids = received("D");
		String orderId = ids.get(0);
		String shipmentId = this.retry.artifactOrAssign(orderId, Stage.SHIPMENT,
				() -> this.numbering.next(IdSequence.SHIPMENT));
		this.registry.insert(new Shipment(shipmentId, "X999", "OTHER", "다른 주소"));

		this.shipments.runCycle();

		StageState stage = this.retry.find(orderId, Stage.SHIPMENT).orElseThrow();
		assertThat(stage.status()).isEqualTo(StageStatus.FAILED);
		assertThat(stage.attempts()).as("재시도 없이").isZero();
		assertThat(stage.artifact()).as("미리 배정한 번호는 그대로").isEqualTo(shipmentId);
	}

	@Test
	void shipmentDbOutageSkipsCycleWithoutCounting() {
		List<String> ids = received("E");
		doThrow(new CannotCreateTransactionException("down")).when(this.registry).findByOrderId(anyString());

		this.shipments.runCycle();
		this.shipments.runCycle();

		assertThat(shipmentStatuses(ids)).containsOnly(StageStatus.PENDING);
		assertThat(this.retry.find(ids.get(0), Stage.SHIPMENT).orElseThrow().attempts()).isZero();
		assertThat(count(ProcessEvent.Result.CYCLE_SKIPPED)).isEqualTo(1);
	}

	@Test
	void rejectedInsertIsCountedPerOrder() {
		List<String> ids = received("F");
		doThrow(new JpaSystemException(new RuntimeException("ORA-12899"))).when(this.registry).insert(any());

		this.shipments.runCycle();

		assertThat(this.retry.find(ids.get(0), Stage.SHIPMENT).orElseThrow().attempts()).isEqualTo(1);
		assertThat(this.retry.find(ids.get(1), Stage.SHIPMENT).orElseThrow().attempts()).as("다른 주문은 계속 시도")
			.isEqualTo(1);
		assertThat(shipmentStatuses(ids)).containsOnly(StageStatus.PENDING);
	}

	/**
	 * 배송 INSERT는 커밋됐는데 H2 완료 기록이 실패: 우리 쪽 저장소 장애라 그 주기를 끝낸다. 배송 건별 실패로 세거나 운송
	 * DB 연결 실패로 남기지 않는다. 다음 주기에 기존 배송을 찾아 완료만 적는다 (D-22, D-26).
	 */
	@Test
	void completionRecordFailureEndsCycleWithoutCountingOrBlamingShipmentDb() {
		completionRecordFailureEndsCycle("G", new JpaSystemException(new RuntimeException("H2 쓰기 실패")));
	}

	@Test
	void completionRecordConnectionFailureIsNotShipmentDbOutage() {
		completionRecordFailureEndsCycle("H", new CannotCreateTransactionException("H2 연결 실패"));
	}

	private void completionRecordFailureEndsCycle(String mark, RuntimeException h2Failure) {
		List<String> ids = received(mark);
		doThrow(h2Failure).when(this.retry).recordCompletion(eq(ids.get(0)), eq(Stage.SHIPMENT), anyString());
		long failsBefore = count(ProcessEvent.Result.FAIL);
		long skipsBefore = count(ProcessEvent.Result.CYCLE_SKIPPED);

		this.shipments.runCycle();

		assertThat(shipmentCount(ids.get(0))).as("INSERT는 커밋됐다").isEqualTo(1);
		assertThat(this.retry.find(ids.get(0), Stage.SHIPMENT).orElseThrow().attempts()).as("건별 실패로 세지 않는다")
			.isZero();
		assertThat(count(ProcessEvent.Result.FAIL)).isEqualTo(failsBefore);
		assertThat(count(ProcessEvent.Result.CYCLE_SKIPPED)).as("운송 DB 장애가 아니다").isEqualTo(skipsBefore);
		assertThat(shipmentCount(ids.get(1))).as("주기를 끝내 다음 주문은 처리하지 않는다").isZero();

		doCallRealMethod().when(this.retry).recordCompletion(anyString(), any(), any());
		this.shipments.runCycle();

		assertThat(shipmentStatuses(ids)).containsOnly(StageStatus.DONE);
		assertThat(shipmentCount(ids.get(0))).isEqualTo(1);
	}

	private List<String> received(String mark) {
		String body = """
				<HEADER><USER_ID>USER01</USER_ID><NAME>김민수%1$s</NAME><ADDRESS>서울 %1$s</ADDRESS></HEADER>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM09</ITEM_ID><ITEM_NAME>키보드</ITEM_NAME><PRICE>45000</PRICE></ITEM>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM10</ITEM_ID><ITEM_NAME>마우스</ITEM_NAME><PRICE>32000</PRICE></ITEM>
				""".formatted(mark);
		ReceiveResult result = this.receive.receive(body.getBytes(EUC_KR), null);
		return result.stored().stream().map(ReceiveResult.Stored::orderId).toList();
	}

	private int shipmentCount(String orderId) {
		return new JdbcTemplate(this.shipmentDataSource).queryForObject(
				"SELECT COUNT(*) FROM SHIPMENT_TB WHERE ORDER_ID = ? AND APPLICANT_KEY = ?", Integer.class, orderId,
				this.properties.applicant().key());
	}

	private List<OrderStatus> statuses(List<String> ids) {
		return this.orders.findByIds(ids).stream().map(Order::status).toList();
	}

	private List<StageStatus> shipmentStatuses(List<String> ids) {
		return ids.stream().map(this::shipmentStatus).toList();
	}

	private StageStatus shipmentStatus(String orderId) {
		return StageState.statusOf(this.retry.find(orderId, Stage.SHIPMENT).orElse(null));
	}

	private long count(ProcessEvent.Result result) {
		return this.events.stream(ProcessEvent.class).filter(event -> event.result() == result).count();
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
