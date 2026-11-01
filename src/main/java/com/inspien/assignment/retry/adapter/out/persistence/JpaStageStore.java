package com.inspien.assignment.retry.adapter.out.persistence;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

import com.inspien.assignment.retry.application.port.StageStore;
import com.inspien.assignment.retry.domain.Stage;
import com.inspien.assignment.retry.domain.StageState;

/**
 * H2 ORDER_STAGE 어댑터.
 */
@Repository
class JpaStageStore implements StageStore {

	@PersistenceContext(unitName = "history")
	private EntityManager em;

	@Override
	public Optional<StageState> find(String orderId, Stage stage) {
		return Optional.ofNullable(this.em.find(OrderStageEntity.class, new OrderStageEntity.Key(orderId, stage.name())))
			.map(JpaStageStore::toModel);
	}

	@Override
	public Map<String, StageState> findAll(Collection<String> orderIds, Stage stage) {
		if (orderIds.isEmpty()) {
			return Map.of();
		}
		return this.em
			.createQuery("select s from OrderStageEntity s where s.stage = :stage and s.orderId in :ids",
					OrderStageEntity.class)
			.setParameter("stage", stage.name())
			.setParameter("ids", orderIds)
			.getResultStream()
			.map(JpaStageStore::toModel)
			.collect(Collectors.toMap(StageState::orderId, Function.identity()));
	}

	@Override
	public void save(StageState s) {
		OrderStageEntity entity = this.em.find(OrderStageEntity.class,
				new OrderStageEntity.Key(s.orderId(), s.stage().name()));
		boolean isNew = (entity == null);
		if (isNew) {
			entity = new OrderStageEntity(s.orderId(), s.stage().name());
		}
		// persist 전에 값을 채운다. Hibernate는 persist 시점의 값으로 INSERT한다
		entity.update(s.attempts(), s.nextAttemptAt(), s.reachedFailed(), s.lastError(), s.artifact(), s.updatedAt());
		if (isNew) {
			this.em.persist(entity);
		}
	}

	private static StageState toModel(OrderStageEntity e) {
		return new StageState(e.orderId(), Stage.valueOf(e.stage()), e.attempts(), e.nextAttemptAt(),
				e.reachedFailed(), e.lastError(), e.artifact(), e.updatedAt());
	}

}
