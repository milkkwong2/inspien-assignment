package com.inspien.assignment.inbound.adapter.in.web;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

import org.springframework.util.unit.DataSize;

import com.inspien.assignment.global.error.ApiException;
import com.inspien.assignment.global.error.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 수신 본문 상한 (설계서 §5.1, D-17). Content-Length는 없거나 틀릴 수 있어 실제로 읽은 크기로도 판단한다.
 */
class LimitedBodyReaderTest {

	static final int LIMIT = 1024;

	static final long NO_CONTENT_LENGTH = -1;

	final LimitedBodyReader reader = new LimitedBodyReader(DataSize.ofKilobytes(1));

	@Test
	void declaredLengthOverLimitIsRejectedWithoutReading() {
		ByteArrayInputStream body = new ByteArrayInputStream(new byte[LIMIT + 1]);

		assertThatThrownBy(() -> this.reader.read(body, LIMIT + 1))
			.isInstanceOfSatisfying(ApiException.class,
					ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE))
			.hasMessage("본문이 너무 큽니다. 최대 1KB까지 받습니다.");
		assertThat(body.available()).as("읽지 않았다").isEqualTo(LIMIT + 1);
	}

	@Test
	void bodyOverLimitWithoutContentLengthIsRejected() {
		ByteArrayInputStream body = new ByteArrayInputStream(new byte[LIMIT * 10]);

		assertThatThrownBy(() -> this.reader.read(body, NO_CONTENT_LENGTH))
			.isInstanceOfSatisfying(ApiException.class,
					ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
		assertThat(body.available()).as("상한 + 1바이트까지만 읽었다").isEqualTo(LIMIT * 10 - (LIMIT + 1));
	}

	@Test
	void bodyLargerThanDeclaredLengthIsRejected() {
		ByteArrayInputStream body = new ByteArrayInputStream(new byte[LIMIT + 1]);

		assertThatThrownBy(() -> this.reader.read(body, 10))
			.isInstanceOfSatisfying(ApiException.class,
					ex -> assertThat(ex.code()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
	}

	@Test
	void bodyAtLimitIsReadAsIs() throws Exception {
		byte[] atLimit = new byte[LIMIT];
		atLimit[LIMIT - 1] = 7;

		byte[] withoutLength = this.reader.read(new ByteArrayInputStream(atLimit), NO_CONTENT_LENGTH);
		byte[] withLength = this.reader.read(new ByteArrayInputStream(atLimit), LIMIT);

		assertThat(withoutLength).isEqualTo(atLimit);
		assertThat(withLength).isEqualTo(atLimit);
	}

	@Test
	void emptyBodyBecomesEmptyArray() throws Exception {
		byte[] received = this.reader.read(new ByteArrayInputStream(new byte[0]), NO_CONTENT_LENGTH);

		assertThat(received).isEmpty();
	}

	@Test
	void detailShowsLimitInReadableUnit() {
		LimitedBodyReader oneMegabyte = new LimitedBodyReader(DataSize.ofMegabytes(1));
		byte[] overOneMegabyte = new byte[(int) DataSize.ofMegabytes(1).toBytes() + 1];
		ByteArrayInputStream body = new ByteArrayInputStream(overOneMegabyte);

		assertThatThrownBy(() -> oneMegabyte.read(body, NO_CONTENT_LENGTH))
			.hasMessage("본문이 너무 큽니다. 최대 1MB까지 받습니다.");
	}

	@Test
	void limitMustBePositive() {
		DataSize zero = DataSize.ofBytes(0);

		assertThatThrownBy(() -> new LimitedBodyReader(zero)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("app.inbound.max-body-size");
	}

}
