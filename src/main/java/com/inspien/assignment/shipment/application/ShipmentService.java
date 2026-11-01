package com.inspien.assignment.shipment.application;

import java.time.Clock;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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
import com.inspien.assignment.retry.application.RetryService;
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

	private final Clock clock;

	private final int batchSize;

	private final ConnectionWatch shipmentDb;

	ShipmentService(OrderService orders, InboundQueryService inbound, RetryService retry, NumberingService numbering,
			ShipmentRegistry registry, ProcessEvents events, Clock clock, AppProperties properties) {
		this.orders = orders;
		this.inbound = inbound;
		this.retry = retry;
		this.numbering = numbering;
		this.registry = registry;
		this.events = events;
		this.clock = clock;
		this.batchSize = properties.scheduler().batchSize();
		this.shipmentDb = new ConnectionWatch("운송회사 DB", STAGE, events);
	}

	public void runCycle() {
		List<String> due;
		try {
			due = this.retry.selectDue(STAGE, this.orders.findIdsByStatus(OrderStatus.N), this.batchSize);
		}
		catch (RuntimeException ex) {
			log.error("쇼핑몰 DB 또는 H2 장애로 배송 주기를 끝냅니다", ex);
			return;
		}
		for (String orderId : due) {
			if (!processOne(orderId)) {
				break;
			}
		}
	}

	/**
	 * @return 다음 주문을 계속 처리할지
	 */
	private boolean processOne(String orderId) {
		MDC.put("orderId", orderId);
		try {
			Optional<Order> found = this.orders.findById(orderId);
			if (found.isEmpty() || found.get().status() != OrderStatus.N) {
				return true;
			}
			if (this.inbound.receivedAt(orderId).isEmpty()) {
				missingRecord(orderId);
				return true;
			}
			String shipmentId = this.retry.artifactOrAssign(orderId, STAGE,
					() -> this.numbering.next(IdSequence.SHIPMENT));
			return register(Shipment.of(shipmentId, found.get()));
		}
		catch (RuntimeException ex) {
			log.error("쇼핑몰 DB 또는 H2 장애로 배송 주기를 끝냅니다. 다음 주기에 사전 확인으로 이어서 처리합니다", ex);
			return false;
		}
		finally {
			MDC.remove("orderId");
		}
	}

	/**
	 * 운송 DB 작업만 오류 분류 대상이다. H2 완료 기록은 그 밖에서 해, 실패하면 예외가 processOne으로 올라가 주기를 끝낸다.
	 * 분류 안에 두면 H2 장애가 배송 건별 실패로 세지거나 운송 DB 연결 실패로 남는다 (D-22, D-26).
	 */
	private boolean register(Shipment shipment) {
		String orderId = shipment.orderId();
		String registered;
		boolean inserted;
		try {
			Optional<Shipment> existing = this.registry.findByOrderId(orderId);
			this.shipmentDb.connected();
			if (existing.isPresent()) {
				// INSERT 후 배송 완료 기록 전에 중단됐던 경우. INSERT 없이 완료만 적는다
				registered = existing.get().shipmentId();
				inserted = false;
			}
			else {
				this.registry.insert(shipment);
				registered = shipment.shipmentId();
				inserted = true;
			}
		}
		catch (DataIntegrityViolationException ex) {
			return afterKeyConflict(shipment, ex);
		}
		catch (RuntimeException ex) {
			if (!DbFailures.isConnectionOrResource(ex)) {
				orderFailed(orderId, "배송 등록 실패: " + rootMessage(ex));
				return true;
			}
			// 커밋 중 타임아웃(결과 불명)도 여기다. 다음 주기에 같은 SHIPMENT_ID로 다시 시도해, 앞 커밋이 늦게 끝났으면
			// PK 충돌로 드러난다
			this.shipmentDb.failed(ex);
			return false;
		}
		succeeded(orderId, registered, inserted);
		return true;
	}

	private boolean afterKeyConflict(Shipment shipment, DataIntegrityViolationException conflict) {
		Optional<Shipment> stored;
		try {
			stored = this.registry.findById(shipment.shipmentId());
		}
		catch (RuntimeException ex) {
			if (DbFailures.isConnectionOrResource(ex)) {
				this.shipmentDb.failed(ex);
				return false;
			}
			throw ex;
		}
		if (stored.isPresent() && stored.get().sameAs(shipment)) {
			// 앞 시도가 늦게 커밋됐다
			succeeded(shipment.orderId(), shipment.shipmentId(), false);
			return true;
		}
		String error = "SHIPMENT_ID " + shipment.shipmentId() + "에 다른 배송이 있음: " + rootMessage(conflict);
		if (stored.isEmpty()) {
			// PK 충돌이 아닌 무결성 위반: DB가 이 행을 거절했다
			orderFailed(shipment.orderId(), "배송 등록 거절: " + rootMessage(conflict));
			return true;
		}
		// 다시 해도 결과가 같으므로 재시도 없이 사람에게 넘긴다
		this.retry.markFailed(shipment.orderId(), STAGE, error);
		this.events.publish(null, shipment.orderId(), ProcessEvent.Stage.SHIPMENT, Result.FAIL,
				"배송 FAILED (사람이 확인): " + error);
		return true;
	}

	private void succeeded(String orderId, String shipmentId, boolean inserted) {
		this.retry.recordCompletion(orderId, STAGE, shipmentId);
		this.events.publish(null, orderId, ProcessEvent.Stage.SHIPMENT, inserted ? Result.SUCCESS : Result.SKIP,
				"배송 " + shipmentId + (inserted ? " 등록" : " 이미 있음"));
	}

	private void orderFailed(String orderId, String error) {
		RetryService.Failure failure = this.retry.recordFailure(orderId, STAGE, error);
		if (!failure.exhausted()) {
			this.events.publish(null, orderId, ProcessEvent.Stage.SHIPMENT, Result.FAIL,
					"%d회째 실패, 다음 시도 %s: %s".formatted(failure.attempts(),
							LocalTime.ofInstant(failure.nextAttemptAt(), this.clock.getZone())
								.truncatedTo(ChronoUnit.SECONDS),
							error));
			return;
		}
		this.events.publish(null, orderId, ProcessEvent.Stage.SHIPMENT, Result.FAIL,
				failure.attempts() + "회째 실패, 배송 FAILED (사람이 확인): " + error);
	}

	private void missingRecord(String orderId) {
		this.retry.markFailed(orderId, STAGE, "H2에 수신 기록 없음");
		this.events.publish(null, orderId, ProcessEvent.Stage.SHIPMENT, Result.MISSING_RECORD,
				"H2에 수신 기록이 없어 배송 FAILED. 주문번호 " + orderId);
	}

	private static String rootMessage(Throwable ex) {
		Throwable root = ex;
		while (root.getCause() != null) {
			root = root.getCause();
		}
		return String.valueOf(root.getMessage()).strip();
	}

}
