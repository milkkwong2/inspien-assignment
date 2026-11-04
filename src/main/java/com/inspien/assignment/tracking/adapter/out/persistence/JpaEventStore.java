package com.inspien.assignment.tracking.adapter.out.persistence;

import java.util.List;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import com.inspien.assignment.global.event.EventStage;
import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.tracking.application.port.EventStore;
import com.inspien.assignment.tracking.domain.EventRecord;

/**
 * H2 PROCESS_EVENT 어댑터. 이벤트 기록(EventRecord)은 구간·결과를 문자열로 들고 조회 API에 그대로 나간다. 엔티티는 enum으로
 * 매핑하므로 문자열과 enum은 여기서만 바꾼다.
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
		EventStage stage = EventStage.valueOf(event.stage());
		ProcessEvent.Result result = ProcessEvent.Result.valueOf(event.result());
		this.em.persist(new ProcessEventEntity(event.id(), event.occurredAt(), event.messageId(), event.orderId(),
				stage, result, event.detail()));
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
		// STAGE 컬럼은 비어 있을 수 있다 (schema-h2.sql)
		String stage = (entity.stage() != null) ? entity.stage().name() : null;
		return new EventRecord(entity.id(), entity.occurredAt(), entity.messageId(), entity.orderId(), stage,
				entity.result().name(), entity.detail());
	}

}
