package com.inspien.assignment.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BootstrapStoreTest {

	@TempDir
	Path dir;

	@Test
	void createsDirectoryAndSavesRawResponse() {
		BootstrapStore store = new BootstrapStore(this.dir.resolve("config"));

		store.saveRawResponse("{\"APPLICANT_KEY\":\"K1\"}");

		assertThat(store.readRawResponse()).isEqualTo("{\"APPLICANT_KEY\":\"K1\"}");
	}

	@Test
	void refusesWhenRawResponseAlreadyExists() {
		BootstrapStore store = new BootstrapStore(this.dir);
		store.saveRawResponse("first");

		assertThatThrownBy(store::ensureNoRawResponse).isInstanceOf(BootstrapException.class)
			.hasMessageContaining("--decrypt-only");
		assertThatThrownBy(() -> store.saveRawResponse("second")).isInstanceOf(BootstrapException.class);
		assertThat(store.readRawResponse()).isEqualTo("first");
	}

	@Test
	void readFailsWhenNoRawResponse() {
		assertThatThrownBy(() -> new BootstrapStore(this.dir).readRawResponse())
			.isInstanceOf(BootstrapException.class);
	}

	@Test
	void overwritesDerivedFiles() throws IOException {
		BootstrapStore store = new BootstrapStore(this.dir);

		store.saveConnection("old");
		store.saveConnection("new");
		store.saveSampleOrder(new byte[] { 1, 2 });
		store.saveSampleOrder(new byte[] { 3 });

		assertThat(Files.readString(store.connectionPath())).isEqualTo("new");
		assertThat(Files.readAllBytes(store.sampleOrderPath())).containsExactly(3);
	}

	@Test
	void overwritesErrorResponseBecauseFailuresRepeat() throws IOException {
		BootstrapStore store = new BootstrapStore(this.dir);

		store.saveErrorResponse("first error");
		store.saveErrorResponse("second error");

		assertThat(Files.readString(store.errorResponsePath())).isEqualTo("second error");
		store.ensureNoRawResponse();
	}

	@Test
	void leavesNoTemporaryFiles() throws IOException {
		BootstrapStore store = new BootstrapStore(this.dir);

		store.saveRawResponse("raw");
		store.saveConnection("conn");
		assertThatThrownBy(() -> store.saveRawResponse("again")).isInstanceOf(BootstrapException.class);

		try (Stream<Path> files = Files.list(this.dir)) {
			assertThat(files.map((path) -> path.getFileName().toString()))
				.containsExactlyInAnyOrder(BootstrapStore.RAW_RESPONSE, BootstrapStore.CONNECTION);
		}
	}

}
