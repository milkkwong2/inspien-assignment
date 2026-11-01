package com.inspien.assignment.order;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.inspien.assignment.global.config.AppProperties;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.order.domain.OrderStatus;
import com.inspien.assignment.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 다른 지원자의 행이 든 공용 테이블에서 내 행만 다루는지 확인한다 (D-01, D-25).
 */
@IntegrationTest
class OrderServiceIntegrationTest {

	@Autowired
	OrderService orders;

	@Autowired
	AppProperties properties;

	@Autowired
	@Qualifier("orderDataSource")
	DataSource dataSource;

	@Test
	void insertsWithMyKeyAlongsideOtherApplicantsSameOrderId() {
		this.orders.insertAll(List.of(order("A001"), order("A002")));

		JdbcTemplate jdbc = new JdbcTemplate(this.dataSource);
		assertThat(jdbc.queryForList("SELECT APPLICANT_KEY FROM ORDER_TB WHERE ORDER_ID = 'A001'", String.class))
			.contains("OTHER-APPLICANT-1", "OTHER-APPLICANT-2", this.properties.applicant().key());
		assertThat(this.orders.findByIds(List.of("A001", "A002"))).extracting(Order::name)
			.containsExactly("김민수", "김민수");
	}

	@Test
	void duplicateOrderIdFailsInsteadOfOverwriting() {
		this.orders.insertAll(List.of(order("A010")));

		assertThatThrownBy(() -> this.orders.insertAll(List.of(order("A011"), order("A010"))))
			.isInstanceOf(DataIntegrityViolationException.class);
		// 묶음 전체가 롤백된다 (D-05)
		assertThat(this.orders.findById("A011")).isEmpty();
	}

	@Test
	void seesOnlyMyOrdersByStatus() {
		this.orders.insertAll(List.of(order("A020"), order("A021")));

		// 다른 지원자의 RECEIVED 주문이 150건 넘게 있지만 내 것만 나온다
		assertThat(this.orders.findIdsByStatus(OrderStatus.RECEIVED)).contains("A020", "A021")
			.doesNotContain("A150");
	}

	@Test
	void moveStatusIsConditionalAndLeavesOthersAlone() {
		this.orders.insertAll(List.of(order("A003")));

		assertThat(this.orders.moveStatus("A003", OrderStatus.RECEIPT_SENT, OrderStatus.SHIPPED)).as("현재 상태가 다름")
			.isFalse();
		assertThat(this.orders.moveStatus("A003", OrderStatus.RECEIVED, OrderStatus.RECEIPT_SENT)).isTrue();
		assertThat(this.orders.findById("A003")).get().extracting(Order::status).isEqualTo(OrderStatus.RECEIPT_SENT);

		JdbcTemplate jdbc = new JdbcTemplate(this.dataSource);
		assertThat(jdbc.queryForObject(
				"SELECT STATUS FROM ORDER_TB WHERE ORDER_ID = 'A003' AND APPLICANT_KEY = 'OTHER-APPLICANT-1'",
				String.class))
			.isEqualTo("RECEIVED");
	}

	@Test
	void reportsContentDifferenceForReprocessing() {
		this.orders.insertAll(List.of(order("A030")));
		Order stored = this.orders.findById("A030").orElseThrow();

		assertThat(stored.sameContentAs(order("A030"))).isTrue();
		assertThat(stored.sameContentAs(Order.received("A030", "USER01", "ITEM10", "김민수", "다른 주소", "노트북 거치대, 블랙",
				"32000")))
			.isFalse();
	}

	static Order order(String orderId) {
		return Order.received(orderId, "USER01", "ITEM10", "김민수", "서울시 강남구", "노트북 거치대, 블랙", "32000");
	}

}
