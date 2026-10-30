package com.inspien.assignment.bootstrap;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 과제 API 대신 로컬 HTTP 서버로 요청 형식과 오류 처리를 검증한다.
 */
class RecruitingApiClientTest {

	private HttpServer server;

	private final AtomicReference<RecordedRequest> recorded = new AtomicReference<>();

	@BeforeEach
	void startServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		this.server.start();
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
	}

	@Test
	void sendsApplicantInfoWithBasicAuthAndReturnsBody() {
		respondWith(200, "{\"APPLICANT_KEY\":\"K1\"}", Duration.ZERO);

		String body = call(new RecruitingApiClient());

		assertThat(body).isEqualTo("{\"APPLICANT_KEY\":\"K1\"}");
		RecordedRequest request = this.recorded.get();
		assertThat(request.method()).isEqualTo("POST");
		assertThat(request.authorization()).isEqualTo("Basic "
				+ Base64.getEncoder().encodeToString("user!b1:pass$=".getBytes(StandardCharsets.UTF_8)));
		assertThat(request.contentType()).startsWith("application/json");
		JsonNode json = JsonMapper.builder().build().readTree(request.body());
		assertThat(json.get("NAME").asString()).isEqualTo("홍길동");
		assertThat(json.get("PHONE_NUMBER").asString()).isEqualTo("010-1234-5678");
		assertThat(json.get("E_MAIL").asString()).isEqualTo("hong@example.com");
	}

	@Test
	void failsOnNon2xxWithStatusCode() {
		respondWith(401, "Unauthorized", Duration.ZERO);

		assertThatThrownBy(() -> call(new RecruitingApiClient()))
			.isInstanceOf(BootstrapException.class)
			.hasMessageContaining("HTTP 401")
			.hasMessageContaining("Unauthorized");
	}

	@Test
	void failsOnTimeoutWithoutRetry() {
		respondWith(200, "{}", Duration.ofSeconds(2));

		assertThatThrownBy(() -> call(new RecruitingApiClient(Duration.ofMillis(200))))
			.isInstanceOf(BootstrapException.class)
			.hasMessageContaining("재시도하지 않습니다");
	}

	private String call(RecruitingApiClient client) {
		URI uri = URI.create("http://localhost:" + this.server.getAddress().getPort() + "/http/RecruitingTest");
		return client.requestConnectionInfo(uri, input());
	}

	private static BootstrapInput input() {
		return new BootstrapInput("https://example.com/http/RecruitingTest", "user!b1", "pass$=", "홍길동",
				"010-1234-5678", "hong@example.com");
	}

	private void respondWith(int status, String body, Duration delay) {
		this.server.createContext("/http/RecruitingTest", (exchange) -> {
			record(exchange);
			sleep(delay);
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, bytes.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
	}

	private void record(HttpExchange exchange) throws IOException {
		this.recorded.set(new RecordedRequest(exchange.getRequestMethod(),
				exchange.getRequestHeaders().getFirst("Authorization"),
				exchange.getRequestHeaders().getFirst("Content-Type"),
				new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
	}

	private static void sleep(Duration delay) {
		try {
			Thread.sleep(delay);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private record RecordedRequest(String method, String authorization, String contentType, String body) {
	}

}
