package com.inspien.assignment.receipt.adapter.out.csv;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.receipt.application.port.ReceiptFormatter;
import com.inspien.assignment.receipt.domain.Receipt;

/**
 * 영수증 CSV 변환기 (설계서 §5.2, D-09). 형식 세부는 모두 여기 있다: 헤더(DB 컬럼명), 수신일시 형식, CRLF, 인코딩과 BOM,
 * RFC 4180 이스케이프, 수식 무력화.
 *
 * <pre>
 * (BOM) 헤더 1줄 + 데이터 1줄, CRLF(마지막 줄 포함)
 * ORDER_ID,USER_ID,NAME,ITEM_ID,ITEM_NAME,PRICE,RECEIVED_AT
 * A002,USER01,김민수,ITEM10,"노트북 거치대, 블랙",32000,2026-10-31 14:05:12
 * </pre>
 *
 * 영수증에 든 값만 쓰므로 같은 영수증이면 항상 같은 바이트가 나온다 (포트의 약속).
 */
@Component
class CsvReceiptFormatter implements ReceiptFormatter {

	static final List<String> COLUMNS = List.of("ORDER_ID", "USER_ID", "NAME", "ITEM_ID", "ITEM_NAME", "PRICE",
			"RECEIVED_AT");

	/** 엑셀이 날짜로 읽는 형식 */
	static final DateTimeFormatter RECEIVED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final String CRLF = "\r\n";

	private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	/** 등호, 더하기, 빼기, at, 탭(0x09), CR(0x0D) */
	private static final String FORMULA_TRIGGERS = "=+-@\t\r";

	private final Charset charset;

	private final boolean bom;

	CsvReceiptFormatter(ReceiptProperties receipt) {
		this.charset = receipt.contentCharset();
		this.bom = receipt.bom();
	}

	/**
	 * 값마다 {@link #cell(String)}로 쓴다. BOM은 설정이 켜져 있고 UTF-8일 때만 붙인다. 없으면 한국어 엑셀이 CP949로 읽어
	 * 한글이 깨진다.
	 */
	@Override
	public byte[] format(Receipt receipt) {
		Order order = receipt.order();
		String header = String.join(",", COLUMNS);
		String row = String.join(",", List.of(cell(order.orderId()), cell(order.userId()), cell(order.name()),
				cell(order.itemId()), cell(order.itemName()), cell(order.price()),
				cell(RECEIVED_AT.format(receipt.receivedAt()))));
		String text = header + CRLF + row + CRLF;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (this.bom && this.charset.equals(StandardCharsets.UTF_8)) {
			out.writeBytes(UTF8_BOM);
		}
		out.writeBytes(text.getBytes(this.charset));
		return out.toByteArray();
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
