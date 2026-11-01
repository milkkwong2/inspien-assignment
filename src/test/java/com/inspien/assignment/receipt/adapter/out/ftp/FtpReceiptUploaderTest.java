package com.inspien.assignment.receipt.adapter.out.ftp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.inspien.assignment.global.config.AppProperties;
import com.inspien.assignment.receipt.application.port.ReceiptUploader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 로컬과 같은 vsftpd 이미지(목록·신규 업로드만 허용)로 FTP 어댑터를 확인한다 (D-14).
 *
 * <p>패시브 모드의 데이터 포트는 서버가 알려 준 번호로 바로 접속하므로, 호스트와 컨테이너의 포트 번호를 같게 묶는다.
 */
class FtpReceiptUploaderTest {

	static final int PASV_MIN = 21200;

	static final int PASV_MAX = 21204;

	static final GenericContainer<?> FTP = new GenericContainer<>(
			new ImageFromDockerfile("inspien-test-ftp", false).withFileFromPath(".", Path.of("local/ftp")))
		.withEnv("FTP_PASSWORD", "recruit_local")
		.withEnv("PASV_ADDRESS", "127.0.0.1")
		.withEnv("PASV_MIN_PORT", String.valueOf(PASV_MIN))
		.withEnv("PASV_MAX_PORT", String.valueOf(PASV_MAX))
		.withExposedPorts(21)
		.withCreateContainerCmdModifier(cmd -> {
			List<ExposedPort> exposed = new ArrayList<>(List.of(cmd.getExposedPorts()));
			List<PortBinding> bindings = new ArrayList<>(cmd.getHostConfig().getPortBindings().getBindings().entrySet()
				.stream()
				.flatMap(e -> java.util.Arrays.stream(e.getValue()).map(b -> new PortBinding(b, e.getKey())))
				.toList());
			for (int port = PASV_MIN; port <= PASV_MAX; port++) {
				exposed.add(ExposedPort.tcp(port));
				bindings.add(new PortBinding(Ports.Binding.bindPort(port), ExposedPort.tcp(port)));
			}
			cmd.withExposedPorts(exposed);
			cmd.getHostConfig().withPortBindings(bindings);
		})
		.waitingFor(Wait.forListeningPort());

	static FtpReceiptUploader uploader;

	@BeforeAll
	static void start() {
		FTP.start();
		uploader = uploader("recruit_local");
	}

	@Test
	void listsSharedFolderWithKoreanFileNames() {
		try (ReceiptUploader.Session session = uploader.open()) {
			assertThat(session.list()).containsKey("영수증_김철수_A001.csv");
		}
	}

	@Test
	void uploadsInBinaryModeAndKeepsExactSize() {
		byte[] content = "﻿ORDER_ID\r\nA001\r\n".getBytes(StandardCharsets.UTF_8);
		try (ReceiptUploader.Session session = uploader.open()) {
			session.upload("영수증_홍길동_B001.csv", content);
		}
		try (ReceiptUploader.Session session = uploader.open()) {
			Map<String, Long> listing = session.list();
			assertThat(listing).containsEntry("영수증_홍길동_B001.csv", (long) content.length);
		}
	}

	@Test
	void cannotOverwriteExistingFile() {
		try (ReceiptUploader.Session session = uploader.open()) {
			session.upload("영수증_홍길동_B002.csv", new byte[] { 1 });
			assertThatThrownBy(() -> session.upload("영수증_홍길동_B002.csv", new byte[] { 2, 3 }))
				.isInstanceOf(ReceiptUploader.UploadFailedException.class)
				.satisfies(ex -> assertThat(((ReceiptUploader.UploadFailedException) ex).connectionLost()).isFalse());
		}
	}

	@Test
	void wrongPasswordIsConnectionFailure() {
		assertThatThrownBy(() -> uploader("wrong").open()).isInstanceOf(ReceiptUploader.ConnectionFailedException.class)
			.hasMessageContaining("로그인 실패");
	}

	@Test
	void unreachableServerIsConnectionFailure() {
		AppProperties.Ftp ftp = new AppProperties.Ftp("127.0.0.1", 1, "recruit", "x", "Recruit/2026/",
				Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(5), "UTF-8");
		assertThatThrownBy(() -> new FtpReceiptUploader(properties(ftp)).open())
			.isInstanceOf(ReceiptUploader.ConnectionFailedException.class);
	}

	static FtpReceiptUploader uploader(String password) {
		AppProperties.Ftp ftp = new AppProperties.Ftp(FTP.getHost(), FTP.getMappedPort(21), "recruit", password,
				"Recruit/2026/", Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(30), "UTF-8");
		return new FtpReceiptUploader(properties(ftp));
	}

	static AppProperties properties(AppProperties.Ftp ftp) {
		return new AppProperties(new AppProperties.Applicant("K", "홍길동"), null, null, null, null, ftp, null, null,
				null, null);
	}

}
