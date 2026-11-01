package com.inspien.assignment.order.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.inspien.assignment.global.transaction.OrderTx;
import com.inspien.assignment.order.application.port.OrderStore;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.order.domain.OrderStatus;

/**
 * 다른 도메인이 ORDER_TB에 닿는 유일한 길 (D-21). 상태 변경은 조건부 갱신으로만 나간다 (D-06).
 */
@Service
public class OrderService {

	private final OrderStore store;

	public OrderService(OrderStore store) {
		this.store = store;
	}

	/**
	 * 수신 메시지 하나의 주문 전체를 한 트랜잭션으로 저장한다 (D-05).
	 */
	@OrderTx
	public void insertAll(List<Order> orders) {
		this.store.insertAll(orders);
	}

	@OrderTx(readOnly = true)
	public List<Order> findByIds(Collection<String> orderIds) {
		return this.store.findByIds(orderIds);
	}

	@OrderTx(readOnly = true)
	public Optional<Order> findById(String orderId) {
		return this.store.findById(orderId);
	}

	@OrderTx(readOnly = true)
	public List<String> findIdsByStatus(OrderStatus status) {
		return this.store.findIdsByStatus(status);
	}

	/**
	 * 현재 상태가 from일 때만 to로 바꾼다.
	 *
	 * @return 바꿨으면 true. 다른 상태였으면 false (호출한 쪽이 STATE_MISMATCH로 남긴다)
	 */
	@OrderTx
	public boolean moveStatus(String orderId, OrderStatus from, OrderStatus to) {
		if (!from.canMoveTo(to)) {
			throw new IllegalArgumentException("허용하지 않는 상태 전이: " + from + " → " + to);
		}
		return this.store.updateStatus(orderId, from, to) == 1;
	}

}
