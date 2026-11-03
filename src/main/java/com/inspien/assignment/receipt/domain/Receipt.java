package com.inspien.assignment.receipt.domain;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.inspien.assignment.order.domain.Order;

/**
 * 주문 1건의 영수증 CSV (설계서 §5.2, D-09).
 *
 * <pre>
 * 영수증_{지원자명}_{ORDER_ID}.csv       기본
 * 영수증_{지원자명}_{ORDER_ID}_r{N}.csv  앞 파일이 반쪽일 때 재업로드
 * </pre>
 *
 * 파일명에 날짜·시각을 넣지 않는다. 재시도 때 이름이 바뀌면 "이미 올렸나"를 판단할 수 없다. 내용은 ORDER_TB와 수신일시만으로
 * 만들어, 같은 주문이면 항상 같은 바이트가 나오고 예상 크기를 미리 계산할 수 있다.
 *
 * @param order 주문
 * @param receivedAt 메시지를 처음 받은 시각 (한국 시간). 재시도해도 같다
 * @param applicantName 지원자명. 공용 폴더에서 다른 지원자의 파일과 구분한다
 */
public record Receipt(Order order, OffsetDateTime receivedAt, String applicantName) {

	static final List<String> COLUMNS = List.of("ORDER_ID", "USER_ID", "NAME", "ITEM_ID", "ITEM_NAME", "PRICE",
			"RECEIVED_AT");

	/** 엑셀이 날짜로 읽는 형식 */
	static final DateTimeFormatter RECEIVED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final String CRLF = "\r\n";

	private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	public Receipt {
		Objects.requireNonNull(order, "order");
		Objects.requireNonNull(receivedAt, "receivedAt");
		Objects.requireNonNull(applicantName, "applicantName");
	}

	public String fileName(int revision) {
		return baseName() + ((revision == 0) ? "" : "_r" + revision) + ".csv";
	}

	/**
	 * 목록의 파일명이 이 주문의 영수증이면 재시도 번호(기본은 0).
	 */
	public OptionalInt revisionOf(String fileName) {
		Matcher matcher = Pattern.compile("^" + Pattern.quote(baseName()) + "(?:_r(\\d+))?\\.csv$").matcher(fileName);
		if (!matcher.matches()) {
			return OptionalInt.empty();
		}
		return OptionalInt.of((matcher.group(1) != null) ? Integer.parseInt(matcher.group(1)) : 0);
	}

	/**
	 * 헤더 1줄 + 데이터 1줄, CRLF(마지막 줄 포함). 쉼표·따옴표·줄바꿈이 있는 값만 큰따옴표로 감싼다 (RFC 4180).
	 *
	 * @param bom UTF-8일 때 BOM을 붙일지. 없으면 한국어 엑셀이 CP949로 읽어 한글이 깨진다
	 */
	public byte[] content(Charset charset, boolean bom) {
		Order order = this.order;
		String header = String.join(",", COLUMNS);
		String row = String.join(",", List.of(escape(order.orderId()), escape(order.userId()), escape(order.name()),
				escape(order.itemId()), escape(order.itemName()), escape(order.price()),
				RECEIVED_AT.format(this.receivedAt)));
		String text = header + CRLF + row + CRLF;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (bom && charset.equals(StandardCharsets.UTF_8)) {
			out.writeBytes(UTF8_BOM);
		}
		out.writeBytes(text.getBytes(charset));
		return out.toByteArray();
	}

	private String baseName() {
		return "영수증_" + this.applicantName + "_" + this.order.orderId();
	}

	static String escape(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\r") || value.contains("\n")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

}
