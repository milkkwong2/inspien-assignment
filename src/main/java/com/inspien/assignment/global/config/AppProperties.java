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
		Ftp ftp, Inbound inbound, Scheduler scheduler, Retry retry, Receipt receipt) {

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

	/**
	 * IF-01 수신 설정 (설계서 §9).
	 *
	 * @param defaultCharset 본문에 인코딩 표시가 없을 때 (D-20)
	 * @param duplicateWindow 완료된 메시지와 같은 본문을 같은 메시지로 보는 시간 (D-24)
	 * @param lockStripes 수신 잠금 수 (D-16)
	 * @param lockWait 수신 잠금 대기 상한 (D-16)
	 * @param dbCharset ORDER_TB 값 길이를 바이트로 셀 때의 문자셋 (D-07, 설계서 §11)
	 * @param maxValueBytes ORDER_TB 컬럼 최대 바이트 수 (VARCHAR2(100))
	 */
	public record Inbound(String defaultCharset, Duration duplicateWindow, int lockStripes, Duration lockWait,
			String dbCharset, int maxValueBytes) {
	}

	/**
	 * FTP (회계팀 영수증). 타임아웃은 상대가 응답 없이 멈췄을 때 실패로 바꾸려고 둔다 (D-12, D-19).
	 *
	 * @param filenameEncoding 제어 연결 인코딩(한글 파일명). 제공 환경 확인 후 정한다 (설계서 §11)
	 */
	public record Ftp(String host, int port, String username, String password, String path, Duration connectTimeout,
			Duration commandTimeout, Duration dataTimeout, String filenameEncoding) {

		@Override
		public String toString() {
			return "Ftp[host=" + this.host + ", port=" + this.port + ", username=" + this.username
					+ ", password=****, path=" + this.path + "]";
		}

	}

	/**
	 * 영수증·배송 스케줄러 (D-12).
	 *
	 * @param enabled 스케줄러 실행 여부 (테스트에서 끈다)
	 * @param fixedDelay 주기. 가장 짧은 재시도 간격보다 짧아야 한다
	 * @param batchSize 한 주기 최대 처리 건수
	 */
	public record Scheduler(boolean enabled, Duration fixedDelay, int batchSize) {
	}

	/**
	 * 건별 실패 재시도 (D-10). 대기는 initialBackoff × multiplier^(n-1): 1·2·4·8분.
	 *
	 * @param maxAttempts 첫 시도 포함 최대 시도 횟수
	 */
	public record Retry(int maxAttempts, Duration initialBackoff, int multiplier) {
	}

	/**
	 * 영수증 CSV (D-09).
	 *
	 * @param contentCharset 내용 인코딩
	 * @param bom BOM을 붙일지. 없으면 한국어 엑셀이 CP949로 읽어 한글이 깨진다
	 */
	public record Receipt(String contentCharset, boolean bom) {
	}

}
