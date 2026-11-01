package com.inspien.assignment.order.domain;

import org.junit.jupiter.api.Test;

import static com.inspien.assignment.order.domain.OrderStatus.RECEIPT_FAILED;
import static com.inspien.assignment.order.domain.OrderStatus.RECEIPT_SENT;
import static com.inspien.assignment.order.domain.OrderStatus.RECEIVED;
import static com.inspien.assignment.order.domain.OrderStatus.SHIPMENT_FAILED;
import static com.inspien.assignment.order.domain.OrderStatus.SHIPPED;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

	@Test
	void receiptComesBeforeShipment() {
		assertThat(RECEIVED.canMoveTo(RECEIPT_SENT)).isTrue();
		assertThat(RECEIVED.canMoveTo(RECEIPT_FAILED)).isTrue();
		assertThat(RECEIPT_SENT.canMoveTo(SHIPPED)).isTrue();
		assertThat(RECEIPT_SENT.canMoveTo(SHIPMENT_FAILED)).isTrue();

		assertThat(RECEIVED.canMoveTo(SHIPPED)).as("영수증 없이 배송").isFalse();
		assertThat(SHIPPED.canMoveTo(RECEIVED)).isFalse();
	}

	@Test
	void appDoesNotResetFailedOrders() {
		// 되돌림은 운영자가 Oracle에서 직접 한다 (D-10)
		assertThat(RECEIPT_FAILED.canMoveTo(RECEIVED)).isFalse();
		assertThat(SHIPMENT_FAILED.canMoveTo(RECEIPT_SENT)).isFalse();
	}

	@Test
	void unknownDatabaseValueIsEmpty() {
		assertThat(OrderStatus.parse("SHIPPED")).contains(SHIPPED);
		assertThat(OrderStatus.parse("shipped")).isEmpty();
		assertThat(OrderStatus.parse(null)).isEmpty();
	}

}
