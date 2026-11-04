package com.inspien.assignment.global.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import com.inspien.assignment.inbound.application.InboundProperties;
import com.inspien.assignment.receipt.adapter.out.csv.ReceiptProperties;
import com.inspien.assignment.receipt.adapter.out.ftp.FtpProperties;
import com.inspien.assignment.stage.application.RetryProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 프로필별 설정 바인딩과 기동 시 검사만 본다. DB에는 붙지 않는다.
 *
 * <p>앱과 같이 설정 레코드를 모두 스캔한다. 새 설정 레코드도 여기서 application*.yml로 바인딩된다.
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
			ApplicantProperties applicant = context.getBean(ApplicantProperties.class);
			AppProperties properties = context.getBean(AppProperties.class);
			FtpProperties ftp = context.getBean(FtpProperties.class);

			assertThat(applicant).isEqualTo(new ApplicantProperties("FAKE-KEY-1", "홍길동"));
			assertThat(properties.orderDb().url()).isEqualTo("jdbc:oracle:thin:@db:1521:ORCL");
			assertThat(properties.orderDb().password()).isEqualTo("p$w=1");
			assertThat(properties.orderDb().poolSize()).isEqualTo(7);
			assertThat(properties.shipmentDb().username()).isEqualTo("APP2");
			assertThat(properties.shipmentDb().poolSize()).isEqualTo(2);
			assertThat(ftp.port()).isEqualTo(30021);
			assertThat(ftp.path()).isEqualTo("Recruit/2026/");
			assertThat(applicant.toString()).doesNotContain("FAKE-KEY-1", "홍길동")
				.isEqualTo("ApplicantProperties[key=FAKE******, name=****]");
			assertThat(properties.toString()).doesNotContain("p$w=1", "p2");
			assertThat(ftp.toString()).doesNotContain("fp");
		}
	}

	@Test
	void providedProfileFailsWithoutBootstrapFiles() {
		assertThatThrownBy(() -> run("provided", "--app.provided-config-dir=" + this.configDir))
			.hasMessageContaining("connection.json");
	}

	/**
	 * 지원자·접속 정보는 프로필만 채운다. 어느 설정이 먼저 실패하든 프로필을 확인하라고 알린다 (D-11).
	 */
	@Test
	void failsWithoutProfile() {
		assertThatThrownBy(() -> run(null)).rootCause().hasMessageContaining(PropertyChecks.PROFILE_HINT);
	}

	@Test
	void applicantKeyAndNameAreRequired() {
		assertThatThrownBy(() -> new ApplicantProperties("KEY", " ")).hasMessageContaining("app.applicant.key")
			.hasMessageContaining("app.applicant.name");
	}

	@Test
	void localProfileBindsEveryModuleWithTypedValues() {
		try (ConfigurableApplicationContext context = run("local")) {
			assertThat(context.getBean(ApplicantProperties.class).key()).isEqualTo("LOCAL-APPLICANT-0001");
			assertThat(context.getBean(InboundProperties.class).defaultCharset().name()).isEqualTo("EUC-KR");
			assertThat(context.getBean(InboundProperties.class).dbCharset()).isEqualTo(StandardCharsets.UTF_8);
			assertThat(context.getBean(ReceiptProperties.class).contentCharset()).isEqualTo(StandardCharsets.UTF_8);
			assertThat(context.getBean(FtpProperties.class).filenameEncoding()).isEqualTo(StandardCharsets.UTF_8);
			assertThat(context.getBean(SchedulerProperties.class).batchSize()).isEqualTo(100);
			assertThat(context.getBean(RetryProperties.class).maxAttempts()).isEqualTo(5);
		}
	}

	/**
	 * 잘못된 값은 쓰는 곳에서 늦게 드러나지 않고 기동할 때 키 이름과 함께 실패한다. 잠금 수 0은 첫 수신에서 0으로 나누고,
	 * 주기당 건수 0은 오류 없이 아무것도 처리하지 않는다.
	 */
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "app.inbound.lock-stripes=0 | app.inbound.lock-stripes 값은 1 이상",
			"app.inbound.max-value-bytes=0 | app.inbound.max-value-bytes 값은 1 이상",
			"app.inbound.lock-wait=0s | app.inbound.lock-wait 값은 0보다 길어야",
			"app.inbound.max-body-size=0B | app.inbound.max-body-size 값은 0보다 커야",
			"app.scheduler.batch-size=0 | app.scheduler.batch-size 값은 1 이상",
			"app.scheduler.fixed-delay=-1s | app.scheduler.fixed-delay 값은 0보다 길어야",
			"app.retry.max-attempts=0 | app.retry.max-attempts 값은 1 이상",
			"app.retry.multiplier=0 | app.retry.multiplier 값은 1 이상",
			"app.oracle.pool-wait=1s | app.oracle.pool-wait(PT1S)는 app.oracle.connect-timeout(PT3S)보다 길어야" })
	void invalidValueFailsAtStartup(String setting, String message) {
		assertThatThrownBy(() -> run("local", "--" + setting)).rootCause()
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(message);
	}

	/**
	 * 문자셋은 Charset으로 받으므로 이름이 틀리면 바인딩에서 실패한다. 예전에는 쓰는 클래스의 생성자가 이름을 풀었다.
	 */
	@ParameterizedTest
	@CsvSource({ "app.inbound.default-charset", "app.inbound.db-charset", "app.receipt.content-charset",
			"app.ftp.filename-encoding" })
	void unknownCharsetFailsAtStartup(String key) {
		assertThatThrownBy(() -> run("local", "--" + key + "=NO-SUCH-CHARSET")).hasStackTraceContaining(key)
			.rootCause()
			.isInstanceOf(UnsupportedCharsetException.class);
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
	@ConfigurationPropertiesScan("com.inspien.assignment")
	static class PropertiesOnly {

	}

}
