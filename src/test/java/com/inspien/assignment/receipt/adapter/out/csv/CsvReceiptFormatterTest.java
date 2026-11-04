package com.inspien.assignment.receipt.adapter.out.csv;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.receipt.domain.Receipt;

import static org.assertj.core.api.Assertions.assertThat;

class CsvReceiptFormatterTest {

	static final Receipt RECEIPT = new Receipt(
			Order.received("A002", "USER01", "ITEM10", "김민수", "서울", "노트북 거치대, 블랙", "32000"),
			OffsetDateTime.parse("2026-10-31T14:05:12+09:00"), "홍길동");

	/** 기본 설정 (application.yml): UTF-8 + BOM */
	final CsvReceiptFormatter formatter = new CsvReceiptFormatter(new ReceiptProperties(StandardCharsets.UTF_8, true));

	final CsvReceiptFormatter withoutBom = new CsvReceiptFormatter(
			new ReceiptProperties(StandardCharsets.UTF_8, false));

	@Test
	void contentIsUtf8BomCsvWithCrlfAndQuotedComma() {
		// 설계서 §5.2 예시
		String expected = "﻿ORDER_ID,USER_ID,NAME,ITEM_ID,ITEM_NAME,PRICE,RECEIVED_AT\r\n"
				+ "A002,USER01,김민수,ITEM10,\"노트북 거치대, 블랙\",32000,2026-10-31 14:05:12\r\n";

		assertThat(new String(this.formatter.format(RECEIPT), StandardCharsets.UTF_8)).isEqualTo(expected);
		assertThat(this.withoutBom.format(RECEIPT)).hasSize(expected.getBytes(StandardCharsets.UTF_8).length - 3);
	}

	@Test
	void bomIsOnlyForUtf8() {
		Charset cp949 = Charset.forName("x-windows-949");
		CsvReceiptFormatter formatter = new CsvReceiptFormatter(new ReceiptProperties(cp949, true));

		byte[] content = formatter.format(RECEIPT);

		assertThat(new String(content, cp949)).startsWith("ORDER_ID,").contains("김민수");
	}

	@Test
	void escapesQuotesAndLineBreaks() {
		assertThat(CsvReceiptFormatter.escape("a\"b")).isEqualTo("\"a\"\"b\"");
		assertThat(CsvReceiptFormatter.escape("a\nb")).isEqualTo("\"a\nb\"");
		assertThat(CsvReceiptFormatter.escape(null)).isEmpty();
	}

	@Test
	void prefixesValuesThatExcelWouldRunAsFormula() {
		assertThat(CsvReceiptFormatter.cell("=1+1")).isEqualTo("\"'=1+1\"");
		assertThat(CsvReceiptFormatter.cell("+82 10")).isEqualTo("\"'+82 10\"");
		assertThat(CsvReceiptFormatter.cell("-상품")).isEqualTo("\"'-상품\"");
		assertThat(CsvReceiptFormatter.cell("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
		assertThat(CsvReceiptFormatter.cell("\t=1+1")).isEqualTo("\"'\t=1+1\"");
		assertThat(CsvReceiptFormatter.cell("\r=1+1")).isEqualTo("\"'\r=1+1\"");
	}

	@Test
	void formulaPrefixAndRfc4180QuotingApplyTogether() {
		assertThat(CsvReceiptFormatter.cell("=HYPERLINK(\"http://x\",\"a,b\")"))
			.isEqualTo("\"'=HYPERLINK(\"\"http://x\"\",\"\"a,b\"\")\"");
	}

	@Test
	void ordinaryValuesAreWrittenAsBefore() {
		assertThat(CsvReceiptFormatter.cell("김민수")).isEqualTo("김민수");
		assertThat(CsvReceiptFormatter.cell("A1=B1")).as("중간의 등호").isEqualTo("A1=B1");
		assertThat(CsvReceiptFormatter.cell("노트북 거치대, 블랙")).isEqualTo("\"노트북 거치대, 블랙\"");
		assertThat(CsvReceiptFormatter.cell("2026-10-31 14:05:12")).isEqualTo("2026-10-31 14:05:12");
		assertThat(CsvReceiptFormatter.cell("")).isEmpty();
		assertThat(CsvReceiptFormatter.cell(null)).isEmpty();
	}

	@Test
	void contentNeutralizesFormulaInShopValues() {
		Receipt receipt = new Receipt(
				Order.received("A003", "@USER", "-ITEM", "=1+1", "서울", "-10% 할인, 쿠폰", "1000"),
				OffsetDateTime.parse("2026-10-31T14:05:12+09:00"), "홍길동");

		String row = "A003,\"'@USER\",\"'=1+1\",\"'-ITEM\",\"'-10% 할인, 쿠폰\",1000,2026-10-31 14:05:12\r\n";

		assertThat(new String(this.withoutBom.format(receipt), StandardCharsets.UTF_8)).endsWith("\r\n" + row);
	}

	@Test
	void sameOrderAlwaysGivesSameBytes() {
		assertThat(this.formatter.format(RECEIPT)).isEqualTo(this.formatter.format(RECEIPT));
	}

	/**
	 * 변환기를 도메인(Receipt.content)에서 어댑터로 옮기기 전의 구현이 기본 설정으로 낸 바이트다. 바이트가 하나라도 달라지면
	 * 이미 올린 파일과 예상 크기가 어긋나 완료 기록 전에 멈춘 주문이 _r1로 한 번 더 올라간다 (D-09).
	 */
	@Test
	void bytesAreSameAsBeforeMovingOutOfTheDomain() {
		Receipt tricky = new Receipt(
				Order.received("A003", "@USER", "-ITEM", "=1+1", "서울", "\"특가\" 거치대,\r\n2단", "1000"),
				OffsetDateTime.parse("2026-10-31T09:00:00+09:00"), "홍길동");

		assertThat(HexFormat.of().formatHex(this.formatter.format(RECEIPT))).isEqualTo(
				"efbbbf4f524445525f49442c555345525f49442c4e414d452c4954454d5f49442c4954454d5f4e414d452c50524943452c"
						+ "52454345495645445f41540d0a413030322c5553455230312ceab980ebafbcec88982c4954454d31302c22eb85b8"
						+ "ed8ab8ebb68120eab1b0ecb998eb8c802c20ebb894eb9e99222c33323030302c323032362d31302d33312031343a"
						+ "30353a31320d0a");
		assertThat(HexFormat.of().formatHex(this.formatter.format(tricky))).isEqualTo(
				"efbbbf4f524445525f49442c555345525f49442c4e414d452c4954454d5f49442c4954454d5f4e414d452c50524943452c"
						+ "52454345495645445f41540d0a413030332c22274055534552222c22273d312b31222c22272d4954454d222c2222"
						+ "22ed8ab9eab080222220eab1b0ecb998eb8c802c0d0a32eb8ba8222c313030302c323032362d31302d33312030"
						+ "393a30303a30300d0a");
	}

}
