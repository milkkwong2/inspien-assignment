package com.inspien.assignment.global.event;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.inspien.assignment.global.event.ProcessEvent.Result;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessEventTest {

	@Test
	void logSummaryHasIdentifiersButNotDetail() {
		ProcessEvent event = new ProcessEvent(Instant.parse("2026-11-04T01:00:00Z"), "0HB1234567890", null,
				EventStage.RECEIVE, Result.REJECT, "seq=3 hong123/ITEM-77 PRICE_NOT_NUMBER");

		String summary = event.logSummary();

		assertThat(summary).contains("stage=RECEIVE", "result=REJECT", "messageId=0HB1234567890", "orderId=null");
		assertThat(summary).doesNotContain("hong123", "ITEM-77", "PRICE_NOT_NUMBER");
	}

}
