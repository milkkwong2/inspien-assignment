package com.inspien.assignment.tracking.application;

import java.util.List;

import org.springframework.stereotype.Service;

import com.inspien.assignment.global.error.ApiException;
import com.inspien.assignment.global.error.ErrorCode;
import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.global.event.ProcessEvents;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.retry.application.RetryService;
import com.inspien.assignment.retry.domain.Stage;
import com.inspien.assignment.tracking.application.TrackingViews.RetryView;

/**
 * 운영자 창구의 쓰기 하나: 재처리 (설계서 §5.4, D-26). 원인을 고친 뒤 막힌 주문의 자동 처리를 다시 시작한다.
 *
 * <p>H2만 바꾼다. Oracle STATUS에는 "사람이 확인"을 담을 곳이 없어 표시도 해제도 H2 한 곳에서 끝나므로 이중 쓰기가 아니다.
 * 트랜잭션을 걸지 않는다. 재처리는 RetryService에서 커밋되고, OPERATOR_RESET 이벤트는 그 뒤에 다른 이벤트처럼 따로 커밋된다
 * (D-13). 이벤트 기록이 실패해도 재처리는 된다.
 */
@Service
public class OperationsService {

	private final OrderService orders;

	private final RetryService retry;

	private final ProcessEvents events;

	public OperationsService(OrderService orders, RetryService retry, ProcessEvents events) {
		this.orders = orders;
		this.retry = retry;
		this.events = events;
	}

	public RetryView retry(String orderId) {
		if (this.orders.findById(orderId).isEmpty()) {
			throw new ApiException(ErrorCode.NOT_FOUND, "주문이 없습니다: " + orderId);
		}
		List<Stage> restarted = this.retry.restart(orderId);
		if (restarted.isEmpty()) {
			throw new ApiException(ErrorCode.NOTHING_TO_RETRY,
					"다시 시작할 단계가 없습니다. FAILED인 단계만 재처리합니다 (처리 중이거나 이미 끝남): " + orderId);
		}
		for (Stage stage : restarted) {
			this.events.publish(null, orderId, eventStage(stage), ProcessEvent.Result.OPERATOR_RESET,
					"운영자 재처리: 표시 해제, 횟수 0부터 다시 처리");
		}
		return new RetryView(orderId, restarted.stream().map(Stage::name).toList());
	}

	private static ProcessEvent.Stage eventStage(Stage stage) {
		return switch (stage) {
			case RECEIPT -> ProcessEvent.Stage.RECEIPT;
			case SHIPMENT -> ProcessEvent.Stage.SHIPMENT;
		};
	}

}
