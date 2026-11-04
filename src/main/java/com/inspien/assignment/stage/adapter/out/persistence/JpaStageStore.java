package com.inspien.assignment.stage.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import com.inspien.assignment.stage.application.port.StageStore;
import com.inspien.assignment.stage.domain.Stage;
import com.inspien.assignment.stage.domain.StageState;

/**
 * H2 ORDER_STAGE 어댑터.
 */
@Repository
class JpaStageStore implements StageStore {

	private static final QOrderStageEntity orderStage = QOrderStageEntity.orderStageEntity;

	@PersistenceContext(unitName = "history")
	private EntityManager em;

	private final JPAQueryFactory query;

	JpaStageStore(@Qualifier("historyQueryFactory") JPAQueryFactory query) {
		this.query = query;
	}

	@Override
	public Optional<StageState> find(String orderId, Stage stage) {
		OrderStageEntity found = this.em.find(OrderStageEntity.class, new OrderStageEntity.Key(orderId, stage));
		return Optional.ofNullable(found)
			.map(JpaStageStore::toModel);
	}

	@Override
	public Map<String, StageState> findAll(Collection<String> orderIds, Stage stage) {
		if (orderIds.isEmpty()) {
			return Map.of();
		}
		return this.query.selectFrom(orderStage)
			.where(orderStage.stage.eq(stage), orderStage.orderId.in(orderIds))
			.fetch()
			.stream()
			.map(JpaStageStore::toModel)
			.collect(Collectors.toMap(StageState::orderId, Function.identity()));
	}

	@Override
	public void save(StageState state) {
		OrderStageEntity entity = this.em.find(OrderStageEntity.class,
				new OrderStageEntity.Key(state.orderId(), state.stage()));
		boolean isNew = (entity == null);
		if (isNew) {
			entity = new OrderStageEntity(state.orderId(), state.stage());
		}
		// persist 전에 값을 채운다. Hibernate는 persist 시점의 값으로 INSERT한다
		entity.apply(state);
		if (isNew) {
			this.em.persist(entity);
		}
	}

	@Override
	public boolean restartIfFailed(String orderId, Stage stage) {
		// 벌크 UPDATE는 엔티티 콜백을 거치지 않으므로 UPDATED_AT을 직접 넣는다 (D-27)
		return this.query.update(orderStage)
			.set(orderStage.attempts, 0)
			.set(orderStage.reachedFailed, false)
			.setNull(orderStage.nextAttemptAt)
			.set(orderStage.updatedAt, OffsetDateTime.now())
			.where(orderStage.orderId.eq(orderId), orderStage.stage.eq(stage), orderStage.reachedFailed.isTrue())
			.execute() == 1;
	}

	private static StageState toModel(OrderStageEntity entity) {
		return new StageState(entity.orderId(), entity.stage(), entity.attempts(), entity.nextAttemptAt(),
				entity.reachedFailed(), entity.lastError(), entity.artifact(), entity.completedAt());
	}

}
