package com.inspien.assignment.receipt.application;

import java.nio.charset.Charset;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.inspien.assignment.retry.application.CycleRunner;
import com.inspien.assignment.retry.application.NextStep;
import com.inspien.assignment.retry.application.RetryService;
import com.inspien.assignment.retry.application.StageFailures;
import com.inspien.assignment.retry.domain.Stage;

/**
 * IF-02 영수증 (설계서 §7.2). STATUS=N이고 영수증 단계가 PENDING인 주문마다 영수증 CSV를 FTP에 올리고 H2에 영수증 완료를
 * 적는다. 배송 단계는 보지 않고, STATUS도 바꾸지 않는다(마감 스케줄러 몫) (D-26).
 *
 * <p>실패를 둘로 나눈다 (D-10, D-22).
 * <ul>
 * <li>연결 실패·우리 쪽 저장소 장애: 그 주기를 끝내고 주문별 횟수를 세지 않는다. 상대 점검 때문에 멀쩡한 주문이
 * 한꺼번에 실패 처리되지 않게 한다.</li>
 * <li>건별 실패(연결된 뒤 그 파일 업로드 실패): 그 주문만 횟수 +1, 백오프 후 재시도, 5회째면 영수증 FAILED.</li>
 * </ul>
 */
@Service
public class ReceiptService {

	private static final Stage STAGE = Stage.RECEIPT;

	private static final String CYCLE_NAME = "영수증";

	private final OrderService orders;

	private final InboundQueryService inbound;

	private final RetryService retry;

	private final ReceiptUploader uploader;

	private final ProcessEvents events;

	private final StageFailures failures;

	private final CycleRunner cycles;

	private final Clock clock;

	private final String applicantName;

	private final Charset contentCharset;

	private final boolean bom;

	private final int batchSize;

	private final ConnectionWatch ftp;

	public ReceiptService(OrderService orders, InboundQueryService inbound, RetryService retry,
			ReceiptUploader uploader, ProcessEvents events, StageFailures failures, CycleRunner cycles, Clock clock,
			AppProperties properties) {
		this.orders = orders;
		this.inbound = inbound;
		this.retry = retry;
		this.uploader = uploader;
		this.events = events;
		this.failures = failures;
		this.cycles = cycles;
		this.clock = clock;
		this.applicantName = properties.applicant().name();
		this.contentCharset = Charset.forName(properties.receipt().contentCharset());
		this.bom = properties.receipt().bom();
		this.batchSize = properties.scheduler().batchSize();
		this.ftp = new ConnectionWatch("FTP", STAGE, events);
	}

	/**
	 * 한 주기: 대상 조회 → 영수증 PENDING이고 백오프 중이 아닌 것만 → 앞에서 batchSize건 → (대상이 있으면) FTP 연결 →
	 * 주문마다 처리·커밋.
	 */
	public void runCycle() {
		List<String> due = this.cycles.selectTargets(CYCLE_NAME, this::selectDue);
		if (due.isEmpty()) {
			return;
		}
		try (ReceiptUploader.Session session = this.uploader.open()) {
			Map<String, Long> listing = new HashMap<>(session.list());
			this.ftp.connected();
			this.cycles.forEachOrder(CYCLE_NAME, due, orderId -> processOne(orderId, session, listing));
		}
		catch (ReceiptUploader.ConnectionFailedException ex) {
			// 접속·로그인·목록 조회 실패. 주문의 문제가 아니라 횟수를 세지 않고 주기를 건너뛴다 (D-10).
			// 주문 처리 중의 예외는 CycleRunner가 모두 받으므로 여기로 오지 않는다
			this.ftp.failed(ex);
		}
	}

	private List<String> selectDue() {
		List<String> candidates = this.orders.findIdsByStatus(OrderStatus.N);
		return this.retry.selectDue(STAGE, candidates, this.batchSize);
	}

	/**
	 * 예외는 CycleRunner가 받아 주기를 끝낸다.
	 *
	 * @return 연결이 끊기거나 우리 쪽 저장소가 안 되면 END_CYCLE. 남은 주문도 같은 이유로 실패한다
	 */
	private NextStep processOne(String orderId, ReceiptUploader.Session session, Map<String, Long> listing) {
		Optional<Order> found = this.orders.findById(orderId);
		if (found.isEmpty() || found.get().status() != OrderStatus.N) {
			// 대상을 고른 뒤 마감됐다. 이번 주기에는 건드리지 않는다
			return NextStep.NEXT_ORDER;
		}
		Optional<Instant> receivedAt = this.inbound.receivedAt(orderId);
		if (receivedAt.isEmpty()) {
			this.failures.missingRecord(orderId, STAGE);
			return NextStep.NEXT_ORDER;
		}
		OffsetDateTime receivedTime = receivedAt.get().atZone(this.clock.getZone()).toOffsetDateTime();
		Receipt receipt = new Receipt(found.get(), receivedTime, this.applicantName);
		byte[] content = receipt.content(this.contentCharset, this.bom);
		UploadPlan plan = UploadPlan.of(receipt, listing, content.length);
		if (!plan.upload()) {
			alreadyUploaded(orderId, plan.fileName());
			return NextStep.NEXT_ORDER;
		}
		return upload(orderId, plan, content, session, listing);
	}

	/**
	 * 올리고 완료를 적는다. 업로드 실패만 여기서 건별 실패로 센다. 데이터 연결을 열지 못했으면(아무것도 보내지 않음)
	 * 연결 실패로 보고 횟수 없이 주기를 끝낸다 (D-10). 완료 기록(H2)이 실패하면 예외가 CycleRunner로 올라가 주기를
	 * 끝낸다 (D-22, D-26).
	 */
	private NextStep upload(String orderId, UploadPlan plan, byte[] content, ReceiptUploader.Session session,
			Map<String, Long> listing) {
		try {
			session.upload(plan.fileName(), content);
		}
		catch (ReceiptUploader.ConnectionFailedException ex) {
			this.ftp.failed(ex);
			return NextStep.END_CYCLE;
		}
		catch (ReceiptUploader.UploadFailedException ex) {
			this.failures.orderFailed(orderId, STAGE, plan.fileName() + " 업로드 실패: " + ex.getMessage());
			return ex.connectionLost() ? NextStep.END_CYCLE : NextStep.NEXT_ORDER;
		}
		listing.put(plan.fileName(), (long) content.length);
		if (plan.replacesPartial()) {
			replacedPartial(orderId, plan.fileName());
		}
		else {
			uploaded(orderId, plan.fileName());
		}
		return NextStep.NEXT_ORDER;
	}

	private void uploaded(String orderId, String fileName) {
		this.retry.recordCompletion(orderId, STAGE, fileName);
		this.events.publishForOrder(orderId, ProcessEvent.Stage.RECEIPT, Result.SUCCESS, fileName + " 업로드");
	}

	private void replacedPartial(String orderId, String fileName) {
		this.retry.recordCompletion(orderId, STAGE, fileName);
		this.events.publishForOrder(orderId, ProcessEvent.Stage.RECEIPT, Result.SUCCESS,
				fileName + " 업로드 (반쪽 파일 대신)");
	}

	/**
	 * 업로드 뒤 완료 기록 전에 중단됐던 경우. 올리지 않고 완료만 적는다 (D-03).
	 */
	private void alreadyUploaded(String orderId, String fileName) {
		this.retry.recordCompletion(orderId, STAGE, fileName);
		this.events.publishForOrder(orderId, ProcessEvent.Stage.RECEIPT, Result.SKIP, fileName + " 이미 있음");
	}

}
