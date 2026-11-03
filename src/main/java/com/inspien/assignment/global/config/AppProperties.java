package com.inspien.assignment.global.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DB 접속 설정 (설계서 §9). DataSource 3개를 만드는 global의 DB 설정만 쓴다. 값은 프로필(local, provided)이 채운다.
 *
 * <p>다른 설정은 그 값을 쓰는 모듈에 따로 둔다: 지원자({@link ApplicantProperties}), 스케줄러({@link SchedulerProperties}),
 * 수신·영수증·FTP·재시도는 각 도메인. 비밀번호가 들어 있으므로 toString은 가린다.
 */
@ConfigurationProperties("app")
public record AppProperties(Oracle oracle, Db orderDb, Db shipmentDb, HistoryDb historyDb) {

	public AppProperties {
		PropertyChecks.required("app.oracle", oracle);
		PropertyChecks.required("app.order-db", orderDb);
		orderDb.check("app.order-db");
		PropertyChecks.required("app.shipment-db", shipmentDb);
		shipmentDb.check("app.shipment-db");
		PropertyChecks.required("app.history-db", historyDb);
	}

	/**
	 * Oracle 공통 타임아웃.
	 *
	 * @param connectTimeout 접속 타임아웃
	 * @param readTimeout 응답 읽기 타임아웃
	 * @param poolWait 커넥션 풀에서 연결을 기다리는 상한. 접속 타임아웃보다 길어야 빈 풀에서 새 연결을 맺을 수 있다 (D-19)
	 */
	public record Oracle(Duration connectTimeout, Duration readTimeout, Duration poolWait) {

		public Oracle {
			PropertyChecks.positive("app.oracle.connect-timeout", connectTimeout);
			PropertyChecks.positive("app.oracle.read-timeout", readTimeout);
			PropertyChecks.positive("app.oracle.pool-wait", poolWait);
			if (poolWait.compareTo(connectTimeout) <= 0) {
				throw new IllegalStateException("app.oracle.pool-wait(%s)는 app.oracle.connect-timeout(%s)보다 길어야 합니다 (D-19)"
					.formatted(poolWait, connectTimeout));
			}
		}

	}

	/**
	 * 쇼핑몰·운송 DB가 같은 모양이라 키 접두어는 {@link #check(String)}에 넘긴다.
	 */
	public record Db(String url, String username, String password, int poolSize) {

		void check(String prefix) {
			PropertyChecks.requiredFromProfile(prefix + ".url", this.url);
			PropertyChecks.requiredFromProfile(prefix + ".username", this.username);
			PropertyChecks.requiredFromProfile(prefix + ".password", this.password);
			PropertyChecks.positive(prefix + ".pool-size", this.poolSize);
		}

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

		public HistoryDb {
			PropertyChecks.required("app.history-db.path", path);
		}

	}

}
