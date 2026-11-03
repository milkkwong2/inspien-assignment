package com.inspien.assignment.numbering.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import com.inspien.assignment.global.transaction.HistoryTx;
import com.inspien.assignment.numbering.application.port.IssuedIdReader;
import com.inspien.assignment.numbering.application.port.SequenceStore;
import com.inspien.assignment.numbering.domain.IdFormat;
import com.inspien.assignment.numbering.domain.IdSequence;

/**
 * ORDER_ID·SHIPMENT_ID 발급 (D-08). 단일 인스턴스 전제.
 */
@Service
public class NumberingService {

	private static final Logger log = LoggerFactory.getLogger(NumberingService.class);

	private final SequenceStore sequences;

	private final List<IssuedIdReader> issuedIdReaders;

	public NumberingService(SequenceStore sequences, List<IssuedIdReader> issuedIdReaders) {
		this.sequences = sequences;
		this.issuedIdReaders = issuedIdReaders;
	}

	/**
	 * 다음 번호. 호출한 쪽의 H2 트랜잭션에 참여해, 번호 기록(MESSAGE_ORDER, ORDER_STAGE)과 함께 커밋된다.
	 *
	 * @throws com.inspien.assignment.numbering.domain.IdExhaustedException Z999를 넘으면
	 */
	@HistoryTx
	public String next(IdSequence sequence) {
		long value = this.sequences.next(sequence);
		return IdFormat.format(value);
	}

	/**
	 * 기동 시 동기화: 다음 발급 값 = max(시퀀스 다음 값, 내 최댓값 + 1) (설계서 §7.3).
	 *
	 * <p>평소에는 아무 일도 없다. H2가 비어 있을 때(새로 받은 코드, 다른 PC, 파일 손실) 이미 쓴 번호를 다시 주지 않게 막는다.
	 * Oracle에 닿지 못하면 예외가 그대로 나가 기동이 실패한다 (D-22).
	 */
	@HistoryTx
	public void synchronizeWithIssuedIds() {
		for (IssuedIdReader reader : this.issuedIdReaders) {
			long maxIssued = 0;
			for (String id : reader.issuedIds()) {
				Long value = IdFormat.parse(id).orElse(null);
				if (value == null) {
					log.warn("{} 번호 형식이 아닌 값은 채번 동기화에서 제외합니다: {}", reader.sequence(), id);
					continue;
				}
				maxIssued = Math.max(maxIssued, value);
			}
			long next = this.sequences.raiseNextTo(reader.sequence(), maxIssued + 1);
			String maxText = (maxIssued == 0) ? "없음" : IdFormat.format(maxIssued);
			String nextText = (next > IdFormat.MAX) ? "소진" : IdFormat.format(next);
			log.info("{} 채번 동기화: 내 최댓값 {}, 다음 발급 {}", reader.sequence(), maxText, nextText);
		}
	}

}
