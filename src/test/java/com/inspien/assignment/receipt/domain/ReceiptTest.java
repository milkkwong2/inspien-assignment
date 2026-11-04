package com.inspien.assignment.receipt.domain;

import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.inspien.assignment.order.domain.Order;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 파일명·재시도 번호 규칙. CSV 내용은 CsvReceiptFormatterTest가 본다.
 */
class ReceiptTest {

	static final Receipt RECEIPT = new Receipt(
			Order.received("A002", "USER01", "ITEM10", "김민수", "서울", "노트북 거치대, 블랙", "32000"),
			OffsetDateTime.parse("2026-10-31T14:05:12+09:00"), "홍길동");

	@Test
	void fileNameHasApplicantNameAndOrderIdWithoutTime() {
		assertThat(RECEIPT.fileName(0)).isEqualTo("영수증_홍길동_A002.csv");
		assertThat(RECEIPT.fileName(1)).isEqualTo("영수증_홍길동_A002_r1.csv");
	}

	@Test
	void recognizesOnlyThisOrdersFiles() {
		assertThat(RECEIPT.revisionOf("영수증_홍길동_A002.csv")).hasValue(0);
		assertThat(RECEIPT.revisionOf("영수증_홍길동_A002_r12.csv")).hasValue(12);
		assertThat(RECEIPT.revisionOf("영수증_홍길동_A0021.csv")).isEmpty();
		assertThat(RECEIPT.revisionOf("영수증_김철수_A002.csv")).as("다른 지원자").isEmpty();
	}

	@Test
	void uploadPlanFollowsTheListing() {
		long size = 100;

		assertThat(UploadPlan.of(RECEIPT, Map.of("영수증_김철수_A002.csv", 100L), size))
			.isEqualTo(UploadPlan.initialUpload("영수증_홍길동_A002.csv"));
		assertThat(UploadPlan.of(RECEIPT, Map.of("영수증_홍길동_A002.csv", 100L), size))
			.isEqualTo(UploadPlan.alreadyUploaded("영수증_홍길동_A002.csv"));
		assertThat(UploadPlan.of(RECEIPT, Map.of("영수증_홍길동_A002.csv", 40L), size))
			.isEqualTo(UploadPlan.replacePartial("영수증_홍길동_A002_r1.csv"));
		// 정본은 재시도 번호가 가장 큰 파일
		assertThat(UploadPlan.of(RECEIPT, Map.of("영수증_홍길동_A002.csv", 40L, "영수증_홍길동_A002_r1.csv", 100L), size))
			.isEqualTo(UploadPlan.alreadyUploaded("영수증_홍길동_A002_r1.csv"));
	}

}
