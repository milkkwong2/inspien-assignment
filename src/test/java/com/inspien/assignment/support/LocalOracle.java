package com.inspien.assignment.support;

import java.time.Duration;

import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * 통합 테스트용 Oracle. 로컬 개발 환경(compose.yaml)과 같은 이미지·같은 초기화 스크립트를 쓴다 (D-14).
 *
 * <p>앱 계정은 SELECT·INSERT·UPDATE만 가지고, 다른 지원자의 행이 미리 들어 있다. 기동이 느리므로 테스트 전체에서 한 번만
 * 띄운다.
 */
public final class LocalOracle {

	public static final String APP_USER = "applicant";

	public static final String APP_PASSWORD = "applicant_local";

	private static final OracleContainer CONTAINER = new OracleContainer(
			DockerImageName.parse("gvenzl/oracle-free:23.26.3-slim-faststart"))
		.withCopyFileToContainer(MountableFile.forHostPath("local/oracle/init"), "/container-entrypoint-initdb.d/")
		.withEnv("NLS_LANG", "AMERICAN_AMERICA.AL32UTF8")
		.withStartupTimeout(Duration.ofMinutes(5));

	private LocalOracle() {
	}

	public static synchronized String jdbcUrl() {
		if (!CONTAINER.isRunning()) {
			CONTAINER.start();
		}
		return CONTAINER.getJdbcUrl();
	}

	/**
	 * 컨테이너를 일시 정지해 응답 없는 DB를 흉내 낸다. 반드시 {@link #unpause()}로 되돌린다.
	 */
	public static void pause() {
		CONTAINER.getDockerClient().pauseContainerCmd(CONTAINER.getContainerId()).exec();
	}

	public static synchronized void unpause() {
		Boolean paused = CONTAINER.getDockerClient()
			.inspectContainerCmd(CONTAINER.getContainerId())
			.exec()
			.getState()
			.getPaused();
		if (Boolean.TRUE.equals(paused)) {
			CONTAINER.getDockerClient().unpauseContainerCmd(CONTAINER.getContainerId()).exec();
		}
	}

}
