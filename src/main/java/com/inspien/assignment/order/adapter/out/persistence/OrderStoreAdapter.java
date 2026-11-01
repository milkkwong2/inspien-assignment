package com.inspien.assignment.order.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import com.inspien.assignment.numbering.application.port.IssuedIdReader;
import com.inspien.assignment.numbering.domain.IdSequence;
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
class OrderStoreAdapter implements OrderStore, IssuedIdReader {

	private static final QOrderEntity order = QOrderEntity.orderEntity;

	@PersistenceContext(unitName = "order")
	private EntityManager em;

	private final JPAQueryFactory query;

	OrderStoreAdapter(@Qualifier("orderQueryFactory") JPAQueryFactory query) {
		this.query = query;
	}

	@Override
	public void insertAll(List<Order> orders) {
		for (Order o : orders) {
			this.em.persist(new OrderEntity(o.orderId(), o.userId(), o.itemId(), o.name(), o.address(), o.itemName(),
					o.price(), o.status().name()));
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
			.map(OrderStoreAdapter::toModel)
			.toList();
	}

	@Override
	public Optional<Order> findById(String orderId) {
		return Optional.ofNullable(this.query.selectFrom(order).where(order.orderId.eq(orderId)).fetchOne())
			.map(OrderStoreAdapter::toModel);
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
	public long updateStatus(String orderId, OrderStatus expected, OrderStatus next) {
		// 엔티티 변경 감지로 갱신하면 "AND STATUS = 현재" 조건이 빠진다. 벌크 UPDATE로 하고 건수를 본다 (D-06)
		return this.query.update(order)
			.set(order.status, next.name())
			.where(order.orderId.eq(orderId), order.status.eq(expected.name()))
			.execute();
	}

	@Override
	public IdSequence sequence() {
		return IdSequence.ORDER;
	}

	@Override
	public List<String> issuedIds() {
		return this.query.select(order.orderId).from(order).fetch();
	}

	private static Order toModel(OrderEntity e) {
		OrderStatus status = OrderStatus.parse(e.status())
			.orElseThrow(() -> new IllegalStateException("알 수 없는 STATUS 값: " + e.orderId() + "=" + e.status()));
		return new Order(e.orderId(), e.userId(), e.itemId(), e.name(), e.address(), e.itemName(), e.price(), status);
	}

}
