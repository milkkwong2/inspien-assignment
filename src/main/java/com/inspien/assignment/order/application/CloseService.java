package com.inspien.assignment.order.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.inspien.assignment.global.config.AppProperties;
import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.global.event.ProcessEvents;
import com.inspien.assignment.order.domain.OrderStatus;
import com.inspien.assignment.retry.application.CycleRunner;
import com.inspien.assignment.retry.application.RetryService;

/**
 * 마감 (설계서 §7.2, D-26). STATUS=N이고 영수증·배송이 모두 DONE인 주문을 조건부로 N→Y로 바꾼다.
 *
 * <p>Y를 쓰는 곳은 여기 하나다. 영수증·배송 스케줄러가 끝날 때마다 상대 단계를 확인해 Y를 쓰면, 독립이라면서 서로를 알아야
 * 하고 Y 직전에 멈춘 주문을 위한 정리 분기가 따로 필요하다. 매 주기 같은 조건으로 찾으므로 평소 처리와 중단 뒤 복구가 같은
 * 경로다.
 *
 * <p>외부 연결이 없어 연결 실패도 없다. 쇼핑몰 Oracle·H2가 안 되면 그 주기를 끝내고 다음 주기에 다시 한다. 갱신 0건(이미
 * Y)은 오류가 아니다.
 */
@Service
public class CloseService {

	private static final String CYCLE_NAME = "마감";

	private final OrderService orders;

	private final RetryService stages;

	private final ProcessEvents events;

	private final CycleRunner cycles;

	private final int batchSize;

	public CloseService(OrderService orders, RetryService stages, ProcessEvents events, CycleRunner cycles,
			AppProperties properties) {
		this.orders = orders;
		this.stages = stages;
		this.events = events;
		this.cycles = cycles;
		this.batchSize = properties.scheduler().batchSize();
	}

	public void runCycle() {
		this.cycles.selectAndRunAll(CYCLE_NAME, this::selectAllDone, this::close);
	}

	private List<String> selectAllDone() {
		List<String> candidates = this.orders.findIdsByStatus(OrderStatus.N);
		return this.stages.selectAllDone(candidates, this.batchSize);
	}

	private void close(String orderId) {
		if (this.orders.close(orderId)) {
			this.events.publishForOrder(orderId, ProcessEvent.Stage.CLOSE, ProcessEvent.Result.SUCCESS,
					"영수증·배송 모두 완료, STATUS N→Y");
		}
	}

}
