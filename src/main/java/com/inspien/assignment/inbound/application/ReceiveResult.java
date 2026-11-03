package com.inspien.assignment.inbound.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.inspien.assignment.inbound.domain.AssignedOrder;
import com.inspien.assignment.inbound.domain.IssueReason;
import com.inspien.assignment.inbound.domain.OrderAssembly;

/**
 * 수신 결과 = 200 응답의 data (설계서 §5.1, D-18). H2에 저장해 재전송 때 그대로 돌려준다 (D-05).
 *
 * @param messageId 메시지 ID
 * @param receivedAt 처음 받은 시각
 * @param replayed 이번 요청이 저장한 것이 아니라 이전 결과임 (D-15, D-24)
 * @param notice replayed일 때 송신자 안내
 * @param summary 건수
 * @param stored 저장한 주문
 * @param rejected 거부한 ITEM
 * @param warnings 경고
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReceiveResult(String messageId, OffsetDateTime receivedAt, boolean replayed, String notice,
		Summary summary, List<Stored> stored, List<Rejected> rejected, List<Warning> warnings) {

	static ReceiveResult of(String messageId, OffsetDateTime receivedAt, OrderAssembly assembly,
			List<AssignedOrder> assigned) {
		Map<Integer, String> orderIdsBySeq = AssignedOrder.orderIdsBySeq(assigned);
		List<Stored> stored = assembly.accepted()
			.stream()
			.map(accepted -> Stored.of(accepted, orderIdsBySeq.get(accepted.seq())))
			.toList();
		List<Rejected> rejected = assembly.rejected()
			.stream()
			.map(Rejected::of)
			.toList();
		List<Warning> warnings = assembly.warnings()
			.stream()
			.map(Warning::of)
			.toList();

		Summary summary = new Summary(stored.size(), rejected.size(), warnings.size());
		return new ReceiveResult(messageId, receivedAt, false, null, summary, stored, rejected, warnings);
	}

	/**
	 * 이전 결과를 다시 보낼 때. 같은 ORDER_ID와 원래 receivedAt을 그대로 둔다.
	 */
	ReceiveResult asReplay(String notice) {
		return new ReceiveResult(this.messageId, this.receivedAt, true, notice, this.summary, this.stored,
				this.rejected, this.warnings);
	}

	public record Summary(int stored, int rejected, int warnings) {
	}

	/**
	 * @param seq 본문 안 ITEM 순서 (1부터)
	 */
	public record Stored(int seq, String orderId, String userId, String itemId) {

		static Stored of(OrderAssembly.Accepted accepted, String orderId) {
			return new Stored(accepted.seq(), orderId, accepted.item().userId(), accepted.item().itemId());
		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Rejected(int seq, String userId, String itemId, IssueReason reason, String field) {

		static Rejected of(OrderAssembly.Rejected rejected) {
			return new Rejected(rejected.seq(), rejected.userId(), rejected.itemId(), rejected.reason(),
					rejected.field());
		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Warning(String userId, IssueReason reason) {

		static Warning of(OrderAssembly.Warning warning) {
			return new Warning(warning.userId(), warning.reason());
		}

	}

}
