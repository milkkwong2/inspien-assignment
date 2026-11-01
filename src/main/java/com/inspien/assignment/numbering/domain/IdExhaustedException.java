package com.inspien.assignment.numbering.domain;

/**
 * Z999를 넘어 더 발급할 번호가 없다 (D-08). 수신은 500 ORDER_ID_EXHAUSTED로 답한다 (D-17).
 */
public class IdExhaustedException extends RuntimeException {

	public IdExhaustedException(long sequence) {
		super("발급할 번호가 없습니다 (Z999 초과, 시퀀스 " + sequence + ")");
	}

}
