package com.inspien.assignment.receipt.application;

import java.nio.charset.Charset;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.stereotype.Service;

import com.inspien.assignment.global.config.AppProperties;
import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.global.event.ProcessEvent.Result;
import com.inspien.assignment.global.event.ProcessEvents;
import com.inspien.assignment.inbound.application.InboundQueryService;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.order.domain.Order;
import com.inspien.assignment.order.domain.OrderStatus;
import com.inspien.assignment.receipt.application.port.ReceiptUploader;
import com.inspien.assignment.receipt.domain.Receipt;
import com.inspien.assignment.receipt.domain.UploadPlan;
import com.inspien.assignment.retry.application.ConnectionWatch;
import com.inspien.assignment.retry.application.RetryService;
import com.inspien.assignment.retry.domain.Stage;

/**
 * IF-02 영수증 (설계서 §7.2). RECEIVED 주문마다 영수증 CSV를 FTP에 올리고 RECEIPT_SENT로 바꾼다.
 *
 * <p>실패를 둘로 나눈다 (D-10, D-22).
 * <ul>
 * <li>연결 실패·우리 쪽 저장소 장애: 그 주기를 끝내고 주문별 횟수를 세지 않는다. 상대 점검 때문에 멀쩡한 주문이
 * 한꺼번에 실패 처리되지 않게 한다.</li>
 * <li>건별 실패(연결된 뒤 그 파일 업로드 실패): 그 주문만 횟수 +1, 백오프 후 재시도, 5회째면 RECEIPT_FAILED.</li>
 * </ul>
 */
@Service
public class ReceiptService {

	private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

	private static final Stage STAGE = Stage.RECEIPT;

	private final OrderService orders;

	private final InboundQueryService inbound;

	private final RetryService retry;

	private final ReceiptUploader uploader;

	private final ProcessEvents events;

	private final Clock clock;

	private final String applicantName;

	private final Charset contentCharset;

	private final boolean bom;

	private final int batchSize;

	private final ConnectionWatch ftp;

	public ReceiptService(OrderService orders, InboundQueryService inbound, RetryService retry,
			ReceiptUploader uploader, ProcessEvents events, Clock clock, AppProperties properties) {
		this.orders = orders;
		this.inbound = inbound;
		this.retry = retry;
		this.uploader = uploader;
		this.events = events;
		this.clock = clock;
		this.applicantName = properties.applicant().name();
		this.contentCharset = Charset.forName(properties.receipt().contentCharset());
		this.bom = properties.receipt().bom();
		this.batchSize = properties.scheduler().batchSize();
		this.ftp = new ConnectionWatch("FTP", STAGE, events);
	}

	/**
	 * 한 주기: 대상 조회 → 되돌림 감지·백오프 제외 → 앞에서 batchSize건 → (대상이 있으면) FTP 연결 → 주문마다 처리·커밋.
	 */
	public void runCycle() {
		List<String> due;
		try {
			due = this.retry.selectDue(STAGE, this.orders.findIdsByStatus(OrderStatus.RECEIVED), this.batchSize);
		}
		catch (RuntimeException ex) {
			log.error("쇼핑몰 DB 또는 H2 장애로 영수증 주기를 끝냅니다", ex);
			return;
		}
		if (due.isEmpty()) {
			return;
		}
		ReceiptUploader.Session session;
		Map<String, Long> listing;
		try {
			session = this.uploader.open();
		}
		catch (ReceiptUploader.ConnectionFailedException ex) {
			this.ftp.failed(ex);
			return;
		}
		try (session) {
			try {
				listing = new HashMap<>(session.list());
			}
			catch (ReceiptUploader.ConnectionFailedException ex) {
				this.ftp.failed(ex);
				return;
			}
			this.ftp.connected();
			for (String orderId : due) {
				if (!processOne(orderId, session, listing)) {
					break;
				}
			}
		}
	}

