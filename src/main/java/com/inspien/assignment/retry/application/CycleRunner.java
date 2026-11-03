package com.inspien.assignment.retry.application;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

/**
 * 스케줄러 주기(영수증·배송·마감)의 골격: 대상 조회, 주문마다 MDC의 orderId, 주기를 끝내는 실패와 그 로그.
 *
 * <p>주기를 끝내는 기준을 한 곳에 둔다. 쇼핑몰 Oracle·H2 장애면 남은 주문도 같은 이유로 실패하므로 그 주기를 끝내고
 * (D-10, D-22, D-26), DB 예외가 아닌 오류도 스케줄러를 멈추지 않고 그 주기만 끝내되 코드 결함일 수 있다는 로그로 따로
 * 남긴다(D-22 2026-11-04 보완). 서비스마다 복사해 두면 한쪽만 고쳐져 기준이 갈라진다.
 *
 * <p>상속할 기반 클래스가 아니라 서비스가 주입받아 쓰는 부품이다. 연결 상태(ConnectionWatch)와 주문 하나의 처리는 각
 * 서비스에 둔다. 스케줄러 스레드 하나에서만 쓴다.
 */
@Component
public class CycleRunner {

	private static final Logger log = LoggerFactory.getLogger(CycleRunner.class);

	private static final String ORDER_ID = "orderId";

	/**
	 * 처리할 주문을 고른다. 고르다 실패하면 로그를 남기고 빈 목록을 돌려준다(그 주기를 끝낸다).
	 *
	 * @param cycleName 로그에 쓰는 주기 이름 (예: 영수증)
	 */
	public List<String> selectTargets(String cycleName, Supplier<List<String>> selector) {
		try {
			return selector.get();
		}
		catch (DataAccessException | TransactionException ex) {
			log.error("쇼핑몰 DB 또는 H2 장애로 {} 주기를 끝냅니다", cycleName, ex);
			return List.of();
		}
		catch (RuntimeException ex) {
			log.error("예상하지 못한 오류로 {} 주기를 끝냅니다. 코드 결함일 수 있습니다", cycleName, ex);
			return List.of();
		}
	}

	/**
	 * 주문마다 처리하고, END_CYCLE이면 남은 주문을 두고 멈춘다. 처리 중 예외는 END_CYCLE로 본다. 완료를 못 적었어도 다음
	 * 주기의 사전 확인(FTP 목록, SHIPMENT_TB 조회)이 이어서 처리한다 (D-22, D-26).
	 */
	public void forEachOrder(String cycleName, List<String> orderIds, Function<String, NextStep> work) {
		for (String orderId : orderIds) {
			if (runOne(cycleName, orderId, work) == NextStep.END_CYCLE) {
				break;
			}
		}
	}

	/**
	 * 고르기부터 모든 주문의 처리까지 한 번에 하고, 어디서든 실패하면 그 주기를 끝낸다. 주문별 처리가 주기를 끝낼 일이
	 * 없고(외부 연결 없음) 남기는 진행 상태가 없어, 다음 주기에 같은 조건으로 다시 찾으면 되는 주기(마감)에 쓴다.
	 */
	public void selectAndRunAll(String cycleName, Supplier<List<String>> selector, Consumer<String> work) {
		try {
			List<String> orderIds = selector.get();
			for (String orderId : orderIds) {
				withOrderId(orderId, work);
			}
		}
		catch (DataAccessException | TransactionException ex) {
			log.error("쇼핑몰 DB 또는 H2 장애로 {} 주기를 끝냅니다. 다음 주기에 같은 조건으로 다시 찾습니다", cycleName, ex);
		}
		catch (RuntimeException ex) {
			log.error("예상하지 못한 오류로 {} 주기를 끝냅니다. 코드 결함일 수 있습니다. "
					+ "다음 주기에 같은 조건으로 다시 찾습니다", cycleName, ex);
		}
	}

	/**
	 * 로그는 MDC를 지우기 전에 남겨 어느 주문에서 끝났는지 보이게 한다.
	 */
	private NextStep runOne(String cycleName, String orderId, Function<String, NextStep> work) {
		MDC.put(ORDER_ID, orderId);
		try {
			return work.apply(orderId);
		}
		catch (DataAccessException | TransactionException ex) {
			log.error("쇼핑몰 DB 또는 H2 장애로 {} 주기를 끝냅니다. 다음 주기에 사전 확인으로 이어서 처리합니다", cycleName, ex);
			return NextStep.END_CYCLE;
		}
		catch (RuntimeException ex) {
			log.error("예상하지 못한 오류로 {} 주기를 끝냅니다. 코드 결함일 수 있습니다. "
					+ "다음 주기에 사전 확인으로 이어서 처리합니다", cycleName, ex);
			return NextStep.END_CYCLE;
		}
		finally {
			MDC.remove(ORDER_ID);
		}
	}

	private static void withOrderId(String orderId, Consumer<String> work) {
		MDC.put(ORDER_ID, orderId);
		try {
			work.accept(orderId);
		}
		finally {
			MDC.remove(ORDER_ID);
		}
	}

}
