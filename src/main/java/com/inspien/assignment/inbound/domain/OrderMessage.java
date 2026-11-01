package com.inspien.assignment.inbound.domain;

import java.util.List;

/**
 * 파싱한 주문 메시지. 본문에 나온 순서대로의 HEADER와 ITEM이다. 서로 잇는 것은 {@link OrderAssembly}가 한다.
 *
 * <p>값은 앞뒤 공백을 지운 문자열이고, 태그가 없거나 비어 있으면 null이다.
 */
public record OrderMessage(List<Header> headers, List<Item> items) {

	public OrderMessage {
		headers = List.copyOf(headers);
		items = List.copyOf(items);
	}

	public record Header(String userId, String name, String address) {
	}

	/**
	 * @param seq 본문 안 ITEM 순서 (1부터). 거부된 ITEM은 주문번호가 없어 순서로 특정한다
	 */
	public record Item(int seq, String userId, String itemId, String itemName, String price) {
	}

}
