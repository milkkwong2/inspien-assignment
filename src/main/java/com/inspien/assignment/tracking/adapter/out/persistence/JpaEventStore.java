package com.inspien.assignment.tracking.adapter.out.persistence;

import java.util.List;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import com.inspien.assignment.tracking.application.port.EventStore;
import com.inspien.assignment.tracking.domain.EventRecord;

/**
 * H2 PROCESS_EVENT 어댑터.
 */
@Repository
class JpaEventStore implements EventStore {

	private static final QProcessEventEntity processEvent = QProcessEventEntity.processEventEntity;

	@PersistenceContext(unitName = "history")
	private EntityManager em;

	private final JPAQueryFactory query;

	JpaEventStore(@Qualifier("historyQueryFactory") JPAQueryFactory query) {
		this.query = query;
	}

	@Override
	public void insert(EventRecord event) {
		this.em.persist(new ProcessEventEntity(event.id(), event.occurredAt(), event.messageId(), event.orderId(),
				event.stage(), event.result(), event.detail()));
	}

	@Override
	public List<EventRecord> findByOrderId(String orderId) {
		return this.query.selectFrom(processEvent)
			.where(processEvent.orderId.eq(orderId))
			.orderBy(processEvent.id.asc())
			.fetch()
			.stream()
			.map(JpaEventStore::toModel)
			.toList();
	}

	@Override
	public List<EventRecord> findByMessageId(String messageId) {
		return this.query.selectFrom(processEvent)
			.where(processEvent.messageId.eq(messageId))
			.orderBy(processEvent.id.asc())
			.fetch()
			.stream()
			.map(JpaEventStore::toModel)
			.toList();
	}

	private static EventRecord toModel(ProcessEventEntity entity) {
		return new EventRecord(entity.id(), entity.occurredAt(), entity.messageId(), entity.orderId(), entity.stage(),
				entity.result(), entity.detail());
	}

}
