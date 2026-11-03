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
	public void insert(EventRecord event) {
		this.em.persist(new ProcessEventEntity(event.id(), event.occurredAt(), event.messageId(), event.orderId(),
				event.stage(), event.result(), event.detail()));
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

	private static EventRecord toModel(ProcessEventEntity entity) {
		return new EventRecord(entity.id(), entity.occurredAt(), entity.messageId(), entity.orderId(), entity.stage(),
				entity.result(), entity.detail());
	}

}
