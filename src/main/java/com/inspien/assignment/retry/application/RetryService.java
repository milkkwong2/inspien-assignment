package com.inspien.assignment.retry.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.inspien.assignment.global.config.AppProperties;
import com.inspien.assignment.global.event.ProcessEvent;
import com.inspien.assignment.global.transaction.HistoryTx;
import com.inspien.assignment.retry.application.port.StageStore;
import com.inspien.assignment.retry.domain.RetryPolicy;
import com.inspien.assignment.retry.domain.Stage;
import com.inspien.assignment.retry.domain.StageState;
import com.inspien.assignment.retry.domain.StageStatus;

/**
 * 주문·단계별 진행 상태: 단계 완료, 재시도 횟수와 다음 시도 시각, "사람이 확인" 표시 (D-10, D-26). 영수증·배송이 각자 자기
 * 단계만 쓰고, 마감과 조회가 읽는다.
 *
 * <p>여기 쓰는 값은 최선 노력이 아니다. 단계 완료와 표시는 Oracle에 담을 곳이 없어 H2에만 있으므로, 쓰기에 실패하면 예외가
 * 그대로 올라가 호출한 쪽이 그 주기를 끝낸다 (D-22, D-26).
 */
@Service
public class RetryService {

	private final StageStore store;

	private final RetryPolicy policy;

	private final Clock clock;

	public RetryService(StageStore store, AppProperties properties, Clock clock) {
		this.store = store;
		this.policy = new RetryPolicy(properties.retry().maxAttempts(), properties.retry().initialBackoff(),
				properties.retry().multiplier());
		this.clock = clock;
	}

