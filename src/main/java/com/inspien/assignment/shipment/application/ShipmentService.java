package com.inspien.assignment.shipment.application;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import com.inspien.assignment.global.config.AppProperties;
import com.inspien.assignment.global.error.DbFailures;
import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.global.event.ProcessEvent.Result;
import com.inspien.assignment.global.event.ProcessEvents;
import com.inspien.assignment.inbound.application.InboundQueryService;
import com.inspien.assignment.numbering.application.NumberingService;
import com.inspien.assignment.numbering.domain.IdSequence;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.order.domain.OrderStatus;
import com.inspien.assignment.retry.application.ConnectionWatch;
import com.inspien.assignment.retry.application.NextStep;
import com.inspien.assignment.retry.application.RetryService;
import com.inspien.assignment.retry.application.StageEvents;
import com.inspien.assignment.retry.domain.Stage;
import com.inspien.assignment.shipment.domain.Shipment;

/**
 * IF-03 배송 (설계서 §5.3, §7.2). STATUS=N이고 배송 단계가 PENDING인 주문을 운송회사 DB에 등록하고 H2에 배송 완료를
 * 적는다. 영수증 단계를 기다리지 않고, STATUS도 바꾸지 않는다(마감 스케줄러 몫) (D-26).
 *
 * <p>두 DB는 한 트랜잭션으로 묶을 수 없어 정합성은 멱등성으로 맞춘다 (D-03).
 * <ul>
 * <li>SHIPMENT_ID는 INSERT 전에 H2에 미리 배정하고 재시도에 재사용한다. 커밋 결과를 모를 때 새 번호로 INSERT하면 앞 커밋이
 * 늦게 끝났을 때 배송이 두 건이 된다. 같은 번호면 PK로 부딪혀 한 건으로 끝난다.</li>
 * <li>INSERT 전에 같은 ORDER_ID의 배송을 조회한다. 있으면 INSERT 없이 배송 완료만 적는다.</li>
 * <li>PK 충돌이면 새 트랜잭션에서 그 번호로 다시 조회해, 같은 배송이면 배송 완료, 다르면 재시도 없이 배송 FAILED.</li>
 * </ul>
 */
@Service
public class ShipmentService {

	private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

	private static final Stage STAGE = Stage.SHIPMENT;

	private final OrderService orders;

	private final InboundQueryService inbound;

	private final RetryService retry;

	private final NumberingService numbering;

	private final ShipmentRegistry registry;

	private final ProcessEvents events;

	private final StageEvents stageEvents;

	private final int batchSize;

	private final ConnectionWatch shipmentDb;

	ShipmentService(OrderService orders, InboundQueryService inbound, RetryService retry, NumberingService numbering,
			ShipmentRegistry registry, ProcessEvents events, StageEvents stageEvents, AppProperties properties) {
		this.orders = orders;
		this.inbound = inbound;
		this.retry = retry;
		this.numbering = numbering;
		this.registry = registry;
		this.events = events;
		this.stageEvents = stageEvents;
		this.batchSize = properties.scheduler().batchSize();
		this.shipmentDb = new ConnectionWatch("운송회사 DB", STAGE, events);
	}

	public void runCycle() {
		List<String> due;
		try {
			List<String> candidates = this.orders.findIdsByStatus(OrderStatus.N);
			due = this.retry.selectDue(STAGE, candidates, this.batchSize);
		}
		catch (DataAccessException | TransactionException ex) {
			log.error("쇼핑몰 DB 또는 H2 장애로 배송 주기를 끝냅니다", ex);
			return;
		}
		catch (RuntimeException ex) {
			log.error("예상하지 못한 오류로 배송 주기를 끝냅니다. 코드 결함일 수 있습니다", ex);
			return;
		}
		for (String orderId : due) {
			if (processOne(orderId) == NextStep.END_CYCLE) {
				break;
			}
		}
	}

	/**
	 * @return 연결이 끊기거나 우리 쪽 저장소가 안 되면 END_CYCLE. 남은 주문도 같은 이유로 실패한다
	 */
	private NextStep processOne(String orderId) {
		MDC.put("orderId", orderId);
		try {
			Optional<Order> found = this.orders.findById(orderId);
			if (found.isEmpty() || found.get().status() != OrderStatus.N) {
				// 대상을 고른 뒤 마감됐다. 이번 주기에는 건드리지 않는다
				return NextStep.NEXT_ORDER;
			}
			if (this.inbound.receivedAt(orderId).isEmpty()) {
				missingRecord(orderId);
				return NextStep.NEXT_ORDER;
			}
			String shipmentId = this.retry.artifactOrAssign(orderId, STAGE,
					() -> this.numbering.next(IdSequence.SHIPMENT));
			return register(Shipment.of(shipmentId, found.get()));
		}
		catch (DataAccessException | TransactionException ex) {
			log.error("쇼핑몰 DB 또는 H2 장애로 배송 주기를 끝냅니다. 다음 주기에 사전 확인으로 이어서 처리합니다", ex);
			return NextStep.END_CYCLE;
		}
		catch (RuntimeException ex) {
			log.error("예상하지 못한 오류로 배송 주기를 끝냅니다. 코드 결함일 수 있습니다. "
					+ "다음 주기에 사전 확인으로 이어서 처리합니다", ex);
			return NextStep.END_CYCLE;
		}
		finally {
			MDC.remove("orderId");
		}
	}

