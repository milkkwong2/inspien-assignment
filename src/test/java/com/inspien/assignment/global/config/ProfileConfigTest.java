package com.inspien.assignment.global.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 프로필별 설정 바인딩만 검증한다. DB에는 붙지 않는다.
 */
class ProfileConfigTest {

	@TempDir
	Path configDir;

	@Test
	void providedProfileReadsBootstrapFilesAsIs() throws IOException {
		Files.writeString(this.configDir.resolve("connection.json"), """
				{
				  "APPLICANT_KEY" : "FAKE-KEY-1",
				  "ORDER_TB_CONN" : { "URL" : "jdbc:oracle:thin:@db:1521:ORCL", "ID" : "APP", "PASSWORD" : "p$w=1", "TABLE" : "ORDER_TB" },
				  "SHIPMENT_TB_CONN" : { "URL" : "jdbc:oracle:thin:@db2:1521:ORCL", "ID" : "APP2", "PASSWORD" : "p2", "TABLE" : "SHIPMENT_TB" },
				  "FTP_CONN" : { "URL" : "10.0.0.1", "PORT" : "30021", "ID" : "ftpuser", "PASSWORD" : "fp", "PATH" : "Recruit/2026/" }
				}
				""", StandardCharsets.UTF_8);
		Files.writeString(this.configDir.resolve("bootstrap.properties"), "applicant.name=홍길동\n", StandardCharsets.UTF_8);

		try (ConfigurableApplicationContext context = run("provided",
				"--app.provided-config-dir=" + this.configDir)) {
			AppProperties properties = context.getBean(AppProperties.class);

			assertThat(properties.applicant()).isEqualTo(new AppProperties.Applicant("FAKE-KEY-1", "홍길동"));
			assertThat(properties.orderDb().url()).isEqualTo("jdbc:oracle:thin:@db:1521:ORCL");
			assertThat(properties.orderDb().password()).isEqualTo("p$w=1");
			assertThat(properties.orderDb().poolSize()).isEqualTo(5);
			assertThat(properties.shipmentDb().username()).isEqualTo("APP2");
			assertThat(properties.shipmentDb().poolSize()).isEqualTo(2);
			assertThat(properties.ftp().port()).isEqualTo(30021);
			assertThat(properties.ftp().path()).isEqualTo("Recruit/2026/");
			assertThat(properties.toString()).doesNotContain("p$w=1", "fp");
		}
	}

	@Test
	void providedProfileFailsWithoutBootstrapFiles() {
		assertThatThrownBy(() -> run("provided", "--app.provided-config-dir=" + this.configDir))
			.hasMessageContaining("connection.json");
	}

	@Test
	void failsWithoutProfileBecauseApplicantIsMissing() {
		assertThatThrownBy(() -> run(null)).rootCause().hasMessageContaining("app.applicant.key");
	}

	@Test
	void localProfileHasFakeApplicant() {
		try (ConfigurableApplicationContext context = run("local")) {
			assertThat(context.getBean(AppProperties.class).applicant().key()).isEqualTo("LOCAL-APPLICANT-0001");
		}
	}

	private static ConfigurableApplicationContext run(String profile, String... args) {
		SpringApplicationBuilder builder = new SpringApplicationBuilder(PropertiesOnly.class)
			.web(WebApplicationType.NONE);
		if (profile != null) {
			builder.profiles(profile);
		}
		return builder.run(args);
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(AppProperties.class)
	static class PropertiesOnly {

	}

}