	/**
	 * 한 주기에 처리할 주문을 고른다 (설계서 §7.2 ②③). 그 단계가 PENDING이고 백오프 중이 아닌 것만 남긴 뒤 앞에서 limit건.
	 * 먼저 자르고 거르면 그 limit건이 모두 백오프 중이거나 끝난 주문일 때 뒤의 정상 주문이 계속 기다린다.
	 *
	 * @param candidates Oracle STATUS=N인 주문번호, 오름차순
	 */
	@HistoryTx(readOnly = true)
	public List<String> selectDue(Stage stage, List<String> candidates, int limit) {
		Instant now = this.clock.instant();
		Map<String, StageState> states = this.store.findAll(candidates, stage);
		List<String> due = new ArrayList<>();
		for (String orderId : candidates) {
			StageState state = states.get(orderId);
			if (state != null && !state.dueAt(now)) {
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
	 * 모든 단계가 DONE인 주문을 앞에서 limit건 고른다. 마감 스케줄러가 쓴다 (D-26).
	 *
	 * @param candidates Oracle STATUS=N인 주문번호, 오름차순
	 */
	@HistoryTx(readOnly = true)
	public List<String> selectAllDone(List<String> candidates, int limit) {
		Map<Stage, Map<String, StageState>> byStage = findAll(candidates);
		List<String> done = new ArrayList<>();
		for (String orderId : candidates) {
			boolean allDone = byStage.values()
				.stream()
				.allMatch(states -> StageState.statusOf(states.get(orderId)) == StageStatus.DONE);
			if (allDone) {
				done.add(orderId);
				if (done.size() == limit) {
					break;
				}
			}
		}
		return done;
	}

	/**
	 * 건별 실패 1회를 센다. 횟수·다음 시도 시각·한도 도달 표시를 한 H2 트랜잭션으로 쓴다. 따로 쓰면 그 사이에 멈췄을 때 횟수만
	 * 한도에 닿고 표시가 없는 주문이 생긴다 (D-26).
	 *
	 * @return 이번 실패로 한도에 닿았으면 exhausted = true (그 단계 FAILED)
	 */
	@HistoryTx
	public Failure recordFailure(String orderId, Stage stage, String error) {
		StageState current = current(orderId, stage);
		int attempts = current.attempts() + 1;
		boolean exhausted = this.policy.exhausted(attempts);
		Instant next = exhausted ? null : this.policy.nextAttemptAt(this.clock.instant(), attempts);
		this.store.save(current.failed(truncate(error), next, exhausted));
		return new Failure(attempts, exhausted, next);
	}

	/**
	 * 다시 해도 결과가 같은 실패(수신 기록 없음, 미리 배정한 번호에 다른 배송): 재시도 없이 그 단계 FAILED.
	 */
	@HistoryTx
	public void markFailed(String orderId, Stage stage, String error) {
		this.store.save(current(orderId, stage).markedFailed(truncate(error)));
	}

	/**
	 * 단계 완료를 적는다. 실패하면 예외가 올라가 그 주기를 끝내고, 다음 주기의 사전 확인(FTP 목록, SHIPMENT_TB 조회)이
	 * 완료만 다시 적는다 (D-03, D-26).
	 */
	@HistoryTx
	public void recordCompletion(String orderId, Stage stage, String artifact) {
		this.store.save(current(orderId, stage).completed(artifact, this.clock.instant()));
	}

	/**
	 * 결과물을 미리 배정한다. 이미 있으면 그 값을 쓴다. 배정과 기록이 한 H2 트랜잭션이다 (D-03 보완, D-08).
	 */
	@HistoryTx
	public String artifactOrAssign(String orderId, Stage stage, Supplier<String> assign) {
		StageState current = current(orderId, stage);
		if (current.artifact() != null) {
			return current.artifact();
		}
		String artifact = assign.get();
		this.store.save(current.withArtifact(artifact));
		return artifact;
	}

	/**
	 * 운영자 재처리 (설계서 §5.4, D-26). "사람이 확인" 표시가 있는 단계를 모두 다시 시작한다. H2만 바꾼다.
	 *
	 * <p>잠금 없이 조건부 갱신(WHERE REACHED_FAILED = TRUE)으로 한다. 표시를 쓰는 쪽은 스케줄러, 지우는 쪽은 이것뿐이고,
	 * 스케줄러는 표시된 단계를 고르지 않는다. 건별 실패가 횟수와 표시를 한 트랜잭션으로 쓰므로 반쯤 쓴 상태를 보지 않는다.
	 * 지우는 것은 재시도 상태뿐이다. 완료 시각과 결과물(특히 미리 배정한 SHIPMENT_ID)을 지우면 결과 불명이던 앞 INSERT와 다른
	 * 번호로 중복 배송이 생길 수 있다.
	 *
	 * @return 다시 시작한 단계. 비어 있으면 표시가 하나도 없었다
	 */
	@HistoryTx
	public List<Stage> restart(String orderId) {
		List<Stage> restarted = new ArrayList<>();
		for (Stage stage : Stage.values()) {
			if (this.store.restartIfFailed(orderId, stage)) {
				restarted.add(stage);
			}
		}
		return restarted;
	}

	@HistoryTx(readOnly = true)
	public Optional<StageState> find(String orderId, Stage stage) {
		return this.store.find(orderId, stage);
	}

	/**
	 * 단계마다 주문번호 → 진행 상태. 행이 없는 주문은 맵에 없다(PENDING).
	 */
	@HistoryTx(readOnly = true)
	public Map<Stage, Map<String, StageState>> findAll(Collection<String> orderIds) {
		Map<Stage, Map<String, StageState>> byStage = new EnumMap<>(Stage.class);
		for (Stage stage : Stage.values()) {
			byStage.put(stage, this.store.findAll(orderIds, stage));
		}
		return byStage;
	}

	static ProcessEvent.Stage eventStage(Stage stage) {
		return switch (stage) {
			case RECEIPT -> ProcessEvent.Stage.RECEIPT;
			case SHIPMENT -> ProcessEvent.Stage.SHIPMENT;
		};
	}

	private StageState current(String orderId, Stage stage) {
		return this.store.find(orderId, stage).orElseGet(() -> StageState.initial(orderId, stage));
	}

	private static String truncate(String error) {
		return (error == null || error.length() <= 1000) ? error : error.substring(0, 1000);
	}

	/**
	 * @param attempts 이번 실패까지 센 횟수
	 * @param exhausted 한도에 닿음 (그 단계 FAILED)
	 * @param nextAttemptAt 다음 시도 시각 (한도면 null)
	 */
	public record Failure(int attempts, boolean exhausted, Instant nextAttemptAt) {
	}

}
