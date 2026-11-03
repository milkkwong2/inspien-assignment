package com.inspien.assignment.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BootstrapInputTest {

	@TempDir
	Path dir;

	@Test
	void loadsUtf8PropertiesFile() throws IOException {
		Path file = writeProperties("""
				recruit.api.url=https://example.com/http/RecruitingTest
				recruit.api.username=sb-user!b1|it-rt!b2
				recruit.api.password=pa$$word=
				applicant.name=홍길동
				applicant.phone=010-1234-5678
				applicant.email=hong@example.com
				""");

		BootstrapInput input = BootstrapInput.load(file);

		assertThat(input.name()).isEqualTo("홍길동");
		assertThat(input.username()).isEqualTo("sb-user!b1|it-rt!b2");
		assertThat(input.password()).isEqualTo("pa$$word=");
		assertThat(input.phoneNumber()).isEqualTo("010-1234-5678");
	}

	@Test
	void rejectsPhoneNumberWithoutHyphens() {
		assertThatThrownBy(() -> input("01012345678", "hong@example.com"))
			.isInstanceOf(BootstrapException.class)
			.hasMessageContaining("applicant.phone");
	}

	@Test
	void rejectsInvalidEmail() {
		assertThatThrownBy(() -> input("010-1234-5678", "hong.example.com"))
			.isInstanceOf(BootstrapException.class)
			.hasMessageContaining("applicant.email");
	}

	@Test
	void reportsAllMissingValuesAtOnce() {
		assertThatThrownBy(() -> new BootstrapInput(null, " ", null, "홍길동", null, null))
			.isInstanceOf(BootstrapException.class)
			.hasMessageContaining("recruit.api.url")
			.hasMessageContaining("recruit.api.username")
			.hasMessageContaining("recruit.api.password")
			.hasMessageContaining("applicant.phone")
			.hasMessageContaining("applicant.email")
			.hasMessageNotContaining("applicant.name");
	}

	@Test
	void failsWhenFileIsMissing() {
		assertThatThrownBy(() -> BootstrapInput.load(this.dir.resolve("missing.properties")))
			.isInstanceOf(BootstrapException.class)
			.hasMessageContaining("bootstrap.properties.example");
	}

	@Test
	void toStringMasksSecretsAndPersonalData() {
		String text = input("010-1234-5678", "hong@example.com").toString();

		assertThat(text).doesNotContain("secret-password", "010-1234-5678", "hong@example.com", "secret-user",
				"홍길동");
	}

	private static BootstrapInput input(String phone, String email) {
		return new BootstrapInput("https://example.com/api", "secret-user", "secret-password", "홍길동", phone, email);
	}

	private Path writeProperties(String content) throws IOException {
		Path file = this.dir.resolve("bootstrap.properties");
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

}
