package com.inspien.assignment.receipt.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.inspien.assignment.order.domain.Order;

/**
 * 주문 1건의 영수증 (설계서 §5.2, D-09). 업무 규칙인 파일명과 재시도 번호를 맡는다.
 *
 * <pre>
 * 영수증_{지원자명}_{ORDER_ID}.csv       기본
 * 영수증_{지원자명}_{ORDER_ID}_r{N}.csv  앞 파일이 반쪽일 때 재업로드
 * </pre>
 *
 * 파일명에 날짜·시각을 넣지 않는다. 재시도 때 이름이 바뀌면 "이미 올렸나"를 판단할 수 없다. 영수증은 ORDER_TB 값과
 * 수신일시만 담아, 같은 주문이면 변환기가 항상 같은 바이트를 내고 예상 크기를 미리 계산할 수 있다. 파일 내용(CSV)은
 * 포트 ReceiptFormatter의 구현(어댑터)이 만든다 (D-21).
 *
 * @param order 주문
 * @param receivedAt 메시지를 처음 받은 시각 (한국 시간). 재시도해도 같다
 * @param applicantName 지원자명. 공용 폴더에서 다른 지원자의 파일과 구분한다
 */
public record Receipt(Order order, OffsetDateTime receivedAt, String applicantName) {

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

	private String baseName() {
		return "영수증_" + this.applicantName + "_" + this.order.orderId();
	}

}
