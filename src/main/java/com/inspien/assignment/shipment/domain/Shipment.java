package com.inspien.assignment.shipment.domain;

import java.util.Objects;

import com.inspien.assignment.order.domain.Order;

/**
 * 운송회사 DB SHIPMENT_TB 1행 = 주문 1건의 배송 (설계서 §4.1, §5.3).
 *
 * @param shipmentId 미리 배정한 배송번호 (A001~Z999, ORDER_ID와 별도 시퀀스)
 * @param orderId 주문번호
 * @param itemId 상품 ID
 * @param address 배송 주소
 */
public record Shipment(String shipmentId, String orderId, String itemId, String address) {

	public static Shipment of(String shipmentId, Order order) {
		return new Shipment(shipmentId, order.orderId(), order.itemId(), order.address());
	}

	/**
	 * 같은 배송인가. PK 충돌 뒤 다시 조회한 행이 앞 시도가 늦게 커밋한 것인지 볼 때 쓴다 (D-03 보완, D-19).
	 */
	public boolean sameAs(Shipment other) {
		return Objects.equals(this.orderId, other.orderId) && Objects.equals(this.itemId, other.itemId)
				&& Objects.equals(this.address, other.address);
	}

}
