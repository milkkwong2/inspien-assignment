package com.inspien.assignment.order.domain;

import java.util.Objects;

/**
 * 정규 모델: 주문 1건 = 상품 1개 = ORDER_TB 1행 (설계서 §4.1, D-07, D-21).
 *
 * <p>앱 안에서는 주문을 이 모델로만 다룬다. XML, ORDER_TB 행, CSV, SHIPMENT_TB 행 같은 바깥 형식은 어댑터가 바꾼다.
 * APPLICANT_KEY는 Oracle 어댑터가 붙이므로 여기에는 없다.
 *
 * @param orderId 주문번호 (A001~Z999)
 * @param userId 주문자 ID (HEADER와 ITEM을 잇는 값)
 * @param itemId 상품 ID
 * @param name 주문자 이름 (HEADER)
 * @param address 주소 (HEADER)
 * @param itemName 상품명
 * @param price 가격 (숫자 문자열)
 * @param status 처리 상태
 */
public record Order(String orderId, String userId, String itemId, String name, String address, String itemName,
		String price, OrderStatus status) {

	public Order {
		Objects.requireNonNull(orderId, "orderId");
		Objects.requireNonNull(status, "status");
	}

	/**
	 * 수신해 새로 저장할 주문.
	 */
	public static Order received(String orderId, String userId, String itemId, String name, String address,
			String itemName, String price) {
		return new Order(orderId, userId, itemId, name, address, itemName, price, OrderStatus.RECEIVED);
	}

	/**
	 * 같은 주문 내용인가. 재처리 때 미리 배정한 번호로 조회한 행이 원본과 같은지 볼 때 쓴다 (D-16, D-19).
	 * 상태는 비교하지 않는다. 그사이 스케줄러가 처리를 진행했을 수 있다.
	 */
	public boolean sameContentAs(Order other) {
		return this.orderId.equals(other.orderId) && Objects.equals(this.userId, other.userId)
				&& Objects.equals(this.itemId, other.itemId) && Objects.equals(this.name, other.name)
				&& Objects.equals(this.address, other.address) && Objects.equals(this.itemName, other.itemName)
				&& Objects.equals(this.price, other.price);
	}

}
