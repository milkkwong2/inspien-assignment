package com.inspien.assignment.bootstrap;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SampleDataDecoderTest {

	private static final String XML = "<?xml version=\"1.0\" encoding=\"EUC-KR\"?><ORDER><NAME>홍길동</NAME></ORDER>";

	@Test
	void decodesEucKrXml() {
		String base64 = Base64.getEncoder().encodeToString(XML.getBytes(SampleDataDecoder.EUC_KR));

		assertThat(SampleDataDecoder.decodeToString(base64)).isEqualTo(XML);
	}

	@Test
	void decodeBytesKeepsOriginalEucKrBytes() {
		byte[] eucKr = XML.getBytes(SampleDataDecoder.EUC_KR);
		String base64 = Base64.getEncoder().encodeToString(eucKr);

		assertThat(SampleDataDecoder.decodeBytes(base64)).isEqualTo(eucKr);
	}

	@Test
	void rejectsInvalidBase64() {
		assertThatThrownBy(() -> SampleDataDecoder.decodeBytes("not*base64"))
			.isInstanceOf(BootstrapException.class);
	}

}
