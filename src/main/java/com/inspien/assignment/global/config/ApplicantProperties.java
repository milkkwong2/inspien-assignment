package com.inspien.assignment.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지원자 키와 이름 (설계서 §9). 공용 Oracle 설정(키)과 영수증(이름)이 쓴다.
 *
 * <p>키와 이름이 없으면 기동하지 않는다. 키가 없으면 조건 없는 쿼리로 다른 지원자의 행을 건드리고, 이름이 없으면 이름 없는
 * 영수증 파일이 올라간다. 둘 다 지울 수 없다 (D-23).
 *
 * @param key 공용 테이블에서 내 행을 가리키는 APPLICANT_KEY
 * @param name 영수증 파일명에 넣는 지원자명
 */
@ConfigurationProperties("app.applicant")
public record ApplicantProperties(String key, String name) {

	static final String MISSING = "지원자 키(app.applicant.key)와 이름(app.applicant.name)이 필요합니다. "
			+ PropertyChecks.PROFILE_HINT;

	public ApplicantProperties {
		if (key == null || key.isBlank() || name == null || name.isBlank()) {
			throw new IllegalStateException(MISSING);
		}
	}

	/**
	 * 키는 앞 4자만 보이고, 실명은 개인정보이므로 (D-23) 모두 가린다.
	 */
	@Override
	public String toString() {
		return "ApplicantProperties[key=" + mask(this.key) + ", name=****]";
	}

	/**
	 * 앞 4자만 남긴다. 부트스트랩 출력(bootstrap 패키지의 BootstrapProcess.mask)과 같은 규칙이다.
	 * 앱과 부트스트랩은 서로 의존하지 않으므로 (ArchitectureTest) 공유하지 않고 따로 둔다.
	 */
	private static String mask(String value) {
		if (value.length() <= 4) {
			return "****";
		}
		return value.substring(0, 4) + "*".repeat(value.length() - 4);
	}

}
