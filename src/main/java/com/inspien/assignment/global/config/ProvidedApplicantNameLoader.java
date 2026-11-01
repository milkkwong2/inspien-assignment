package com.inspien.assignment.global.config;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * provided 프로필에서 지원자명을 config/bootstrap.properties에서 UTF-8로 읽는다 (D-23).
 *
 * <p>spring.config.import로 .properties를 읽으면 ISO-8859-1로 해석되어 한글 이름이 깨진다. 깨진 이름이 영수증 파일명으로
 * FTP에 올라가면 지울 수 없으므로, 이 파일만 직접 읽는다. 전화번호·API 인증 정보 같은 나머지 값은 환경에 올리지 않는다.
 */
public class ProvidedApplicantNameLoader implements EnvironmentPostProcessor, Ordered {

	static final String PROFILE = "provided";

	static final String CONFIG_DIR = "app.provided-config-dir";

	static final String SOURCE_KEY = "applicant.name";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		if (!environment.matchesProfiles(PROFILE)) {
			return;
		}
		Path file = Path.of(environment.getProperty(CONFIG_DIR, "./config"), "bootstrap.properties");
		if (!Files.isRegularFile(file)) {
			throw new IllegalStateException("지원자명 파일이 없습니다: " + file.toAbsolutePath());
		}
		String name = read(file).getProperty(SOURCE_KEY);
		if (name != null) {
			environment.getPropertySources()
				.addLast(new MapPropertySource("providedApplicantName", Map.of("app.applicant.name", name.strip())));
		}
	}

	private static Properties read(Path file) {
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("지원자명 파일을 읽지 못했습니다: " + file, ex);
		}
		return properties;
	}

	/**
	 * 프로필이 정해진 뒤(ConfigData 처리 후)에 실행한다.
	 */
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

}
