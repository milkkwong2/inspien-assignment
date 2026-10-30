package com.inspien.assignment.bootstrap;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.inspien.assignment.bootstrap.ResponseProcessorTest.encrypt;
import static com.inspien.assignment.bootstrap.ResponseProcessorTest.quoted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BootstrapProcessTest {

	private static final String DB_PASSWORD = "db-secret-password";

	private static final String RESPONSE = ResponseProcessorTest.response(
			quoted(encrypt("{\"HOST\":\"db1\",\"PASSWORD\":\"" + DB_PASSWORD + "\"}")), quoted(encrypt("{\"HOST\":\"db2\"}")),
			quoted(encrypt("{\"HOST\":\"ftp\"}")));

	@TempDir
	Path dir;

	private final AtomicInteger apiCalls = new AtomicInteger();

	private final ByteArrayOutputStream console = new ByteArrayOutputStream();

	@Test
	void fetchCallsApiOnceAndSavesAllFiles() throws Exception {
		BootstrapStore store = new BootstrapStore(this.dir);

		process(store, (input) -> RESPONSE).run(BootstrapProcess.Mode.FETCH);

		assertThat(this.apiCalls).hasValue(1);
		assertThat(Files.readString(store.rawResponsePath())).isEqualTo(RESPONSE);
		assertThat(Files.readString(store.connectionPath())).contains("db1", DB_PASSWORD);
		assertThat(Files.readAllBytes(store.sampleOrderPath()))
			.isEqualTo(ResponseProcessorTest.SAMPLE_XML.getBytes(SampleDataDecoder.EUC_KR));
	}

	@Test
	void fetchRefusesWithoutCallingApiWhenResponseExists() {
		BootstrapStore store = new BootstrapStore(this.dir);
		store.saveRawResponse(RESPONSE);

		assertThatThrownBy(() -> process(store, (input) -> RESPONSE).run(BootstrapProcess.Mode.FETCH))
			.isInstanceOf(BootstrapException.class);
		assertThat(this.apiCalls).hasValue(0);
	}

	@Test
	void keepsRawResponseWhenDecryptionFails() {
		BootstrapStore store = new BootstrapStore(this.dir);

		assertThatThrownBy(() -> process(store, (input) -> "{\"APPLICANT_KEY\":\"K\"}").run(BootstrapProcess.Mode.FETCH))
			.isInstanceOf(BootstrapException.class);
		assertThat(store.readRawResponse()).isEqualTo("{\"APPLICANT_KEY\":\"K\"}");
		assertThat(store.connectionPath()).doesNotExist();
	}

	@Test
	void decryptOnlyUsesSavedResponseWithoutCallingApi() {
		BootstrapStore store = new BootstrapStore(this.dir);
		store.saveRawResponse(RESPONSE);

		process(store, (input) -> RESPONSE).run(BootstrapProcess.Mode.DECRYPT_ONLY);

		assertThat(this.apiCalls).hasValue(0);
		assertThat(store.connectionPath()).exists();
	}

	@Test
	void consoleSummaryHidesSecrets() {
		process(new BootstrapStore(this.dir), (input) -> RESPONSE).run(BootstrapProcess.Mode.FETCH);

		String output = this.console.toString(StandardCharsets.UTF_8);
		assertThat(output).contains("APPLICANT_KEY: KEY-****", "ORDER_TB_CONN 복호화 완료: 항목 [HOST, PASSWORD]");
		assertThat(output).doesNotContain(DB_PASSWORD, "KEY-1234", "010-1234-5678");
	}

	@Test
	void parseModeAcceptsOnlyKnownArguments() {
		assertThat(BootstrapMain.parseMode(new String[0])).isEqualTo(BootstrapProcess.Mode.FETCH);
		assertThat(BootstrapMain.parseMode(new String[] { "--decrypt-only" }))
			.isEqualTo(BootstrapProcess.Mode.DECRYPT_ONLY);
		assertThatThrownBy(() -> BootstrapMain.parseMode(new String[] { "--force" }))
			.isInstanceOf(BootstrapException.class);
	}

	private BootstrapProcess process(BootstrapStore store, Function<BootstrapInput, String> api) {
		BootstrapInput input = new BootstrapInput("https://example.com/api", "user", "password", "홍길동",
				ResponseProcessorTest.PHONE, "hong@example.com");
		Function<BootstrapInput, String> countingApi = (in) -> {
			this.apiCalls.incrementAndGet();
			return api.apply(in);
		};
		return new BootstrapProcess(input, store, countingApi,
				new PrintStream(this.console, true, StandardCharsets.UTF_8));
	}

}
