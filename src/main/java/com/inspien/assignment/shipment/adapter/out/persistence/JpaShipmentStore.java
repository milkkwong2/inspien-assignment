package com.inspien.assignment.shipment.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import com.inspien.assignment.shipment.application.port.ShipmentStore;
import com.inspien.assignment.shipment.domain.Shipment;

/**
 * SHIPMENT_TB 어댑터. APPLICANT_KEY는 @TenantId로 Hibernate가 붙인다 (D-25). INSERT는 persist + flush라 같은 PK를
 * 덮어쓰지 않는다.
 */
@Repository
class JpaShipmentStore implements ShipmentStore {

	private static final QShipmentEntity shipment = QShipmentEntity.shipmentEntity;

	@PersistenceContext(unitName = "shipment")
	private EntityManager em;

	private final JPAQueryFactory query;

	JpaShipmentStore(@Qualifier("shipmentQueryFactory") JPAQueryFactory query) {
		this.query = query;
	}

	@Override
	public Optional<Shipment> findByOrderId(String orderId) {
		return this.query.selectFrom(shipment)
			.where(shipment.orderId.eq(orderId))
			.orderBy(shipment.shipmentId.asc())
			.fetch()
			.stream()
			.findFirst()
			.map(JpaShipmentStore::toModel);
	}

	@Override
	public Optional<Shipment> findById(String shipmentId) {
		ShipmentEntity found = this.query.selectFrom(shipment)
			.where(shipment.shipmentId.eq(shipmentId))
			.fetchOne();
		return Optional.ofNullable(found)
			.map(JpaShipmentStore::toModel);
	}

	@Override
	public void insert(Shipment model) {
		this.em.persist(new ShipmentEntity(model.shipmentId(), model.orderId(), model.itemId(), model.address()));
		this.em.flush();
	}

	@Override
	public List<String> issuedIds() {
		return this.query.select(shipment.shipmentId).from(shipment).fetch();
	}

	private static Shipment toModel(ShipmentEntity entity) {
		return new Shipment(entity.shipmentId(), entity.orderId(), entity.itemId(), entity.address());
	}

}
