package com.inspien.assignment.inbound.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import com.inspien.assignment.inbound.application.MessageListItem;
import com.inspien.assignment.inbound.application.MessageSummary;
import com.inspien.assignment.inbound.application.ReceiveResult;
import com.inspien.assignment.inbound.application.port.MessageStore;
import com.inspien.assignment.inbound.domain.AssignedOrder;
import com.inspien.assignment.inbound.domain.MessageKey;
import com.inspien.assignment.inbound.domain.MessageState;
import com.inspien.assignment.inbound.domain.ReceivedMessage;

/**
 * H2 RECEIVED_MESSAGE·MESSAGE_ORDER 어댑터. 응답 결과는 JSON으로 저장해 재전송 때 그대로 돌려준다.
 */
@Repository
class JpaMessageStore implements MessageStore {

	/**
	 * 원래 receivedAt의 +09:00을 그대로 돌려주려고 읽을 때 UTC로 바꾸지 않는다.
	 */
	private static final JsonMapper JSON = JsonMapper.builder()
		.disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
		.build();

	@PersistenceContext(unitName = "history")
	private EntityManager em;

	private static final QReceivedMessageEntity message = QReceivedMessageEntity.receivedMessageEntity;

	private final ReceivedMessageRepository messages;

	private final MessageOrderRepository messageOrders;

	private final JPAQueryFactory query;

	JpaMessageStore(ReceivedMessageRepository messages, MessageOrderRepository messageOrders,
			@Qualifier("historyQueryFactory") JPAQueryFactory query) {
		this.messages = messages;
		this.messageOrders = messageOrders;
		this.query = query;
	}

	@Override
	public Optional<ReceivedMessage> findLatestByKey(MessageKey key) {
		return this.messages.findFirstByMessageKeyOrderByIdDesc(key.value())
			.map(JpaMessageStore::toModel);
	}

	@Override
	public void insert(ReceivedMessage message) {
		this.em.persist(new ReceivedMessageEntity(message.id(), message.key().value(), message.receivedAt(),
				message.contentType(), message.rawBody(), message.state().name()));
	}

	@Override
	public void markProcessing(String messageId) {
		find(messageId).processing();
	}

	@Override
	public void complete(String messageId, ReceiveResult result, Instant completedAt) {
		find(messageId).finish(MessageState.COMPLETED.name(), 200, JSON.writeValueAsString(result), completedAt);
	}