	/**
	 * @return 다음 주문을 계속 처리할지. 연결이 끊기거나 우리 쪽 저장소가 안 되면 남은 주문도 같은 이유로 실패하므로 끝낸다
	 */
	private boolean processOne(String orderId, ReceiptUploader.Session session, Map<String, Long> listing) {
		MDC.put("orderId", orderId);
		try {
			Optional<Order> found = this.orders.findById(orderId);
			if (found.isEmpty() || found.get().status() != OrderStatus.RECEIVED) {
				// 대상을 고른 뒤 상태가 바뀌었다 (운영자 등). 이번 주기에는 건드리지 않는다
				return true;
			}
			Optional<Instant> receivedAt = this.inbound.receivedAt(orderId);
			if (receivedAt.isEmpty()) {
				missingRecord(orderId);
				return true;
			}
			Receipt receipt = new Receipt(found.get(), receivedAt.get().atZone(this.clock.getZone()).toOffsetDateTime(),
					this.applicantName);
			byte[] content = receipt.content(this.contentCharset, this.bom);
			UploadPlan plan = UploadPlan.of(receipt, listing, content.length);
			if (plan.upload()) {
				try {
					session.upload(plan.fileName(), content);
					listing.put(plan.fileName(), (long) content.length);
				}
				catch (ReceiptUploader.UploadFailedException ex) {
					orderFailed(orderId, plan.fileName() + " 업로드 실패: " + ex.getMessage());
					return !ex.connectionLost();
				}
			}
			succeeded(orderId, plan);
			return true;
		}
		catch (RuntimeException ex) {
			log.error("쇼핑몰 DB 또는 H2 장애로 영수증 주기를 끝냅니다. 다음 주기에 사전 확인으로 이어서 처리합니다", ex);
			return false;
		}
		finally {
			MDC.remove("orderId");
		}
	}

	private void succeeded(String orderId, UploadPlan plan) {
		boolean moved = this.orders.moveStatus(orderId, OrderStatus.RECEIVED, OrderStatus.RECEIPT_SENT);
		if (!moved) {
			this.events.publish(null, orderId, ProcessEvent.Stage.RECEIPT, Result.STATE_MISMATCH,
					"RECEIVED가 아니어서 RECEIPT_SENT로 바꾸지 않음 (파일 " + plan.fileName() + ")");
			return;
		}
		this.retry.recordSuccess(orderId, STAGE, plan.fileName());
		if (plan.upload()) {
			this.events.publish(null, orderId, ProcessEvent.Stage.RECEIPT, Result.SUCCESS,
					plan.fileName() + (plan.replacesPartial() ? " 업로드 (반쪽 파일 대신)" : " 업로드"));
		}
		else {
			this.events.publish(null, orderId, ProcessEvent.Stage.RECEIPT, Result.SKIP, plan.fileName() + " 이미 있음");
		}
	}

	private void orderFailed(String orderId, String error) {
		RetryService.Failure failure = this.retry.recordFailure(orderId, STAGE, error);
		if (!failure.exhausted()) {
			this.events.publish(null, orderId, ProcessEvent.Stage.RECEIPT, Result.FAIL, "%d회째 실패, 다음 시도 %s: %s".formatted(
					failure.attempts(), LocalTime.ofInstant(failure.nextAttemptAt(), this.clock.getZone())
						.truncatedTo(ChronoUnit.SECONDS),
					error));
			return;
		}
		if (this.orders.moveStatus(orderId, OrderStatus.RECEIVED, OrderStatus.RECEIPT_FAILED)) {
			this.retry.markReachedFailed(orderId, STAGE, error);
			this.events.publish(null, orderId, ProcessEvent.Stage.RECEIPT, Result.FAIL,
					failure.attempts() + "회째 실패, RECEIPT_FAILED (사람이 확인): " + error);
		}
	}

	/**
	 * H2에 수신 기록이 없는 주문: 수신일시를 지어내지 않는다. 다시 해도 결과가 같아 재시도 없이 사람에게 넘긴다 (D-22).
	 */
	private void missingRecord(String orderId) {
		if (this.orders.moveStatus(orderId, OrderStatus.RECEIVED, OrderStatus.RECEIPT_FAILED)) {
			this.retry.markReachedFailed(orderId, STAGE, "H2에 수신 기록 없음");
			this.events.publish(null, orderId, ProcessEvent.Stage.RECEIPT, Result.MISSING_RECORD,
					"H2에 수신 기록이 없어 RECEIPT_FAILED. 주문번호 " + orderId);
		}
	}

}
