package com.inspien.assignment.order.adapter.out.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * ORDER_TB 행. 복합 PK (ORDER_ID, APPLICANT_KEY).
 *
 * <p>APPLICANT_KEY는 @TenantId라 Hibernate가 모든 조회·갱신 조건과 INSERT 값에 내 키를 넣는다 (D-25).
 *
 * <p>CREATE_TIME은 매핑하지 않는다. INSERT에서 빠져 DB 기본값(SYSDATE)이 들어간다 (D-26, D-27).
 */
@Entity
@Table(name = "ORDER_TB")
@IdClass(OrderEntity.Key.class)
class OrderEntity {

	@Id
	@Column(name = "ORDER_ID")
	private String orderId;

	@Id
	@TenantId
	@Column(name = "APPLICANT_KEY")
	private String applicantKey;

	@Column(name = "USER_ID")
	private String userId;

	@Column(name = "ITEM_ID")
	private String itemId;

	@Column(name = "NAME")
	private String name;

	@Column(name = "ADDRESS")
	private String address;

	@Column(name = "ITEM_NAME")
	private String itemName;

	@Column(name = "PRICE")
	private String price;

	@Column(name = "STATUS")
	private String status;

	protected OrderEntity() {
	}

	OrderEntity(String orderId, String userId, String itemId, String name, String address, String itemName,
			String price, String status) {
		this.orderId = orderId;
		this.userId = userId;
		this.itemId = itemId;
		this.name = name;
		this.address = address;
		this.itemName = itemName;
		this.price = price;
		this.status = status;
	}

	String orderId() {
		return this.orderId;
	}

	String userId() {
		return this.userId;
	}

	String itemId() {
		return this.itemId;
	}

	String name() {
		return this.name;
	}

	String address() {
		return this.address;
	}

	String itemName() {
		return this.itemName;
	}

	String price() {
		return this.price;
	}

	String status() {
		return this.status;
	}

	static class Key implements Serializable {

		private String orderId;

		private String applicantKey;

		Key() {
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Key other && Objects.equals(this.orderId, other.orderId)
					&& Objects.equals(this.applicantKey, other.applicantKey);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.orderId, this.applicantKey);
		}

	}

}
