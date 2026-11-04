package com.inspien.assignment.stage.application;

import org.springframework.stereotype.Component;

import com.inspien.assignment.stage.domain.Stage;

/**
 * 영수증·배송이 똑같이 적는 단계 실패: 재시도 상태를 바꾸고 그에 맞는 이벤트를 남긴다 (D-10, D-22). 두 Facade가 같은
 * 순서와 문구로 적도록 한 곳에 둔다.
 */
@Component
public class StageFailures {

	private final StageService stages;

	private final StageEvents stageEvents;

	public StageFailures(StageService stages, StageEvents stageEvents) {
		this.stages = stages;
		this.stageEvents = stageEvents;
	}

	/**
	 * 건별 실패 1회. 횟수를 세고 백오프하며, 한도에 닿으면 그 단계 FAILED.
	 */
	public void orderFailed(String orderId, Stage stage, String error) {
		StageService.Failure failure = this.stages.recordFailure(orderId, stage, error);
		this.stageEvents.failed(orderId, stage, failure, error);
	}

	/**
	 * H2에 수신 기록이 없는 주문: 수신일시를 지어내지 않는다. 다시 해도 결과가 같아 재시도 없이 사람에게 넘긴다 (D-22).
	 */
	public void missingRecord(String orderId, Stage stage) {
		this.stages.markFailed(orderId, stage, "H2에 수신 기록 없음");
		this.stageEvents.missingRecord(orderId, stage);
	}

}
