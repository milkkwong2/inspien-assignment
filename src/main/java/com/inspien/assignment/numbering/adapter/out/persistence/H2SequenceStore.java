package com.inspien.assignment.numbering.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

import com.inspien.assignment.numbering.application.port.SequenceStore;
import com.inspien.assignment.numbering.domain.IdSequence;

/**
 * H2 시퀀스 ORDER_SEQ, SHIPMENT_SEQ (schema-h2.sql).
 */
@Repository
class H2SequenceStore implements SequenceStore {

	@PersistenceContext(unitName = "history")
	private EntityManager em;

	@Override
	public long next(IdSequence sequence) {
		Number value = (Number) this.em.createNativeQuery("SELECT NEXT VALUE FOR " + name(sequence))
			.getSingleResult();
		return value.longValue();
	}

	@Override
	public long raiseNextTo(IdSequence sequence, long minNext) {
		long current = currentNext(sequence);
		if (current >= minNext) {
			return current;
		}
		this.em.createNativeQuery("ALTER SEQUENCE " + name(sequence) + " RESTART WITH " + minNext).executeUpdate();
		return minNext;
	}

	/**
	 * 다음에 나갈 값. H2의 BASE_VALUE가 그 값이다.
	 */
	private long currentNext(IdSequence sequence) {
		Number value = (Number) this.em
			.createNativeQuery("SELECT BASE_VALUE FROM INFORMATION_SCHEMA.SEQUENCES "
					+ "WHERE SEQUENCE_SCHEMA = 'PUBLIC' AND SEQUENCE_NAME = ?1")
			.setParameter(1, name(sequence))
			.getSingleResult();
		return value.longValue();
	}

	private static String name(IdSequence sequence) {
		return switch (sequence) {
			case ORDER -> "ORDER_SEQ";
			case SHIPMENT -> "SHIPMENT_SEQ";
		};
	}

}