	/**
	 * 운송 DB 작업만 오류 분류 대상이다. H2 완료 기록은 그 밖에서 해, 실패하면 예외가 processOne으로 올라가 주기를 끝낸다.
	 * 분류 안에 두면 H2 장애가 배송 건별 실패로 세지거나 운송 DB 연결 실패로 남는다 (D-22, D-26).
	 */
	private NextStep register(Shipment shipment) {
		String orderId = shipment.orderId();
		Optional<Shipment> existing;
		try {
			existing = this.registry.findByOrderId(orderId);
			this.shipmentDb.connected();
			if (existing.isEmpty()) {
				this.registry.insert(shipment);
			}
		}
		catch (DataIntegrityViolationException ex) {
			return afterKeyConflict(shipment, ex);
		}
		catch (RuntimeException ex) {
			if (!DbFailures.isConnectionOrResource(ex)) {
				orderFailed(orderId, "배송 등록 실패: " + rootMessage(ex));
				return NextStep.NEXT_ORDER;
			}
			// 커밋 중 타임아웃(결과 불명)도 여기다. 다음 주기에 같은 SHIPMENT_ID로 다시 시도해, 앞 커밋이 늦게 끝났으면
			// PK 충돌로 드러난다
			this.shipmentDb.failed(ex);
			return NextStep.END_CYCLE;
		}
		if (existing.isPresent()) {
			// INSERT 후 배송 완료 기록 전에 중단됐던 경우. INSERT 없이 완료만 적는다
			alreadyRegistered(orderId, existing.get().shipmentId());
		}
		else {
			registered(orderId, shipment.shipmentId());
		}
		return NextStep.NEXT_ORDER;
	}

	/**
	 * INSERT가 무결성 위반으로 실패했다. 새 트랜잭션에서 미리 배정한 번호로 다시 조회해 셋으로 나눈다 (D-03, D-19).
	 * <ul>
	 * <li>그 번호의 배송이 없다: PK 충돌이 아니라 DB가 이 행을 거절했다. 건별 실패로 센다.</li>
	 * <li>같은 배송이 있다: 결과를 몰랐던 앞 시도가 늦게 커밋됐다. 완료로 적는다.</li>
	 * <li>다른 배송이 있다: 다시 해도 결과가 같으므로 재시도 없이 사람에게 넘긴다.</li>
	 * </ul>
	 */
	private NextStep afterKeyConflict(Shipment shipment, DataIntegrityViolationException conflict) {
		String orderId = shipment.orderId();
		Optional<Shipment> stored;
		try {
			stored = this.registry.findById(shipment.shipmentId());
		}
		catch (RuntimeException ex) {
			if (DbFailures.isConnectionOrResource(ex)) {
				this.shipmentDb.failed(ex);
				return NextStep.END_CYCLE;
			}
			throw ex;
		}

		if (stored.isEmpty()) {
			orderFailed(orderId, "배송 등록 거절: " + rootMessage(conflict));
			return NextStep.NEXT_ORDER;
		}
		if (stored.get().sameAs(shipment)) {
			alreadyRegistered(orderId, shipment.shipmentId());
			return NextStep.NEXT_ORDER;
		}
		String error = "SHIPMENT_ID " + shipment.shipmentId() + "에 다른 배송이 있음: " + rootMessage(conflict);
		this.retry.markFailed(orderId, STAGE, error);
		this.stageEvents.failedWithoutRetry(orderId, STAGE, error);
		return NextStep.NEXT_ORDER;
	}

	/**
	 * 이번에 INSERT했다.
	 */
	private void registered(String orderId, String shipmentId) {
		this.retry.recordCompletion(orderId, STAGE, shipmentId);
		this.events.publishForOrder(orderId, ProcessEvent.Stage.SHIPMENT, Result.SUCCESS, "배송 " + shipmentId + " 등록");
	}

	/**
	 * 배송은 이미 있었다(앞 시도가 INSERT했다). 완료만 적는다.
	 */
	private void alreadyRegistered(String orderId, String shipmentId) {
		this.retry.recordCompletion(orderId, STAGE, shipmentId);
		this.events.publishForOrder(orderId, ProcessEvent.Stage.SHIPMENT, Result.SKIP, "배송 " + shipmentId + " 이미 있음");
	}

	private void orderFailed(String orderId, String error) {
		RetryService.Failure failure = this.retry.recordFailure(orderId, STAGE, error);
		this.stageEvents.failed(orderId, STAGE, failure, error);
	}

	private void missingRecord(String orderId) {
		this.retry.markFailed(orderId, STAGE, "H2에 수신 기록 없음");
		this.stageEvents.missingRecord(orderId, STAGE);
	}

	private static String rootMessage(Throwable ex) {
		Throwable root = ex;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return String.valueOf(root.getMessage()).strip();
	}

}
