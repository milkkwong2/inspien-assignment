package com.inspien.assignment.inbound.adapter.in.web;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.unit.DataSize;

import com.inspien.assignment.global.error.ApiException;
import com.inspien.assignment.global.error.ErrorCode;

/**
 * 수신 본문을 상한까지만 읽는다 (설계서 §5.1, D-17).
 *
 * <p>수신은 검증보다 먼저 원본을 H2에 저장하므로, 큰 본문을 받아 두는 것 자체가 힙과 디스크를 채우는 공격 경로다. 그래서
 * 상한을 넘는 본문은 수신 잠금을 잡기 전, H2에 저장하기 전에 413으로 거절한다. 거절한 요청은 기록이 없어 조회 API에 나오지
 * 않고 로그(WARN)에만 남는다.
 *
 * <p>Content-Length가 이미 상한을 넘으면 읽지 않고 거절한다. 다만 헤더는 없거나(chunked) 틀릴 수 있으므로 그것만 믿지
 * 않고, 실제로 상한 + 1바이트까지만 읽어 넘는지 본다. raw 본문에는 Tomcat의 max-http-form-post-size가 적용되지 않는다.
 */
final class LimitedBodyReader {

	private static final Logger log = LoggerFactory.getLogger(LimitedBodyReader.class);

	private static final long UNKNOWN_LENGTH = -1;

	private final int maxBytes;

	private final String tooLargeDetail;

	LimitedBodyReader(DataSize maxBodySize) {
		long bytes = maxBodySize.toBytes();
		// 상한 + 1바이트를 배열 하나로 읽으므로 int 범위 안이어야 한다
		if (bytes <= 0 || bytes >= Integer.MAX_VALUE) {
			throw new IllegalStateException("app.inbound.max-body-size는 0보다 크고 2GB보다 작아야 합니다: " + maxBodySize);
		}
		this.maxBytes = (int) bytes;
		String limit = readable(maxBodySize);
		this.tooLargeDetail = "본문이 너무 큽니다. 최대 %s까지 받습니다.".formatted(limit);
	}

	/**
	 * @param body 요청 본문
	 * @param declaredLength Content-Length 헤더 값. 없으면 -1
	 * @return 읽은 본문. 본문이 없으면 빈 배열
	 * @throws ApiException 상한을 넘으면 PAYLOAD_TOO_LARGE
	 */
	byte[] read(InputStream body, long declaredLength) throws IOException {
		if (declaredLength > this.maxBytes) {
			String declared = "Content-Length " + declaredLength + "바이트";
			throw tooLarge(declared);
		}
		int readLimit = this.maxBytes + 1;
		byte[] received = body.readNBytes(readLimit);
		if (received.length > this.maxBytes) {
			String declared = (declaredLength == UNKNOWN_LENGTH) ? "없음" : declaredLength + "바이트";
			String actual = "읽은 크기 " + received.length + "바이트 이상 (Content-Length " + declared + ")";
			throw tooLarge(actual);
		}
		return received;
	}

	private ApiException tooLarge(String receivedSize) {
		log.warn("수신 본문이 상한 {}바이트를 넘어 저장하지 않고 거절합니다: {}", this.maxBytes, receivedSize);
		return new ApiException(ErrorCode.PAYLOAD_TOO_LARGE, this.tooLargeDetail);
	}

	/**
	 * 송신자가 읽는 상한 표기. 1MB, 512KB처럼 나누어떨어지는 단위로 쓴다.
	 */
	private static String readable(DataSize size) {
		long bytes = size.toBytes();
		if (bytes % DataSize.ofMegabytes(1).toBytes() == 0) {
			return size.toMegabytes() + "MB";
		}
		if (bytes % DataSize.ofKilobytes(1).toBytes() == 0) {
			return size.toKilobytes() + "KB";
		}
		return bytes + "바이트";
	}

}
