package com.inspien.assignment.inbound.adapter.out.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Repository;

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

	private final ReceivedMessageRepository messages;

	private final MessageOrderRepository messageOrders;

	JpaMessageStore(ReceivedMessageRepository messages, MessageOrderRepository messageOrders) {
		this.messages = messages;
		this.messageOrders = messageOrders;
	}

	@Override
	public Optional<ReceivedMessage> findLatestByKey(MessageKey key) {
		return this.messages.findFirstByMessageKeyOrderByIdDesc(key.value()).map(JpaMessageStore::toModel);
	}

	@Override
	public void insert(ReceivedMessage m) {
		this.em.persist(new ReceivedMessageEntity(m.id(), m.key().value(), m.receivedAt(), m.contentType(),
				m.rawBody(), m.state().name()));
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
			.filter(m -> MessageState.COMPLETED.name().equals(m.state()) && m.responseBody() != null)
			.map(m -> JSON.readValue(m.responseBody(), ReceiveResult.class));
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
		return this.messageOrders.findByOrderIdIn(orderIds).stream().map(JpaMessageStore::toModel).toList();
	}

	@Override
	public void insertAssigned(List<AssignedOrder> orders) {
		for (AssignedOrder o : orders) {
			this.em.persist(new MessageOrderEntity(o.orderId(), o.messageId(), o.seq(), o.userId(), o.itemId(),
					o.receivedAt()));
		}
	}

	@Override
	public Optional<MessageSummary> findSummary(String messageId) {
		return this.messages.findById(messageId).map(JpaMessageStore::toSummary);
	}

	@Override
	public List<MessageSummary> findSummaries(MessageState state, Instant from, Instant to, String after, int limit) {
		StringBuilder jpql = new StringBuilder("select m from ReceivedMessageEntity m where 1 = 1");
		Map<String, Object> params = new LinkedHashMap<>();
		if (state != null) {
			jpql.append(" and m.state = :state");
			params.put("state", state.name());
		}
		if (from != null) {
			jpql.append(" and m.receivedAt >= :from");
			params.put("from", from);
		}
		if (to != null) {
			jpql.append(" and m.receivedAt < :to");
			params.put("to", to);
		}
		if (after != null) {
			jpql.append(" and m.id > :after");
			params.put("after", after);
		}
		jpql.append(" order by m.id asc");
		var query = this.em.createQuery(jpql.toString(), ReceivedMessageEntity.class).setMaxResults(limit);
		params.forEach(query::setParameter);
		return query.getResultList().stream().map(JpaMessageStore::toSummary).toList();
	}

	private static MessageSummary toSummary(ReceivedMessageEntity e) {
		MessageState state = MessageState.valueOf(e.state());
		ReceiveResult result = null;
		String errorCode = null;
		String errorDetail = null;
		if (e.responseBody() != null) {
			if (state == MessageState.COMPLETED) {
				result = JSON.readValue(e.responseBody(), ReceiveResult.class);
			}
			else {
				JsonNode body = JSON.readTree(e.responseBody());
				errorCode = body.path("code").asString(null);
				errorDetail = body.path("detail").asString(null);
			}
		}
		return new MessageSummary(e.id(), state, e.responseStatus(), e.receivedAt(), e.completedAt(), errorCode,
				errorDetail, result);
	}

	private ReceivedMessageEntity find(String messageId) {
		return this.messages.findById(messageId)
			.orElseThrow(() -> new IllegalStateException("수신 기록이 없습니다: " + messageId));
	}

	private static ReceivedMessage toModel(ReceivedMessageEntity e) {
		return new ReceivedMessage(e.id(), new MessageKey(e.messageKey()), e.receivedAt(), e.contentType(),
				e.rawBody(), MessageState.valueOf(e.state()), e.responseStatus(), e.completedAt());
	}

	private static AssignedOrder toModel(MessageOrderEntity e) {
		return new AssignedOrder(e.orderId(), e.messageId(), e.seq(), e.userId(), e.itemId(), e.receivedAt());
	}

}
