package com.inspien.assignment.order.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.inspien.assignment.global.transaction.OrderTx;
import com.inspien.assignment.numbering.application.port.IssuedIdReader;
import com.inspien.assignment.numbering.domain.IdSequence;
import com.inspien.assignment.order.application.port.OrderStore;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.order.domain.OrderStatus;

/**
 * 다른 도메인이 ORDER_TB에 닿는 유일한 길 (D-21). STATUS를 바꾸는 것은 마감(N→Y 조건부) 하나뿐이다 (D-26).
 *
 * <p>채번기의 포트 {@link IssuedIdReader}도 여기서 구현한다. 채번기는 order를 모르고(D-21), 기동 시 동기화의 Oracle 읽기는
 * 다른 읽기처럼 쇼핑몰 DB의 읽기 전용 트랜잭션에서 돈다.
 */
@Service
public class OrderService implements IssuedIdReader {

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

	@OrderTx(readOnly = true)
	public List<String> findIdsByStatusAfter(OrderStatus status, String after) {
		return this.store.findIdsByStatusAfter(status, after);
	}

	/**
	 * STATUS가 N일 때만 Y로 바꾼다. 쓰는 곳은 마감 하나라 0건(이미 Y)은 정상이다 (D-26).
	 *
	 * @return 바꿨으면 true
	 */
	@OrderTx
	public boolean close(String orderId) {
		return this.store.close(orderId) == 1;
	}

	@Override
	@OrderTx(readOnly = true)
	public IssuedIds issuedIds() {
		return new IssuedIds(IdSequence.ORDER, this.store.issuedIds());
	}

}
