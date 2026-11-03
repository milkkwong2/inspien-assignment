package com.inspien.assignment.order;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.inspien.assignment.global.event.EventStage;
import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.order.application.CloseService;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.order.domain.OrderStatus;
import com.inspien.assignment.receipt.application.ReceiptService;
import com.inspien.assignment.shipment.application.ShipmentService;
import com.inspien.assignment.stage.domain.Stage;
import com.inspien.assignment.stage.domain.StageStatus;
import com.inspien.assignment.support.FakeFtp;
import com.inspien.assignment.support.IntegrationFakes;
import com.inspien.assignment.support.IntegrationTest;
import com.inspien.assignment.support.TestOrders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 영수증·배송 독립 처리와 마감 (설계서 §6, §7.2, D-26). 두 단계는 서로를 기다리지 않고, 둘 다 DONE이 된 주문만 마감
 * 스케줄러가 N→Y로 바꾼다.
 */
@IntegrationTest
@Import({ IntegrationFakes.class, TestOrders.class })
@RecordApplicationEvents
class CloseFlowIntegrationTest {

	@Autowired
	TestOrders testOrders;

	@Autowired
	ReceiptService receipts;

	@Autowired
	ShipmentService shipments;

	@Autowired
	CloseService close;

	@Autowired
	OrderService orders;

	@Autowired
	FakeFtp ftp;

	@Autowired
	ApplicationEvents events;

	@Test
	void closesOnlyWhenBothStagesAreDone() {
		List<String> ids = receive("A");

		this.shipments.runCycle();
		this.close.runCycle();
		assertThat(this.testOrders.statuses(ids)).as("배송만 끝남").containsOnly(OrderStatus.N);

		this.receipts.runCycle();
		this.close.runCycle();
		assertThat(this.testOrders.statuses(ids)).containsOnly(OrderStatus.Y);
		assertThat(closeEvents(ids.get(0))).isEqualTo(1);

		// 다음 주기: 이미 Y라 대상이 아니고, 이벤트도 더 남지 않는다
		this.close.runCycle();
		assertThat(closeEvents(ids.get(0))).isEqualTo(1);
	}

	@Test
	void ftpOutageDoesNotHoldShipment() {
		List<String> ids = receive("B");
		this.ftp.down = true;

		this.receipts.runCycle();
		this.shipments.runCycle();
		this.close.runCycle();

		assertThat(this.testOrders.stageStatus(ids.get(0), Stage.RECEIPT)).isEqualTo(StageStatus.PENDING);
		assertThat(this.testOrders.stageStatus(ids.get(0), Stage.SHIPMENT)).as("FTP 장애가 출고를 막지 않는다")
			.isEqualTo(StageStatus.DONE);
		assertThat(this.testOrders.statuses(ids)).containsOnly(OrderStatus.N);

		this.ftp.down = false;
		this.receipts.runCycle();
		this.close.runCycle();
		assertThat(this.testOrders.statuses(ids)).containsOnly(OrderStatus.Y);
	}

	@Test
	void failedReceiptKeepsOrderOpenWhileShipmentFinishes() {
		this.orders.insertAll(List.of(Order.received("Z910", "U", "I", "N", "A", "IN", "1")));

		// 수신 기록이 없는 주문: 두 단계 모두 재시도 없이 FAILED, Y가 되지 않는다
		this.receipts.runCycle();
		this.shipments.runCycle();
		this.close.runCycle();

		assertThat(this.testOrders.stageStatus("Z910", Stage.RECEIPT)).isEqualTo(StageStatus.FAILED);
		assertThat(this.testOrders.stageStatus("Z910", Stage.SHIPMENT)).isEqualTo(StageStatus.FAILED);
		assertThat(this.orders.findById("Z910").orElseThrow().status()).isEqualTo(OrderStatus.N);
	}

	private List<String> receive(String mark) {
		String body = """
				<HEADER><USER_ID>USER01</USER_ID><NAME>김민수%1$s</NAME><ADDRESS>서울</ADDRESS></HEADER>
				<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM09</ITEM_ID><ITEM_NAME>키보드</ITEM_NAME><PRICE>45000</PRICE></ITEM>
				""".formatted(mark);
		return this.testOrders.receive(body);
	}

	private long closeEvents(String orderId) {
		return this.events.stream(ProcessEvent.class)
			.filter(event -> orderId.equals(event.orderId()) && event.stage() == EventStage.CLOSE
					&& event.result() == ProcessEvent.Result.SUCCESS)
			.count();
	}

}
