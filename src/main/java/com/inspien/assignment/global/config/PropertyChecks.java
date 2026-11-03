package com.inspien.assignment.global.config;

import java.time.Duration;

import org.springframework.util.unit.DataSize;

/**
 * 설정 레코드가 생성자에서 쓰는 검사. 잘못된 값은 기동할 때 YAML 키 이름을 담아 실패시킨다.
 *
 * <p>잘못된 값으로 뜨면 문제가 엉뚱한 곳에서 늦게 드러난다. 잠금 수 0은 첫 수신에서 0으로 나누고, 주기당 건수 0은 아무것도
 * 처리하지 않으면서 오류도 없다.
 */
public final class PropertyChecks {

	static final String PROFILE_HINT = "프로필(local 또는 provided)을 지정했는지, provided라면 config/ 파일이 있는지 확인하세요.";

	private PropertyChecks() {
	}

	/**
	 * application.yml에 기본값이 있는 값. 비어 있으면 그 기본값을 지운 것이다.
	 */
	public static void required(String key, Object value) {
		if (value == null) {
			throw new IllegalStateException(key + " 값이 필요합니다.");
		}
	}

	/**
	 * 프로필이 채우는 접속 정보. 비어 있으면 대개 프로필을 지정하지 않은 것이다 (D-11, D-23).
	 */
	public static void requiredFromProfile(String key, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(key + " 값이 필요합니다. " + PROFILE_HINT);
		}
	}

	public static void positive(String key, int value) {
		if (value < 1) {
			throw new IllegalStateException(key + " 값은 1 이상이어야 합니다: " + value);
		}
	}

	public static void positive(String key, Duration value) {
		required(key, value);
		if (!value.isPositive()) {
			throw new IllegalStateException(key + " 값은 0보다 길어야 합니다: " + value);
		}
	}

	public static void positive(String key, DataSize value) {
		required(key, value);
		if (value.toBytes() <= 0) {
			throw new IllegalStateException(key + " 값은 0보다 커야 합니다: " + value);
		}
	}

}
