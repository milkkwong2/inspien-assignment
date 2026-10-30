package com.inspien.assignment.bootstrap;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/**
 * 과제 API(RecruitingTest) 호출. 지원자 정보를 보내고 접속 정보가 담긴 응답 본문을 그대로 돌려준다.
 *
 * <p>실패해도 자동으로 다시 시도하지 않는다. 타임아웃처럼 응답만 유실된 경우 서버는 이미 처리했을 수 있고,
 * 다시 호출하면 APPLICANT_KEY가 새로 발급될 수 있기 때문이다.
 */
public final class RecruitingApiClient {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

	private static final int MAX_ERROR_BODY_LENGTH = 500;

	private final HttpClient httpClient;

	private final Duration requestTimeout;

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public RecruitingApiClient() {
		this(DEFAULT_REQUEST_TIMEOUT);
	}

	RecruitingApiClient(Duration requestTimeout) {
		this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
		this.requestTimeout = requestTimeout;
	}

	/**
	 * @return 응답 본문 (2xx일 때만)
	 */
	public String requestConnectionInfo(BootstrapInput input) {
		return requestConnectionInfo(URI.create(input.apiUrl()), input);
	}

	/**
	 * 테스트에서 로컬 HTTP 서버로 호출할 수 있도록 주소를 따로 받는다. (입력 검증은 https만 허용)
	 */
	String requestConnectionInfo(URI uri, BootstrapInput input) {
		HttpRequest request = HttpRequest.newBuilder(uri)
			.timeout(this.requestTimeout)
			.header("Authorization", basicAuth(input.username(), input.password()))
			.header("Content-Type", "application/json; charset=UTF-8")
			.header("Accept", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(requestBody(input), StandardCharsets.UTF_8))
			.build();
		HttpResponse<String> response = send(request);
		if (response.statusCode() / 100 != 2) {
			throw new BootstrapException("과제 API 응답 오류: HTTP " + response.statusCode() + "\n  " + truncate(response.body()));
		}
		return response.body();
	}

	String requestBody(BootstrapInput input) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("NAME", input.name());
		body.put("PHONE_NUMBER", input.phoneNumber());
		body.put("E_MAIL", input.email());
		return this.jsonMapper.writeValueAsString(body);
	}

	private HttpResponse<String> send(HttpRequest request) {
		try {
			return this.httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		}
		catch (HttpTimeoutException ex) {
			throw new BootstrapException("과제 API 응답 시간 초과. 서버는 요청을 처리했을 수 있어 자동으로 재시도하지 않습니다."
					+ "\n  다시 호출하기 전에 담당자에게 지원자 키 발급 여부를 확인하세요.", ex);
		}
		catch (IOException ex) {
			throw new BootstrapException("과제 API 호출 실패: " + ex.getMessage() + " (자동으로 재시도하지 않습니다)", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new BootstrapException("과제 API 호출이 중단되었습니다", ex);
		}
	}

	private static String basicAuth(String username, String password) {
		String credentials = username + ":" + password;
		return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
	}

	private static String truncate(String body) {
		if (body == null) {
			return "";
		}
		return (body.length() <= MAX_ERROR_BODY_LENGTH) ? body : body.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
	}

}
