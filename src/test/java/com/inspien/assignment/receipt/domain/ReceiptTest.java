package com.inspien.assignment.receipt.domain;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.inspien.assignment.order.domain.Order;

import static org.assertj.core.api.Assertions.assertThat;

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
	void contentIsUtf8BomCsvWithCrlfAndQuotedComma() {
		// 설계서 §5.2 예시
		String expected = "﻿ORDER_ID,USER_ID,NAME,ITEM_ID,ITEM_NAME,PRICE,RECEIVED_AT\r\n"
				+ "A002,USER01,김민수,ITEM10,\"노트북 거치대, 블랙\",32000,2026-10-31 14:05:12\r\n";

		assertThat(new String(RECEIPT.content(StandardCharsets.UTF_8, true), StandardCharsets.UTF_8))
			.isEqualTo(expected);
		assertThat(RECEIPT.content(StandardCharsets.UTF_8, false)).hasSize(expected.getBytes(StandardCharsets.UTF_8).length - 3);
	}

	@Test
	void escapesQuotesAndLineBreaks() {
		assertThat(Receipt.escape("a\"b")).isEqualTo("\"a\"\"b\"");
		assertThat(Receipt.escape("a\nb")).isEqualTo("\"a\nb\"");
		assertThat(Receipt.escape(null)).isEmpty();
	}

	@Test
	void prefixesValuesThatExcelWouldRunAsFormula() {
		assertThat(Receipt.cell("=1+1")).isEqualTo("\"'=1+1\"");
		assertThat(Receipt.cell("+82 10")).isEqualTo("\"'+82 10\"");
		assertThat(Receipt.cell("-상품")).isEqualTo("\"'-상품\"");
		assertThat(Receipt.cell("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
		assertThat(Receipt.cell("\t=1+1")).isEqualTo("\"'\t=1+1\"");
		assertThat(Receipt.cell("\r=1+1")).isEqualTo("\"'\r=1+1\"");
	}

	@Test
	void formulaPrefixAndRfc4180QuotingApplyTogether() {
		assertThat(Receipt.cell("=HYPERLINK(\"http://x\",\"a,b\")"))
			.isEqualTo("\"'=HYPERLINK(\"\"http://x\"\",\"\"a,b\"\")\"");
	}

	@Test
	void ordinaryValuesAreWrittenAsBefore() {
		assertThat(Receipt.cell("김민수")).isEqualTo("김민수");
		assertThat(Receipt.cell("A1=B1")).as("중간의 등호").isEqualTo("A1=B1");
		assertThat(Receipt.cell("노트북 거치대, 블랙")).isEqualTo("\"노트북 거치대, 블랙\"");
		assertThat(Receipt.cell("2026-10-31 14:05:12")).isEqualTo("2026-10-31 14:05:12");
		assertThat(Receipt.cell("")).isEmpty();
		assertThat(Receipt.cell(null)).isEmpty();
	}

	@Test
	void contentNeutralizesFormulaInShopValues() {
		Receipt receipt = new Receipt(
				Order.received("A003", "@USER", "-ITEM", "=1+1", "서울", "-10% 할인, 쿠폰", "1000"),
				OffsetDateTime.parse("2026-10-31T14:05:12+09:00"), "홍길동");

		String row = "A003,\"'@USER\",\"'=1+1\",\"'-ITEM\",\"'-10% 할인, 쿠폰\",1000,2026-10-31 14:05:12\r\n";

		assertThat(new String(receipt.content(StandardCharsets.UTF_8, false), StandardCharsets.UTF_8))
			.endsWith("\r\n" + row);
	}

	@Test
	void sameOrderAlwaysGivesSameBytes() {
		assertThat(RECEIPT.content(StandardCharsets.UTF_8, true)).isEqualTo(RECEIPT.content(StandardCharsets.UTF_8, true));
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
