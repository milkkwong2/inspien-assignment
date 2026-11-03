package com.inspien.assignment.inbound.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.inspien.assignment.order.domain.Order;

/**
 * HEADER와 ITEM을 USER_ID로 잇고 검증해, 저장할 주문·거부·경고로 나눈다 (D-07).
 *
 * <p>위치가 아니라 USER_ID로 잇는다. 샘플에서 HEADER 바로 아래 ITEM이 다른 사용자의 것인 경우가 많다. 같은 바이트면 항상
 * 같은 결과가 나와, 재처리 때 저장해 둔 원본을 다시 조립해도 순서와 내용이 같다 (D-16).
 *
 * @param accepted 저장할 주문 (ITEM 순서)
 * @param rejected 거부한 ITEM (ITEM 순서)
 * @param warnings 경고
 */
public record OrderAssembly(List<Accepted> accepted, List<Rejected> rejected, List<Warning> warnings) {

	private static final Pattern DIGITS = Pattern.compile("^\\d+$");

	public static OrderAssembly of(OrderMessage message, ValueLimit limit) {
		Map<String, List<OrderMessage.Header>> headersByUser = new LinkedHashMap<>();
		List<Warning> warnings = new ArrayList<>();
		for (OrderMessage.Header header : message.headers()) {
			if (header.userId() == null) {
				warnings.add(new Warning(null, IssueReason.MISSING_USER_ID));
				continue;
			}
			headersByUser.computeIfAbsent(header.userId(), (key) -> new ArrayList<>()).add(header);
		}

		List<Accepted> accepted = new ArrayList<>();
		List<Rejected> rejected = new ArrayList<>();
		Set<String> usersWithItems = new LinkedHashSet<>();
		for (OrderMessage.Item item : message.items()) {
			if (item.userId() != null) {
				usersWithItems.add(item.userId());
			}
			List<OrderMessage.Header> headers = (item.userId() != null) ? headersByUser.get(item.userId()) : null;
			Optional<Rejected> rejection = findRejection(item, headers, limit);
			if (rejection.isPresent()) {
				rejected.add(rejection.get());
			}
			else {
				accepted.add(new Accepted(item, headers.getFirst()));
			}
		}

		for (String userId : headersByUser.keySet()) {
			if (!usersWithItems.contains(userId)) {
				warnings.add(new Warning(userId, IssueReason.HEADER_WITHOUT_ITEM));
			}
		}
		return new OrderAssembly(List.copyOf(accepted), List.copyOf(rejected), List.copyOf(warnings));
	}

	/**
	 * 이 ITEM을 거부할 사유를 찾는다. 사유가 없으면 저장할 수 있고, 그때 같은 USER_ID의 HEADER는 정확히 하나다.
	 */
	private static Optional<Rejected> findRejection(OrderMessage.Item item, List<OrderMessage.Header> headers,
			ValueLimit limit) {
		if (item.userId() == null) {
			return Optional.of(Rejected.of(item, IssueReason.MISSING_USER_ID, null));
		}
		if (item.itemId() == null) {
			return Optional.of(Rejected.of(item, IssueReason.MISSING_ITEM_ID, null));
		}
		if (headers == null) {
			return Optional.of(Rejected.of(item, IssueReason.HEADER_NOT_FOUND, null));
		}
		if (headers.size() > 1) {
			return Optional.of(Rejected.of(item, IssueReason.DUPLICATE_HEADER, null));
		}
		if (item.price() == null || !DIGITS.matcher(item.price()).matches()) {
			return Optional.of(Rejected.of(item, IssueReason.PRICE_NOT_NUMBER, null));
		}
		OrderMessage.Header header = headers.getFirst();
		Map<String, String> values = new LinkedHashMap<>();
		values.put("USER_ID", item.userId());
		values.put("ITEM_ID", item.itemId());
		values.put("NAME", header.name());
		values.put("ADDRESS", header.address());
		values.put("ITEM_NAME", item.itemName());
		values.put("PRICE", item.price());
		for (Map.Entry<String, String> value : values.entrySet()) {
			if (limit.exceeds(value.getValue())) {
				return Optional.of(Rejected.of(item, IssueReason.VALUE_TOO_LONG, value.getKey()));
			}
		}
		return Optional.empty();
	}

	/**
	 * 저장할 주문 = ITEM + 같은 USER_ID의 HEADER.
	 */
	public record Accepted(OrderMessage.Item item, OrderMessage.Header header) {

		public int seq() {
			return this.item.seq();
		}

		/**
		 * 정규 모델로 바꾼다. 주문자 정보는 HEADER, 상품 정보는 ITEM에서 온다 (D-07).
		 */
		public Order toOrder(String orderId) {
			return Order.received(orderId, this.item.userId(), this.item.itemId(), this.header.name(),
					this.header.address(), this.item.itemName(), this.item.price());
		}

	}

	/**
	 * @param field VALUE_TOO_LONG일 때 넘친 필드 이름
	 */
	public record Rejected(int seq, String userId, String itemId, IssueReason reason, String field) {

		static Rejected of(OrderMessage.Item item, IssueReason reason, String field) {
			return new Rejected(item.seq(), item.userId(), item.itemId(), reason, field);
		}

	}

	public record Warning(String userId, IssueReason reason) {
	}

}
