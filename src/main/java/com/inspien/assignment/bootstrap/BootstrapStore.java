package com.inspien.assignment.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 부트스트랩 결과 파일 관리. 모든 파일은 Git에서 제외된 config/ 디렉토리에 둔다.
 *
 * <p>파일은 임시 파일에 먼저 쓴 뒤 이름을 바꿔서, 중간에 실패해도 반쯤 쓰인 파일이 남지 않게 한다.
 */
public final class BootstrapStore {

	static final String RAW_RESPONSE = "raw-response.json";

	static final String CONNECTION = "connection.json";

	static final String SAMPLE_ORDER = "sample-order.xml";

	static final String ERROR_RESPONSE = "raw-error-response.txt";

	private final Path dir;

	public BootstrapStore(Path dir) {
		this.dir = dir;
	}

	public Path rawResponsePath() {
		return this.dir.resolve(RAW_RESPONSE);
	}

	public Path connectionPath() {
		return this.dir.resolve(CONNECTION);
	}

	public Path sampleOrderPath() {
		return this.dir.resolve(SAMPLE_ORDER);
	}

	public Path errorResponsePath() {
		return this.dir.resolve(ERROR_RESPONSE);
	}

	/**
	 * 과제 API를 다시 호출하면 APPLICANT_KEY가 새로 발급될 수 있으므로, 원본 응답이 이미 있으면 호출을 막는다.
	 */
	public void ensureNoRawResponse() {
		if (Files.exists(rawResponsePath())) {
			throw new BootstrapException("이미 받은 응답이 있습니다: " + rawResponsePath()
					+ "\n  과제 API를 다시 호출하지 않습니다. 복호화만 다시 하려면 --decrypt-only 로 실행하세요.");
		}
	}

	/**
	 * 원본 응답은 한 번만 저장한다. 이미 있으면 덮어쓰지 않고 실패한다.
	 */
	public void saveRawResponse(String body) {
		ensureNoRawResponse();
		write(rawResponsePath(), body.getBytes(StandardCharsets.UTF_8), false);
	}

	public String readRawResponse() {
		Path path = rawResponsePath();
		if (!Files.isRegularFile(path)) {
			throw new BootstrapException("저장된 응답이 없습니다: " + path + " (먼저 --decrypt-only 없이 실행하세요)");
		}
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new BootstrapException("저장된 응답을 읽지 못했습니다: " + path, ex);
		}
	}

	/**
	 * 복호화 결과는 원본 응답에서 언제든 다시 만들 수 있으므로 덮어쓴다.
	 */
	public void saveConnection(String json) {
		write(connectionPath(), json.getBytes(StandardCharsets.UTF_8), true);
	}

	public void saveSampleOrder(byte[] eucKrXml) {
		write(sampleOrderPath(), eucKrXml, true);
	}

	/**
	 * 오류 응답 본문. 원본 응답과 달리 실패는 반복될 수 있으므로 마지막 본문으로 덮어쓴다.
	 */
	public void saveErrorResponse(String body) {
		write(errorResponsePath(), body.getBytes(StandardCharsets.UTF_8), true);
	}

	private void write(Path target, byte[] content, boolean overwrite) {
		Path tmp = null;
		try {
			Files.createDirectories(this.dir);
			tmp = Files.createTempFile(this.dir, target.getFileName().toString(), ".tmp");
			Files.write(tmp, content);
			move(tmp, target, overwrite);
		}
		catch (IOException ex) {
			throw new BootstrapException("파일 저장 실패: " + target, ex);
		}
		finally {
			deleteQuietly(tmp);
		}
	}

	private static void move(Path source, Path target, boolean overwrite) throws IOException {
		if (!overwrite && Files.exists(target)) {
			throw new BootstrapException("이미 파일이 있어 덮어쓰지 않습니다: " + target);
		}
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException ex) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void deleteQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException ex) {
			// 임시 파일 정리 실패는 결과에 영향이 없으므로 무시한다
		}
	}

}
