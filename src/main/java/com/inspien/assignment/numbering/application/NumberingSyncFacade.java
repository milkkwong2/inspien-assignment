package com.inspien.assignment.numbering.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import com.inspien.assignment.numbering.application.port.IssuedIdReader;
import com.inspien.assignment.numbering.application.port.IssuedIdReader.IssuedIds;
import com.inspien.assignment.numbering.domain.IdFormat;

/**
 * 기동 시 동기화: 다음 발급 값 = max(시퀀스 다음 값, 내 최댓값 + 1) (설계서 §7.3).
 *
 * <p>평소에는 아무 일도 없다. H2가 비어 있을 때(새로 받은 코드, 다른 PC, 파일 손실) 이미 쓴 번호를 다시 주지 않게 막는다.
 * 트랜잭션을 걸지 않는다. 이미 쓴 번호는 각 Oracle의 읽기 전용 트랜잭션에서 읽고(IssuedIdReader를 구현한 Service),
 * H2 트랜잭션은 시퀀스를 올리는 동안만 연다. Oracle에 닿지 못하면 예외가 그대로 나가 기동이 실패한다 (D-22).
 */
@Service
public class NumberingSyncFacade {

	private static final Logger log = LoggerFactory.getLogger(NumberingSyncFacade.class);

	private final NumberingService numbering;

	private final List<IssuedIdReader> issuedIdReaders;

	public NumberingSyncFacade(NumberingService numbering, List<IssuedIdReader> issuedIdReaders) {
		this.numbering = numbering;
		this.issuedIdReaders = issuedIdReaders;
	}

	public void synchronizeWithIssuedIds() {
		for (IssuedIdReader reader : this.issuedIdReaders) {
			IssuedIds issued = reader.issuedIds();
			long maxIssued = maxIssued(issued);
			long next = this.numbering.raiseNextTo(issued.sequence(), maxIssued + 1);
			String maxText = (maxIssued == 0) ? "없음" : IdFormat.format(maxIssued);
			String nextText = (next > IdFormat.MAX) ? "소진" : IdFormat.format(next);
			log.info("{} 채번 동기화: 내 최댓값 {}, 다음 발급 {}", issued.sequence(), maxText, nextText);
		}
	}

	/**
	 * 번호 형식인 값 중 최댓값. 없으면 0.
	 */
	private static long maxIssued(IssuedIds issued) {
		long maxIssued = 0;
		for (String id : issued.ids()) {
			Long value = IdFormat.parse(id).orElse(null);
			if (value == null) {
				log.warn("{} 번호 형식이 아닌 값은 채번 동기화에서 제외합니다: {}", issued.sequence(), id);
				continue;
			}
			maxIssued = Math.max(maxIssued, value);
		}
		return maxIssued;
	}

}
