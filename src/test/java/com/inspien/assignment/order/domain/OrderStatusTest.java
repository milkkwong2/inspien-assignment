package com.inspien.assignment.order.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

	@Test
	void readsOnlyNAndY() {
		assertThat(OrderStatus.fromDb("N")).isEqualTo(OrderStatus.N);
		assertThat(OrderStatus.fromDb("Y")).isEqualTo(OrderStatus.Y);
	}

	@Test
	void unknownDatabaseValueStopsInsteadOfBeingSkipped() {
		// 모르는 값을 조용히 대상에서 빼면 그 주문은 아무도 처리하지 않는다 (설계서 §4.1)
		assertThatThrownBy(() -> OrderStatus.fromDb("RECEIVED")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> OrderStatus.fromDb("n")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> OrderStatus.fromDb(null)).isInstanceOf(IllegalStateException.class);
	}

}
