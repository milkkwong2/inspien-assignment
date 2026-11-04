package com.inspien.assignment.inbound.application;

import java.nio.charset.Charset;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import com.inspien.assignment.global.config.PropertyChecks;
import com.inspien.assignment.inbound.domain.ValueLimit;

/**
 * IF-01 수신 설정 (설계서 §9). 수신 Facade·잠금과 어댑터(컨트롤러, 파서)가 함께 쓰므로 application에 둔다. 어댑터는
 * application을 볼 수 있고, domain은 Spring을 모른 채 남는다 (D-21).
 *
 * <p>문자셋은 Charset으로 받아 이름이 틀리면 바인딩할 때(기동할 때) 실패한다. 기본 문자셋이 허용 목록(UTF-8, EUC-KR
 * 계열)에 드는지는 파서가 만들어질 때 본다 (D-20).
 *
 * @param defaultCharset 본문에 인코딩 표시가 없을 때 (D-20)
 * @param duplicateWindow 완료된 메시지와 같은 본문을 같은 메시지로 보는 시간 (D-24)
 * @param lockStripes 수신 잠금 수 (D-16)
 * @param lockWait 수신 잠금 대기 상한 (D-16)
 * @param dbCharset ORDER_TB 값 길이를 바이트로 셀 때의 문자셋 (D-07, 설계서 §11)
 * @param maxValueBytes ORDER_TB 컬럼 최대 바이트 수 (VARCHAR2(100))
 * @param maxBodySize 수신 본문 상한. 넘으면 저장하기 전에 413으로 거절한다 (D-17)
 */
@ConfigurationProperties("app.inbound")
public record InboundProperties(Charset defaultCharset, Duration duplicateWindow, int lockStripes, Duration lockWait,
		Charset dbCharset, int maxValueBytes, DataSize maxBodySize) {

	public InboundProperties {
		PropertyChecks.required("app.inbound.default-charset", defaultCharset);
		PropertyChecks.positive("app.inbound.duplicate-window", duplicateWindow);
		PropertyChecks.positive("app.inbound.lock-stripes", lockStripes);
		PropertyChecks.positive("app.inbound.lock-wait", lockWait);
		PropertyChecks.required("app.inbound.db-charset", dbCharset);
		PropertyChecks.positive("app.inbound.max-value-bytes", maxValueBytes);
		PropertyChecks.positive("app.inbound.max-body-size", maxBodySize);
	}

	public ValueLimit valueLimit() {
		return new ValueLimit(this.dbCharset, this.maxValueBytes);
	}

}
