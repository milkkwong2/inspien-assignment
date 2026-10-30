package com.inspien.assignment.bootstrap;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * 과제 API 호출에 필요한 입력값. Git에서 제외된 config/bootstrap.properties에서 읽는다.
 *
 * @param apiUrl 과제 API 호출 주소
 * @param username 과제 API Basic 인증 username
 * @param password 과제 API Basic 인증 password
 * @param name 과제수행자 이름
 * @param phoneNumber 과제수행자 휴대폰 번호 ("010-1234-5678" 형식). 복호화 키로도 쓰인다
 * @param email 과제수행자 이메일
 */
public record BootstrapInput(String apiUrl, String username, String password, String name, String phoneNumber,
		String email) {

	static final String API_URL = "recruit.api.url";

	static final String USERNAME = "recruit.api.username";

	static final String PASSWORD = "recruit.api.password";

	static final String NAME = "applicant.name";

	static final String PHONE_NUMBER = "applicant.phone";

	static final String EMAIL = "applicant.email";

	private static final Pattern PHONE_PATTERN = Pattern.compile("^010-\\d{4}-\\d{4}$");

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	public BootstrapInput {
		List<String> errors = new ArrayList<>();
		requireText(errors, API_URL, apiUrl);
		requireText(errors, USERNAME, username);
		requireText(errors, PASSWORD, password);
		requireText(errors, NAME, name);
		requireText(errors, PHONE_NUMBER, phoneNumber);
		requireText(errors, EMAIL, email);
		if (isText(apiUrl) && !apiUrl.startsWith("https://")) {
			errors.add(API_URL + ": https:// 로 시작해야 합니다");
		}
		if (isText(phoneNumber) && !PHONE_PATTERN.matcher(phoneNumber).matches()) {
			errors.add(PHONE_NUMBER + ": \"010-1234-5678\" 형식이어야 합니다");
		}
		if (isText(email) && !EMAIL_PATTERN.matcher(email).matches()) {
			errors.add(EMAIL + ": 이메일 형식이 아닙니다");
		}
		if (!errors.isEmpty()) {
			throw new BootstrapException("입력값 오류\n  - " + String.join("\n  - ", errors));
		}
	}

	/**
	 * 한글 이름을 읽을 수 있도록 UTF-8로 읽는다. ({@link Properties#load(java.io.InputStream)}는 ISO-8859-1)
	 */
	public static BootstrapInput load(Path file) {
		if (!Files.isRegularFile(file)) {
			throw new BootstrapException("입력 파일이 없습니다: " + file + " (bootstrap.properties.example을 복사해 작성하세요)");
		}
		Properties props = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			props.load(reader);
		}
		catch (IOException ex) {
			throw new BootstrapException("입력 파일을 읽지 못했습니다: " + file, ex);
		}
		return new BootstrapInput(get(props, API_URL), get(props, USERNAME), get(props, PASSWORD), get(props, NAME),
				get(props, PHONE_NUMBER), get(props, EMAIL));
	}

	private static String get(Properties props, String key) {
		String value = props.getProperty(key);
		return (value != null) ? value.strip() : null;
	}

	private static void requireText(List<String> errors, String key, String value) {
		if (!isText(value)) {
			errors.add(key + ": 값이 비어 있습니다");
		}
	}

	private static boolean isText(String value) {
		return value != null && !value.isBlank();
	}

	/**
	 * 레코드 기본 toString은 모든 값을 출력하므로, 로그에 남지 않도록 비밀번호와 개인정보를 가린다.
	 */
	@Override
	public String toString() {
		return "BootstrapInput[apiUrl=" + this.apiUrl + ", username=****, password=****, name=" + this.name
				+ ", phoneNumber=****, email=****]";
	}

}
