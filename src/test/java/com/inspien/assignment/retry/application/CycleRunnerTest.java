package com.inspien.assignment.retry.application;

import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러 주기의 골격: 어디서 멈추는지, 주문마다 MDC, 주기를 끝내는 로그 문구.
 */
class CycleRunnerTest {

	final CycleRunner cycles = new CycleRunner();

	final List<String> processed = new ArrayList<>();

	final List<String> mdcDuringWork = new ArrayList<>();

	final ListAppender<ILoggingEvent> logs = new ListAppender<>();

	final Logger logger = (Logger) LoggerFactory.getLogger(CycleRunner.class);

	@BeforeEach
	void captureLogs() {
		this.logs.start();
		this.logger.addAppender(this.logs);
	}

	@AfterEach
	void releaseLogs() {
		this.logger.detachAppender(this.logs);
	}

	@Test
	void continuesOnNextOrderAndStopsOnEndCycle() {
		this.cycles.forEachOrder("배송", List.of("A001", "A002", "A003"), orderId -> {
			this.processed.add(orderId);
			return orderId.equals("A002") ? NextStep.END_CYCLE : NextStep.NEXT_ORDER;
		});

		assertThat(this.processed).containsExactly("A001", "A002");
		assertThat(this.logs.list).isEmpty();
	}

	@Test
	void dbFailureEndsCycleWithOrderIdInLog() {
		this.cycles.forEachOrder("영수증", List.of("A001", "A002"), orderId -> {
			this.processed.add(orderId);
			throw new DataAccessResourceFailureException("x");
		});

		assertThat(this.processed).containsExactly("A001");
		assertThat(this.logs.list).singleElement().satisfies(event -> {
			assertThat(event.getFormattedMessage())
				.isEqualTo("쇼핑몰 DB 또는 H2 장애로 영수증 주기를 끝냅니다. 다음 주기에 사전 확인으로 이어서 처리합니다");
			assertThat(event.getMDCPropertyMap()).containsEntry("orderId", "A001");
			assertThat(event.getThrowableProxy()).isNotNull();
		});
		assertThat(MDC.get("orderId")).isNull();
	}

	@Test
	void unexpectedFailureEndsCycleAsPossibleDefect() {
		this.cycles.forEachOrder("배송", List.of("A001", "A002"), orderId -> {
			this.processed.add(orderId);
			throw new IllegalStateException("x");
		});

		assertThat(this.processed).containsExactly("A001");
		assertThat(this.logs.list).singleElement()
			.extracting(ILoggingEvent::getFormattedMessage)
			.isEqualTo("예상하지 못한 오류로 배송 주기를 끝냅니다. 코드 결함일 수 있습니다. 다음 주기에 사전 확인으로 이어서 처리합니다");
		assertThat(MDC.get("orderId")).isNull();
	}

	@Test
	void putsOrderIdInMdcOnlyWhileWorking() {
		this.cycles.forEachOrder("배송", List.of("A001", "A002"), orderId -> {
			this.mdcDuringWork.add(MDC.get("orderId"));
			return NextStep.NEXT_ORDER;
		});

		assertThat(this.mdcDuringWork).containsExactly("A001", "A002");
		assertThat(MDC.get("orderId")).isNull();
	}

	@Test
	void selectionFailureReturnsNoTargets() {
		List<String> afterDbFailure = this.cycles.selectTargets("영수증", () -> {
			throw new DataAccessResourceFailureException("x");
		});
		List<String> afterDefect = this.cycles.selectTargets("영수증", () -> {
			throw new IllegalStateException("x");
		});

		assertThat(afterDbFailure).isEmpty();
		assertThat(afterDefect).isEmpty();
		assertThat(this.logs.list).extracting(ILoggingEvent::getFormattedMessage)
			.containsExactly("쇼핑몰 DB 또는 H2 장애로 영수증 주기를 끝냅니다",
					"예상하지 못한 오류로 영수증 주기를 끝냅니다. 코드 결함일 수 있습니다");
	}

	@Test
	void selectionReturnsTargetsAsIs() {
		assertThat(this.cycles.selectTargets("배송", () -> List.of("A001", "A002"))).containsExactly("A001", "A002");
	}

	/**
	 * 마감: 주문별 처리가 실패해도 주기를 끝낸다. 진행 상태가 없어 다음 주기에 같은 조건으로 다시 찾는다.
	 */
	@Test
	void runAllEndsCycleOnAnyFailure() {
		this.cycles.selectAndRunAll("마감", () -> List.of("A001", "A002", "A003"), orderId -> {
			this.processed.add(orderId);
			this.mdcDuringWork.add(MDC.get("orderId"));
			if (orderId.equals("A002")) {
				throw new DataAccessResourceFailureException("x");
			}
		});
		this.cycles.selectAndRunAll("마감", () -> {
			throw new IllegalStateException("x");
		}, this.processed::add);

		assertThat(this.processed).containsExactly("A001", "A002");
		assertThat(this.mdcDuringWork).containsExactly("A001", "A002");
		assertThat(MDC.get("orderId")).isNull();
		assertThat(this.logs.list).extracting(ILoggingEvent::getFormattedMessage)
			.containsExactly("쇼핑몰 DB 또는 H2 장애로 마감 주기를 끝냅니다. 다음 주기에 같은 조건으로 다시 찾습니다",
					"예상하지 못한 오류로 마감 주기를 끝냅니다. 코드 결함일 수 있습니다. 다음 주기에 같은 조건으로 다시 찾습니다");
	}

}