	@Override
	public void fail(String messageId, int status, String code, String detail) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("code", code);
		body.put("detail", detail);
		find(messageId).finish(MessageState.FAILED.name(), status, JSON.writeValueAsString(body), null);
	}

	@Override
	public Optional<ReceiveResult> findResult(String messageId) {
		return this.messages.findById(messageId)
			.filter(entity -> MessageState.COMPLETED.name().equals(entity.state()) && entity.responseBody() != null)
			.map(entity -> JSON.readValue(entity.responseBody(), ReceiveResult.class));
	}

	@Override
	public List<AssignedOrder> findAssigned(String messageId) {
		return this.messageOrders.findByMessageIdOrderBySeqAsc(messageId)
			.stream()
			.map(JpaMessageStore::toModel)
			.toList();
	}

	@Override
	public List<AssignedOrder> findAssignedByOrderIds(Collection<String> orderIds) {
		return this.messageOrders.findByOrderIdIn(orderIds)
			.stream()
			.map(JpaMessageStore::toModel)
			.toList();
	}

	@Override
	public void insertAssigned(List<AssignedOrder> orders) {
		for (AssignedOrder assigned : orders) {
			this.em.persist(new MessageOrderEntity(assigned.orderId(), assigned.messageId(), assigned.seq(),
					assigned.userId(), assigned.itemId(), assigned.receivedAt()));
		}
	}

	@Override
	public Optional<MessageSummary> findSummary(String messageId) {
		return this.messages.findById(messageId)
			.map(JpaMessageStore::toSummary);
	}

	/**
	 * 목록에 보여 줄 칸만 읽는다. 받은 원문(RAW_BODY, 최대 1MB)은 읽지 않는다. 응답 본문은 오류 코드가 필요한 미완료 메시지만
	 * 읽는다. 완료 메시지의 응답 본문은 수신 결과 전체라 크고, 목록에는 쓰지 않는다.
	 */
	@Override
	public List<MessageListItem> findListItems(MessageState state, Instant from, Instant to, String after, int limit) {
		StringExpression errorBody = new CaseBuilder().when(message.state.ne(MessageState.COMPLETED.name()))
			.then(message.responseBody)
			.otherwise(Expressions.nullExpression(String.class));
		// where에 null을 넘기면 그 조건은 빠진다. 값이 있는 조건만 붙는다
		List<Tuple> found = this.query
			.select(message.id, message.state, message.responseStatus, message.receivedAt, errorBody)
			.from(message)
			.where(stateIs(state), receivedFrom(from), receivedBefore(to), idAfter(after))
			.orderBy(message.id.asc())
			.limit(limit)
			.fetch();
		return found.stream()
			.map(row -> toListItem(row, errorBody))
			.toList();
	}

	private static BooleanExpression stateIs(MessageState state) {
		return (state != null) ? message.state.eq(state.name()) : null;
	}

	private static BooleanExpression receivedFrom(Instant from) {
		return (from != null) ? message.receivedAt.goe(from) : null;
	}

	private static BooleanExpression receivedBefore(Instant to) {
		return (to != null) ? message.receivedAt.lt(to) : null;
	}

	private static BooleanExpression idAfter(String after) {
		return (after != null) ? message.id.gt(after) : null;
	}

	private static MessageSummary toSummary(ReceivedMessageEntity entity) {
		MessageState state = MessageState.valueOf(entity.state());
		ReceiveResult result = null;
		String errorCode = null;
		String errorDetail = null;
		if (entity.responseBody() != null) {
			if (state == MessageState.COMPLETED) {
				result = JSON.readValue(entity.responseBody(), ReceiveResult.class);
			}
			else {
				JsonNode body = JSON.readTree(entity.responseBody());
				errorCode = body.path("code").asString(null);
				errorDetail = body.path("detail").asString(null);
			}
		}
		return new MessageSummary(entity.id(), state, entity.responseStatus(), entity.receivedAt(),
				entity.completedAt(), errorCode, errorDetail, result);
	}

	/**
	 * 오류 본문(Problem Details JSON)에서 code만 꺼낸다. 완료 메시지는 본문을 읽지 않았고 처리 중 메시지는 본문이 없어 null이다.
	 */
	private static MessageListItem toListItem(Tuple row, StringExpression errorBody) {
		MessageState state = MessageState.valueOf(row.get(message.state));
		String body = row.get(errorBody);
		String errorCode = (body != null) ? JSON.readTree(body).path("code").asString(null) : null;
		return new MessageListItem(row.get(message.id), state, row.get(message.responseStatus),
				row.get(message.receivedAt), errorCode);
	}

	private ReceivedMessageEntity find(String messageId) {
		return this.messages.findById(messageId)
			.orElseThrow(() -> new IllegalStateException("수신 기록이 없습니다: " + messageId));
	}

	private static ReceivedMessage toModel(ReceivedMessageEntity entity) {
		return new ReceivedMessage(entity.id(), new MessageKey(entity.messageKey()), entity.receivedAt(),
				entity.contentType(), entity.rawBody(), MessageState.valueOf(entity.state()), entity.responseStatus(),
				entity.completedAt());
	}

	private static AssignedOrder toModel(MessageOrderEntity entity) {
		return new AssignedOrder(entity.orderId(), entity.messageId(), entity.seq(), entity.userId(), entity.itemId(),
				entity.receivedAt());
	}

}
