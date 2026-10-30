package com.inspien.assignment.bootstrap;

import java.nio.file.Path;

/**
 * 과제 API에서 DB/FTP 접속 정보를 받아오는 일회성 도구의 진입점.
 *
 * <p>Spring 애플리케이션({@code InspienAssignmentApplication})과 진입점을 분리했다. 한 번만 실행하는 준비 작업이라
 * Spring 컨텍스트가 필요 없고, 접속 정보가 없는 상태에서 웹 서버나 DB 연결이 먼저 뜨는 일을 막는다.
 * 이 패키지의 클래스에는 Spring 어노테이션을 붙이지 않으므로 본 애플리케이션 실행 시 로딩되지 않는다.
 *
 * <pre>
 * ./gradlew bootstrap                            과제 API 호출 (최초 1회)
 * ./gradlew bootstrap --args="--decrypt-only"    저장된 응답으로 복호화만 다시
 * </pre>
 */
public final class BootstrapMain {

	static final Path CONFIG_DIR = Path.of("config");

	static final String INPUT_FILE = "bootstrap.properties";

	static final String DECRYPT_ONLY = "--decrypt-only";

	private BootstrapMain() {
	}

	public static void main(String[] args) {
		try {
			BootstrapProcess.Mode mode = parseMode(args);
			BootstrapInput input = BootstrapInput.load(CONFIG_DIR.resolve(INPUT_FILE));
			RecruitingApiClient client = new RecruitingApiClient();
			new BootstrapProcess(input, new BootstrapStore(CONFIG_DIR), client::requestConnectionInfo, System.out)
				.run(mode);
			System.out.println("완료");
		}
		catch (BootstrapException ex) {
			System.err.println("[실패] " + ex.getMessage());
			System.exit(1);
		}
	}

	static BootstrapProcess.Mode parseMode(String[] args) {
		if (args.length == 0) {
			return BootstrapProcess.Mode.FETCH;
		}
		if (args.length == 1 && DECRYPT_ONLY.equals(args[0])) {
			return BootstrapProcess.Mode.DECRYPT_ONLY;
		}
		throw new BootstrapException("알 수 없는 인자: " + String.join(" ", args) + " (사용 가능: " + DECRYPT_ONLY + ")");
	}

}
