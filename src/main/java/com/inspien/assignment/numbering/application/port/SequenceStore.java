package com.inspien.assignment.numbering.application.port;

import com.inspien.assignment.numbering.domain.IdSequence;

/**
 * H2 시퀀스. 시퀀스는 롤백되지 않으므로 한 번 나간 값은 다시 나가지 않는다 (D-08).
 */
public interface SequenceStore {

	long next(IdSequence sequence);

	/**
	 * 다음에 나갈 값을 적어도 minNext로 올린다. 이미 크면 그대로 둔다.
	 *
	 * @return 동기화 후 다음에 나갈 값
	 */
	long raiseNextTo(IdSequence sequence, long minNext);

}
