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

	/** 등호, 더하기, 빼기, at, 탭(0x09), CR(0x0D) */
	private static final String FORMULA_TRIGGERS = "=+-@\t\r";

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
	 * 헤더 1줄 + 데이터 1줄, CRLF(마지막 줄 포함). 값마다 {@link #cell(String)}로 쓴다.
	 *
	 * @param bom UTF-8일 때 BOM을 붙일지. 없으면 한국어 엑셀이 CP949로 읽어 한글이 깨진다
	 */
	public byte[] content(Charset charset, boolean bom) {
		Order order = this.order;
		String header = String.join(",", COLUMNS);
		String row = String.join(",", List.of(cell(order.orderId()), cell(order.userId()), cell(order.name()),
				cell(order.itemId()), cell(order.itemName()), cell(order.price()),
				cell(RECEIVED_AT.format(this.receivedAt))));
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

	/**
	 * 영수증 한 칸. 두 규칙을 함께 적용한다 (설계서 §5.2, D-09).
	 * <ul>
	 * <li>수식 무력화 (OWASP CSV Injection): 값이 {@code = + - @}, 탭, CR로 시작하면 앞에 작은따옴표({@code '})를 붙이고
	 * 큰따옴표로 감싼다. 영수증은 회계 담당자가 엑셀로 연다(BOM도 그래서 붙였다). 쇼핑몰이 보낸 값이 그대로 들어가므로
	 * {@code =HYPERLINK(...)} 같은 값이 수식으로 실행되면 안 된다. 거부하지 않고 접두어를 붙이는 것은
	 * "-10% 할인 쿠폰" 같은 정상 값의 주문을 막지 않기 위해서다. 대가로 그런 드문 값은 파일에 작은따옴표가 남는다.</li>
	 * <li>RFC 4180: 그 밖의 값은 {@link #escape(String)}를 따른다.</li>
	 * </ul>
	 * 우리가 만드는 값(ORDER_ID, PRICE, 수신일시)은 이 문자로 시작할 수 없지만, 칸마다 규칙이 갈리지 않게 모든 칸에 쓴다.
	 */
	static String cell(String value) {
		if (startsLikeFormula(value)) {
			return "\"'" + value.replace("\"", "\"\"") + "\"";
		}
		return escape(value);
	}

	/** 엑셀이 수식으로 읽기 시작하는 첫 글자 (OWASP CSV Injection) */
	private static boolean startsLikeFormula(String value) {
		if (value == null || value.isEmpty()) {
			return false;
		}
		return FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0;
	}

	/**
	 * RFC 4180: 쉼표·큰따옴표·CR·LF가 있는 값만 큰따옴표로 감싸고, 안의 큰따옴표는 두 번 쓴다.
	 */
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
