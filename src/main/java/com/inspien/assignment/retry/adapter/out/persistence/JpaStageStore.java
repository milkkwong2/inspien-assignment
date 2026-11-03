package com.inspien.assignment.retry.adapter.out.persistence;

import java.time.OffsetDateTime;
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
		OrderStageEntity found = this.em.find(OrderStageEntity.class, new OrderStageEntity.Key(orderId, stage.name()));
		return Optional.ofNullable(found)
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
			.getResultList()
			.stream()
			.map(JpaStageStore::toModel)
			.collect(Collectors.toMap(StageState::orderId, Function.identity()));
	}

	@Override
	public void save(StageState state) {
		OrderStageEntity entity = this.em.find(OrderStageEntity.class,
				new OrderStageEntity.Key(state.orderId(), state.stage().name()));
		boolean isNew = (entity == null);
		if (isNew) {
			entity = new OrderStageEntity(state.orderId(), state.stage().name());
		}
		// persist 전에 값을 채운다. Hibernate는 persist 시점의 값으로 INSERT한다
		entity.update(state.attempts(), state.nextAttemptAt(), state.reachedFailed(), state.lastError(),
				state.artifact(), state.completedAt());
		if (isNew) {
			this.em.persist(entity);
		}
	}

	@Override
	public boolean restartIfFailed(String orderId, Stage stage) {
		// 벌크 UPDATE는 엔티티 콜백을 거치지 않으므로 UPDATED_AT을 직접 넣는다 (D-27)
		return this.em
			.createQuery("update OrderStageEntity s set s.attempts = 0, s.reachedFailed = false, s.nextAttemptAt = null,"
					+ " s.updatedAt = :now where s.orderId = :orderId and s.stage = :stage and s.reachedFailed = true")
			.setParameter("now", OffsetDateTime.now())
			.setParameter("orderId", orderId)
			.setParameter("stage", stage.name())
			.executeUpdate() == 1;
	}

	private static StageState toModel(OrderStageEntity entity) {
		return new StageState(entity.orderId(), Stage.valueOf(entity.stage()), entity.attempts(),
				entity.nextAttemptAt(), entity.reachedFailed(), entity.lastError(), entity.artifact(),
				entity.completedAt());
	}

}
