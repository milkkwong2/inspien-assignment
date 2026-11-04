package com.inspien.assignment.support;

import java.nio.charset.Charset;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.inspien.assignment.inbound.application.ReceiveOrderFacade;
import com.inspien.assignment.inbound.application.ReceiveResult;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.order.domain.OrderStatus;
import com.inspien.assignment.stage.application.StageService;
import com.inspien.assignment.stage.domain.Stage;
import com.inspien.assignment.stage.domain.StageState;
import com.inspien.assignment.stage.domain.StageStatus;

/**
 * 통합 테스트의 주문 준비와 상태 읽기. 필요한 테스트 클래스가 {@code @Import}한다.
 *
 * <p>주문은 실제 수신 경로({@link ReceiveOrderFacade})로 받는다. 채번, H2 수신 기록, Oracle 저장이 운영과 같게 일어나야
 * 영수증·배송·마감이 그 주문을 처리한다.
 */
@TestComponent
public class TestOrders {

	static final Charset EUC_KR = Charset.forName("EUC-KR");

	private final ReceiveOrderFacade receive;

	private final OrderService orders;

	private final StageService stages;

	TestOrders(ReceiveOrderFacade receive, OrderService orders, StageService stages) {
		this.receive = receive;
		this.orders = orders;
		this.stages = stages;
	}

	/**
	 * 본문을 EUC-KR로 보내 저장된 주문번호를 순서대로 돌려준다. 완료 후 1분 안에 같은 본문을 다시 보내면 이전 결과를 받으므로
	 * (D-15) 테스트마다 본문을 다르게 만든다.
	 */
	public List<String> receive(String body) {
		byte[] bytes = body.getBytes(EUC_KR);
		ReceiveResult result = this.receive.receive(bytes, null);
		return result.stored().stream().map(ReceiveResult.Stored::orderId).toList();
	}

	/**
	 * Oracle STATUS(N/Y).
	 */
	public List<OrderStatus> statuses(List<String> orderIds) {
		return this.orders.findByIds(orderIds).stream().map(Order::status).toList();
	}

	/**
	 * H2의 단계 상태. 아직 기록이 없으면 PENDING이다.
	 */
	public StageStatus stageStatus(String orderId, Stage stage) {
		StageState state = this.stages.find(orderId, stage).orElse(null);
		return StageState.statusOf(state);
	}

	public List<StageStatus> stageStatuses(List<String> orderIds, Stage stage) {
		return orderIds.stream().map(orderId -> stageStatus(orderId, stage)).toList();
	}

}
