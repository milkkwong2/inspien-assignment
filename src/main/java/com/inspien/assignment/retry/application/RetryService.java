package com.inspien.assignment.retry.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.inspien.assignment.global.config.AppProperties;
import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.global.event.ProcessEvents;
import com.inspien.assignment.global.transaction.HistoryTx;
import com.inspien.assignment.retry.application.port.StageStore;
import com.inspien.assignment.retry.domain.RetryPolicy;
import com.inspien.assignment.retry.domain.Stage;
import com.inspien.assignment.retry.domain.StageState;

/**
 * 주문·단계별 재시도 상태: 횟수, 다음 시도 시각, 운영자 되돌림 감지 (D-10). 영수증·배송이 함께 쓴다.
 */
@Service
public class RetryService {

	private final StageStore store;

	private final RetryPolicy policy;

	private final ProcessEvents events;

	private final Clock clock;

	public RetryService(StageStore store, AppProperties properties, ProcessEvents events, Clock clock) {
		this.store = store;
		this.policy = new RetryPolicy(properties.retry().maxAttempts(), properties.retry().initialBackoff(),
				properties.retry().multiplier());
		this.events = events;
		this.clock = clock;
	}

	/**
	 * 한 주기에 처리할 주문을 고른다 (설계서 §7.2 ②③).
	 *
	 * <ol>
	 * <li>되돌림 감지: FAILED까지 갔던 주문이 다시 대상(앞 상태)에 있으면 운영자가 되돌린 것이다. 횟수를 0부터 센다.</li>
	 * <li>백오프 중인 주문을 뺀다. 되돌린 주문은 예전 다음 시도 시각이 남아 있어도 기다리지 않는다.</li>
	 * <li>앞에서 limit건. 먼저 자르고 거르면 그 limit건이 모두 백오프 중일 때 뒤의 정상 주문이 계속 기다린다.</li>
	 * </ol>
	 *
	 * @param candidates Oracle STATUS로 고른 주문번호, 오름차순
	 */
	@HistoryTx
	public List<String> selectDue(Stage stage, List<String> candidates, int limit) {
		Instant now = this.clock.instant();
		Map<String, StageState> states = this.store.findAll(candidates, stage);
		List<String> due = new ArrayList<>();
		for (String orderId : candidates) {
			StageState state = states.get(orderId);
			if (state != null && state.reachedFailed()) {
				this.store.save(state.resetByOperator(now));
				this.events.publish(null, orderId, eventStage(stage), ProcessEvent.Result.OPERATOR_RESET,
						"운영자 되돌림 감지: 횟수 0부터 다시 처리 (이전 " + state.attempts() + "회)");
				state = null;
			}
			if (state != null && state.waitingUntilAfter(now)) {
				continue;
			}
			due.add(orderId);
			if (due.size() == limit) {
				break;
			}
		}
		return due;
	}

	/**
	 * 건별 실패 1회를 센다.
	 *
	 * @return 이번 실패로 한도에 닿았으면 exhausted = true. 호출한 쪽이 STATUS를 *_FAILED로 바꾼다
	 */
	@HistoryTx
	public Failure recordFailure(String orderId, Stage stage, String error) {
		Instant now = this.clock.instant();
		StageState current = this.store.find(orderId, stage).orElseGet(() -> StageState.initial(orderId, stage, now));
		int attempts = current.attempts() + 1;
		Instant next = this.policy.nextAttemptAt(now, attempts);
		this.store.save(current.failed(truncate(error), next, now));
		return new Failure(attempts, this.policy.exhausted(attempts), next);
	}

	/**
	 * Oracle STATUS를 *_FAILED로 바꾼 뒤에 표시한다. 반대 순서면 Oracle 갱신이 실패했을 때 다음 주기에 운영자 되돌림으로
	 * 오인한다 (D-10).
	 */
	@HistoryTx
	public void markReachedFailed(String orderId, Stage stage, String error) {
		Instant now = this.clock.instant();
		StageState current = this.store.find(orderId, stage).orElseGet(() -> StageState.initial(orderId, stage, now));
		this.store.save(new StageState(orderId, stage, current.attempts(), null, true, truncate(error),
				current.artifact(), now));
	}

	@HistoryTx
	public void recordSuccess(String orderId, Stage stage, String artifact) {
		Instant now = this.clock.instant();
		StageState current = this.store.find(orderId, stage).orElseGet(() -> StageState.initial(orderId, stage, now));
		this.store.save(current.succeeded(artifact, now));
	}

	/**
	 * 결과물을 미리 배정한다. 이미 있으면 그 값을 쓴다. 배정과 기록이 한 H2 트랜잭션이다 (D-03 보완, D-08).
	 */
	@HistoryTx
	public String artifactOrAssign(String orderId, Stage stage, Supplier<String> assign) {
		Instant now = this.clock.instant();
		StageState current = this.store.find(orderId, stage).orElseGet(() -> StageState.initial(orderId, stage, now));
		if (current.artifact() != null) {
			return current.artifact();
		}
		String artifact = assign.get();
		this.store.save(current.withArtifact(artifact, now));
		return artifact;
	}

	@HistoryTx(readOnly = true)
	public Optional<StageState> find(String orderId, Stage stage) {
		return this.store.find(orderId, stage);
	}

	static ProcessEvent.Stage eventStage(Stage stage) {
		return switch (stage) {
			case RECEIPT -> ProcessEvent.Stage.RECEIPT;
			case SHIPMENT -> ProcessEvent.Stage.SHIPMENT;
		};
	}

	private static String truncate(String error) {
		return (error == null || error.length() <= 1000) ? error : error.substring(0, 1000);
	}

	/**
	 * @param attempts 이번 실패까지 센 횟수
	 * @param exhausted 한도에 닿음
	 * @param nextAttemptAt 다음 시도 시각 (한도면 null)
	 */
	public record Failure(int attempts, boolean exhausted, Instant nextAttemptAt) {
	}

}
