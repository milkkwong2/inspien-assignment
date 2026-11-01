package com.inspien.assignment.retry.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.global.event.ProcessEvents;
import com.inspien.assignment.retry.domain.Stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 영수증·배송·재처리가 함께 쓰는 단계 이벤트의 단계 변환과 문구.
 */
class StageEventsTest {

	final List<ProcessEvent> published = new ArrayList<>();

	final Clock clock = Clock.fixed(Instant.parse("2026-10-31T05:05:12Z"), ZoneId.of("Asia/Seoul"));

	final StageEvents events = new StageEvents(new ProcessEvents(event -> this.published.add((ProcessEvent) event), this.clock), this.clock);

	@Test
	void mapsEveryRetryStageToEventStage() {
		for (Stage stage : Stage.values()) {
			assertThat(StageEvents.of(stage).name()).isEqualTo(stage.name());
		}
	}

	@Test
	void failureShowsNextAttemptInLocalTimeUntilExhausted() {
		this.events.failed("A001", Stage.RECEIPT,
				new RetryService.Failure(2, false, Instant.parse("2026-10-31T05:07:12.345Z")), "업로드 실패");
		this.events.failed("A001", Stage.SHIPMENT, new RetryService.Failure(5, true, null), "등록 거절");

		assertThat(this.published).extracting(ProcessEvent::stage, ProcessEvent::result, ProcessEvent::detail)
			.containsExactly(
					tuple(ProcessEvent.Stage.RECEIPT, ProcessEvent.Result.FAIL,
							"2회째 실패, 다음 시도 14:07:12: 업로드 실패"),
					tuple(ProcessEvent.Stage.SHIPMENT, ProcessEvent.Result.FAIL,
							"5회째 실패, 배송 FAILED (사람이 확인): 등록 거절"));
	}

	@Test
	void failuresWithoutRetryNameTheStage() {
		this.events.missingRecord("Z900", Stage.RECEIPT);
		this.events.failedWithoutRetry("Z900", Stage.SHIPMENT, "다른 배송이 있음");
		this.events.operatorReset("Z900", Stage.SHIPMENT);

		assertThat(this.published).extracting(ProcessEvent::result, ProcessEvent::detail)
			.containsExactly(
					tuple(ProcessEvent.Result.MISSING_RECORD,
							"H2에 수신 기록이 없어 영수증 FAILED. 주문번호 Z900"),
					tuple(ProcessEvent.Result.FAIL, "배송 FAILED (사람이 확인): 다른 배송이 있음"),
					tuple(ProcessEvent.Result.OPERATOR_RESET,
							"운영자 재처리: 표시 해제, 횟수 0부터 다시 처리"));
	}

}
