package com.inspien.assignment.verification;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * 검증용 엔티티: 복합 PK의 한 컬럼(APPLICANT_KEY)에 @TenantId를 붙인다 (D-25).
 */
@Entity
@Table(name = "ORDER_TB")
@IdClass(ProbeOrder.Key.class)
public class ProbeOrder {

	@Id
	@Column(name = "ORDER_ID")
	String orderId;

	@Id
	@TenantId
	@Column(name = "APPLICANT_KEY")
	String applicantKey;

	@Column(name = "NAME")
	String name;

	@Column(name = "STATUS")
	String status;

	protected ProbeOrder() {
	}

	public ProbeOrder(String orderId, String name, String status) {
		this.orderId = orderId;
		this.name = name;
		this.status = status;
	}

	public static class Key implements Serializable {

		String orderId;

		String applicantKey;

		public Key() {
		}

		public Key(String orderId, String applicantKey) {
			this.orderId = orderId;
			this.applicantKey = applicantKey;
		}

		@Override
		public boolean equals(Object object) {
			return object instanceof Key other && Objects.equals(this.orderId, other.orderId)
					&& Objects.equals(this.applicantKey, other.applicantKey);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.orderId, this.applicantKey);
		}

	}

}
