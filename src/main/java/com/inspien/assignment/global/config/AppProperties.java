package com.inspien.assignment.global.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 애플리케이션 설정 (설계서 §9). 값은 프로필(local, provided)이 채운다.
 *
 * <p>비밀번호가 들어 있으므로 레코드의 toString은 비밀번호를 가린다.
 */
@ConfigurationProperties("app")
public record AppProperties(Applicant applicant, Oracle oracle, Db orderDb, Db shipmentDb, HistoryDb historyDb,
		Ftp ftp) {

	public AppProperties {
		if (applicant == null) {
			throw new IllegalStateException(Applicant.MISSING);
		}
	}

	/**
	 * 지원자 키와 이름이 없으면 기동하지 않는다. 키가 없으면 조건 없는 쿼리로 다른 지원자의 행을 건드리고,
	 * 이름이 없으면 이름 없는 영수증 파일이 올라간다. 둘 다 지울 수 없다 (D-23).
	 *
	 * @param key 공용 테이블에서 내 행을 가리키는 APPLICANT_KEY
	 * @param name 영수증 파일명에 넣는 지원자명
	 */
	public record Applicant(String key, String name) {

		static final String MISSING = "지원자 키(app.applicant.key)와 이름(app.applicant.name)이 필요합니다. "
				+ "프로필(local 또는 provided)을 지정했는지, provided라면 config/ 파일이 있는지 확인하세요.";

		public Applicant {
			if (key == null || key.isBlank() || name == null || name.isBlank()) {
				throw new IllegalStateException(MISSING);
			}
		}

	}

	/**
	 * Oracle 공통 타임아웃.
	 *
	 * @param connectTimeout 접속 타임아웃
	 * @param readTimeout 응답 읽기 타임아웃
	 * @param poolWait 커넥션 풀에서 연결을 기다리는 상한
	 */
	public record Oracle(Duration connectTimeout, Duration readTimeout, Duration poolWait) {
	}

	public record Db(String url, String username, String password, int poolSize) {

		@Override
		public String toString() {
			return "Db[url=" + this.url + ", username=" + this.username + ", password=****, poolSize=" + this.poolSize
					+ "]";
		}

	}

	/**
	 * @param path H2 파일 경로 (확장자 제외)
	 */
	public record HistoryDb(String path) {
	}

	public record Ftp(String host, int port, String username, String password, String path) {

		@Override
		public String toString() {
			return "Ftp[host=" + this.host + ", port=" + this.port + ", username=" + this.username
					+ ", password=****, path=" + this.path + "]";
		}

	}

}
