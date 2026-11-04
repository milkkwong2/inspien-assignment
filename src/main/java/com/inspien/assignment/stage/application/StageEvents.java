package com.inspien.assignment.stage.application;

import java.time.Clock;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.inspien.assignment.global.event.EventStage;
import com.inspien.assignment.global.event.ProcessEvent.Result;
import com.inspien.assignment.global.event.ProcessEvents;
import com.inspien.assignment.stage.domain.Stage;

/**
 * 단계(영수증·배송)의 재시도 상태가 바뀔 때 남기는 처리 이벤트 (D-13, D-26). 영수증·배송·재처리 API가 같은 단계 변환과
 * 문구를 쓰게 한 곳에 모은다. 단계별 처리 결과(업로드, 등록)의 문구는 각 Facade에 둔다.
 */
@Component
public class StageEvents {

	private final ProcessEvents events;

	private final Clock clock;

	public StageEvents(ProcessEvents events, Clock clock) {
		this.events = events;
		this.clock = clock;
	}

	/**
	 * 단계 → 이벤트 구간(EventStage). 이벤트에는 재시도 대상이 아닌 수신·마감도 있어 enum이 따로다.
	 */
	static EventStage of(Stage stage) {
		return switch (stage) {
			case RECEIPT -> EventStage.RECEIPT;
			case SHIPMENT -> EventStage.SHIPMENT;
		};
	}

	/**
	 * 건별 실패 1회. 한도에 닿았으면 그 단계 FAILED(사람이 확인), 아니면 다음 시도 시각.
	 */
	public void failed(String orderId, Stage stage, StageService.Failure failure, String error) {
		String detail;
		if (failure.exhausted()) {
			detail = "%d회째 실패, %s FAILED (사람이 확인): %s".formatted(failure.attempts(), label(stage), error);
		}
		else {
			LocalTime next = LocalTime.ofInstant(failure.nextAttemptAt(), this.clock.getZone())
				.truncatedTo(ChronoUnit.SECONDS);
			detail = "%d회째 실패, 다음 시도 %s: %s".formatted(failure.attempts(), next, error);
		}
		this.events.publishForOrder(orderId, of(stage), Result.FAIL, detail);
	}

	/**
	 * 다시 해도 결과가 같은 실패: 재시도 없이 그 단계 FAILED.
	 */
	public void failedWithoutRetry(String orderId, Stage stage, String error) {
		this.events.publishForOrder(orderId, of(stage), Result.FAIL,
				"%s FAILED (사람이 확인): %s".formatted(label(stage), error));
	}

	/**
	 * H2에 수신 기록이 없어 재시도 없이 그 단계 FAILED (D-22).
	 */
	public void missingRecord(String orderId, Stage stage) {
		this.events.publishForOrder(orderId, of(stage), Result.MISSING_RECORD,
				"H2에 수신 기록이 없어 %s FAILED. 주문번호 %s".formatted(label(stage), orderId));
	}

	public void operatorReset(String orderId, Stage stage) {
		this.events.publishForOrder(orderId, of(stage), Result.OPERATOR_RESET, "운영자 재처리: 표시 해제, 횟수 0부터 다시 처리");
	}

	private static String label(Stage stage) {
		return switch (stage) {
			case RECEIPT -> "영수증";
			case SHIPMENT -> "배송";
		};
	}

}
