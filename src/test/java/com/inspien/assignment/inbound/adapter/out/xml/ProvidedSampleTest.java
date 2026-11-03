package com.inspien.assignment.inbound.adapter.out.xml;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import com.inspien.assignment.inbound.domain.IssueReason;
import com.inspien.assignment.inbound.domain.OrderAssembly;
import com.inspien.assignment.inbound.domain.OrderMessage;
import com.inspien.assignment.inbound.domain.ValueLimit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 과제 API가 준 샘플(config/sample-order.xml, Git 제외)로 설계서의 기대 결과를 확인한다.
 * 파일이 없는 환경(새로 받은 코드, CI)에서는 건너뛴다.
 */
@EnabledIf("sampleExists")
class ProvidedSampleTest {

	static final Path SAMPLE = Path.of("config/sample-order.xml");

	static boolean sampleExists() {
		return Files.isRegularFile(SAMPLE);
	}

	@Test
	void sampleGives63Stored7Rejected4Warnings() throws IOException {
		// 송신자처럼 인코딩 표시 없이 보낸다 (D-20)
		XmlOrderMessageParser parser = new XmlOrderMessageParser(Charset.forName("EUC-KR"));
		OrderMessage message = parser.parse(Files.readAllBytes(SAMPLE), null);
		OrderAssembly assembly = OrderAssembly.of(message, new ValueLimit(StandardCharsets.UTF_8, 100));

		assertThat(message.headers()).hasSize(15);
		assertThat(message.items()).hasSize(70);
		assertThat(assembly.accepted()).hasSize(63);
		assertThat(assembly.rejected()).hasSize(7)
			.allSatisfy(rejected -> assertThat(rejected.reason()).isEqualTo(IssueReason.HEADER_NOT_FOUND));
		assertThat(assembly.warnings()).hasSize(4)
			.allSatisfy(warning -> assertThat(warning.reason()).isEqualTo(IssueReason.HEADER_WITHOUT_ITEM));
	}

}
