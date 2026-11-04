package com.inspien.assignment.order.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.order.domain.OrderStatus;

/**
 * 쇼핑몰 Oracle ORDER_TB. 구현은 모든 쿼리 조건과 INSERT 값에 내 APPLICANT_KEY를 붙인다 (D-01, D-25).
 */
public interface OrderStore {

	/**
	 * 새 주문을 INSERT한다. 같은 PK가 있으면 덮어쓰지 않고 DataIntegrityViolationException으로 실패한다 (D-19).
	 */
	void insertAll(List<Order> orders);

	List<Order> findByIds(Collection<String> orderIds);

	Optional<Order> findById(String orderId);

	/**
	 * 이 상태인 주문번호 전부, 오름차순.
	 */
	List<String> findIdsByStatus(OrderStatus status);

	/**
	 * 이 상태인 주문번호 중 after보다 뒤인 것, 오름차순. 목록 다음 페이지용이다.
	 */
	List<String> findIdsByStatusAfter(OrderStatus status, String after);

	/**
	 * 조건부 갱신: STATUS가 N일 때만 Y로 바꾼다 (D-26).
	 *
	 * @return 갱신한 행 수 (0 또는 1)
	 */
	long close(String orderId);

	/**
	 * 내 APPLICANT_KEY 범위의 주문번호 전부. 기동 시 채번 동기화가 쓴다 (D-08).
	 */
	List<String> issuedIds();

}
