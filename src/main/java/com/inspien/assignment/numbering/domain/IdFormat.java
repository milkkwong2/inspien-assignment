package com.inspien.assignment.numbering.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 채번 형식: 정수 시퀀스 ↔ "대문자 1자 + 숫자 3자리" (설계서 §4.1, D-08).
 *
 * <pre>
 * 1 → A001, 999 → A999, 1000 → B001, …, 25974 → Z999, 25975 → 소진
 * </pre>
 *
 * A000은 없다. 고정 길이라 문자열 정렬이 곧 번호 순서다.
 */
public final class IdFormat {

	/** 발급할 수 있는 마지막 시퀀스 값 (Z999) */
	public static final long MAX = 26L * 999;

	private static final Pattern PATTERN = Pattern.compile("^[A-Z]\\d{3}$");

	private IdFormat() {
	}

	public static String format(long sequence) {
		if (sequence < 1) {
			throw new IllegalArgumentException("시퀀스는 1부터: " + sequence);
		}
		if (sequence > MAX) {
			throw new IdExhaustedException(sequence);
		}
		char letter = (char) ('A' + (sequence - 1) / 999);
		long number = (sequence - 1) % 999 + 1;
		return letter + "%03d".formatted(number);
	}

	/**
	 * 번호를 시퀀스 값으로 되돌린다. 형식에 맞지 않거나 A000 같은 없는 번호면 비어 있다.
	 */
	public static Optional<Long> parse(String id) {
		if (id == null || !PATTERN.matcher(id).matches()) {
			return Optional.empty();
		}
		int number = Integer.parseInt(id.substring(1));
		if (number == 0) {
			return Optional.empty();
		}
		return Optional.of((id.charAt(0) - 'A') * 999L + number);
	}

}
