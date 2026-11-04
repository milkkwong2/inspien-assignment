package com.inspien.assignment.numbering.application;

import org.springframework.stereotype.Service;

import com.inspien.assignment.global.transaction.HistoryTx;
import com.inspien.assignment.numbering.application.port.SequenceStore;
import com.inspien.assignment.numbering.domain.IdFormat;
import com.inspien.assignment.numbering.domain.IdSequence;

/**
 * ORDER_ID·SHIPMENT_ID 발급 (D-08). 단일 인스턴스 전제. H2 시퀀스만 다룬다.
 */
@Service
public class NumberingService {

	private final SequenceStore sequences;

	public NumberingService(SequenceStore sequences) {
		this.sequences = sequences;
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
	 * 다음 발급 값을 적어도 minNext로 올린다. 이미 크면 그대로 둔다. 기동 시 동기화({@link NumberingSyncFacade})가 쓴다.
	 *
	 * @return 동기화 후 다음에 나갈 값
	 */
	@HistoryTx
	public long raiseNextTo(IdSequence sequence, long minNext) {
		return this.sequences.raiseNextTo(sequence, minNext);
	}

}
