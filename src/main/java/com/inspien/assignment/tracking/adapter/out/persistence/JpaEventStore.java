package com.inspien.assignment.tracking.adapter.out.persistence;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

import com.inspien.assignment.tracking.application.port.EventStore;
import com.inspien.assignment.tracking.domain.EventRecord;

/**
 * H2 PROCESS_EVENT 어댑터.
 */
@Repository
class JpaEventStore implements EventStore {

	@PersistenceContext(unitName = "history")
	private EntityManager em;

	@Override
	public void insert(EventRecord e) {
		this.em.persist(new ProcessEventEntity(e.id(), e.occurredAt(), e.messageId(), e.orderId(), e.stage(),
				e.result(), e.detail()));
	}

	@Override
	public List<EventRecord> findByOrderId(String orderId) {
		return this.em
			.createQuery("select e from ProcessEventEntity e where e.orderId = :id order by e.id", ProcessEventEntity.class)
			.setParameter("id", orderId)
			.getResultList()
			.stream()
			.map(JpaEventStore::toModel)
			.toList();
	}

	@Override
	public List<EventRecord> findByMessageId(String messageId) {
		return this.em
			.createQuery("select e from ProcessEventEntity e where e.messageId = :id order by e.id",
					ProcessEventEntity.class)
			.setParameter("id", messageId)
			.getResultList()
			.stream()
			.map(JpaEventStore::toModel)
			.toList();
	}

	private static EventRecord toModel(ProcessEventEntity e) {
		return new EventRecord(e.id(), e.occurredAt(), e.messageId(), e.orderId(), e.stage(), e.result(), e.detail());
	}

}
