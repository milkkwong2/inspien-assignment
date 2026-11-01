package com.inspien.assignment.numbering;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import com.inspien.assignment.numbering.application.NumberingService;
import com.inspien.assignment.numbering.domain.IdSequence;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시퀀스 동기화 (설계서 §7.3, D-08). H2가 비어 있어도 이미 쓴 번호를 다시 주지 않는다.
 */
@IntegrationTest
class NumberingIntegrationTest {

	@Autowired
	NumberingService numbering;

	@Autowired
	OrderService orders;

	@Test
	void synchronizesToMyMaxButIgnoresOtherApplicants() {
		// 다른 지원자는 A150까지 썼지만 내 번호와는 무관하다. 기동 동기화 후 처음 받는 번호는 A001
		String first = this.numbering.next(IdSequence.ORDER);
		assertThat(first).isEqualTo("A001");

		// H2를 잃고 Oracle에만 B005까지 남은 상황: 다른 경로로 들어간 내 주문
		this.orders.insertAll(List.of(Order.received("B005", "U", "I", "N", "A", "IN", "1")));
		this.numbering.synchronizeWithIssuedIds();

		assertThat(this.numbering.next(IdSequence.ORDER)).isEqualTo("B006");
		assertThat(this.numbering.next(IdSequence.ORDER)).isEqualTo("B007");
	}

	@Test
	void synchronizeNeverMovesBackward() {
		this.numbering.next(IdSequence.SHIPMENT);
		String before = this.numbering.next(IdSequence.SHIPMENT);

		this.numbering.synchronizeWithIssuedIds();

		assertThat(this.numbering.next(IdSequence.SHIPMENT)).isGreaterThan(before);
	}

}
