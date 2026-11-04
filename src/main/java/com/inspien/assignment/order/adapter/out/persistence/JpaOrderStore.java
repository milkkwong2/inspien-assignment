package com.inspien.assignment.order.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import com.inspien.assignment.order.application.port.OrderStore;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.order.domain.OrderStatus;

/**
 * ORDER_TB 어댑터. APPLICANT_KEY는 엔티티의 @TenantId로 Hibernate가 모든 쿼리와 INSERT에 붙인다 (D-25).
 *
 * <p>INSERT는 Spring Data save()가 아니라 persist다. save()는 ID를 직접 넣은 엔티티를 merge하고, merge는 같은 PK가
 * 있으면 UPDATE해 PK 충돌 없이 덮어쓴다 (설계서 §4.1). @Repository라 예외가 Spring 예외로 바뀐다.
 */
@Repository
class JpaOrderStore implements OrderStore {

	private static final QOrderEntity order = QOrderEntity.orderEntity;

	@PersistenceContext(unitName = "order")
	private EntityManager em;

	private final JPAQueryFactory query;

	JpaOrderStore(@Qualifier("orderQueryFactory") JPAQueryFactory query) {
		this.query = query;
	}

	@Override
	public void insertAll(List<Order> orders) {
		for (Order model : orders) {
			this.em.persist(new OrderEntity(model.orderId(), model.userId(), model.itemId(), model.name(),
					model.address(), model.itemName(), model.price(), model.status().name()));
		}
		// PK 충돌을 커밋 전에 여기서 드러낸다
		this.em.flush();
	}

	@Override
	public List<Order> findByIds(Collection<String> orderIds) {
		if (orderIds.isEmpty()) {
			return List.of();
		}
		return this.query.selectFrom(order)
			.where(order.orderId.in(orderIds))
			.orderBy(order.orderId.asc())
			.fetch()
			.stream()
			.map(JpaOrderStore::toModel)
			.toList();
	}

	@Override
	public Optional<Order> findById(String orderId) {
		OrderEntity found = this.query.selectFrom(order)
			.where(order.orderId.eq(orderId))
			.fetchOne();
		return Optional.ofNullable(found)
			.map(JpaOrderStore::toModel);
	}

	@Override
	public List<String> findIdsByStatus(OrderStatus status) {
		return this.query.select(order.orderId)
			.from(order)
			.where(order.status.eq(status.name()))
			.orderBy(order.orderId.asc())
			.fetch();
	}

	@Override
	public List<String> findIdsByStatusAfter(OrderStatus status, String after) {
		return this.query.select(order.orderId)
			.from(order)
			.where(order.status.eq(status.name()), order.orderId.gt(after))
			.orderBy(order.orderId.asc())
			.fetch();
	}

	@Override
	public long close(String orderId) {
		// 엔티티 변경 감지로 갱신하면 "AND STATUS = 'N'" 조건이 빠진다. 벌크 UPDATE로 하고 건수를 본다 (설계서 §4.1)
		return this.query.update(order)
			.set(order.status, OrderStatus.Y.name())
			.where(order.orderId.eq(orderId), order.status.eq(OrderStatus.N.name()))
			.execute();
	}

	@Override
	public List<String> issuedIds() {
		return this.query.select(order.orderId).from(order).fetch();
	}

	private static Order toModel(OrderEntity entity) {
		return new Order(entity.orderId(), entity.userId(), entity.itemId(), entity.name(), entity.address(),
				entity.itemName(), entity.price(), OrderStatus.fromDb(entity.status()));
	}

}
