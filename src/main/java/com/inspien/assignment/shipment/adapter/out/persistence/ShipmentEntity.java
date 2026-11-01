package com.inspien.assignment.shipment.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * SHIPMENT_TB 행. 복합 PK (SHIPMENT_ID, APPLICANT_KEY). APPLICANT_KEY는 @TenantId (D-25).
 */
@Entity
@Table(name = "SHIPMENT_TB")
@IdClass(ShipmentEntity.Key.class)
class ShipmentEntity {

	@Id
	@Column(name = "SHIPMENT_ID")
	private String shipmentId;

	@Id
	@TenantId
	@Column(name = "APPLICANT_KEY")
	private String applicantKey;

	@Column(name = "ORDER_ID")
	private String orderId;

	@Column(name = "ITEM_ID")
	private String itemId;

	@Column(name = "ADDRESS")
	private String address;

	protected ShipmentEntity() {
	}

	ShipmentEntity(String shipmentId, String orderId, String itemId, String address) {
		this.shipmentId = shipmentId;
		this.orderId = orderId;
		this.itemId = itemId;
		this.address = address;
	}

	String shipmentId() {
		return this.shipmentId;
	}

	String orderId() {
		return this.orderId;
	}

	String itemId() {
		return this.itemId;
	}

	String address() {
		return this.address;
	}

	static class Key implements Serializable {

		private String shipmentId;

		private String applicantKey;

		Key() {
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Key other && Objects.equals(this.shipmentId, other.shipmentId)
					&& Objects.equals(this.applicantKey, other.applicantKey);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.shipmentId, this.applicantKey);
		}

	}

}
