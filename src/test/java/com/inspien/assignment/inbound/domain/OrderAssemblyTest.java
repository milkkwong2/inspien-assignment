package com.inspien.assignment.inbound.domain;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.inspien.assignment.inbound.domain.OrderAssembly.Rejected;
import com.inspien.assignment.inbound.domain.OrderAssembly.Warning;
import com.inspien.assignment.inbound.domain.OrderMessage.Header;
import com.inspien.assignment.inbound.domain.OrderMessage.Item;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAssemblyTest {

	static final ValueLimit LIMIT = new ValueLimit(StandardCharsets.UTF_8, 100);

	@Test
	void joinsByUserIdNotByPosition() {
		// 설계서 §5.1 예시: HEADER 바로 아래 ITEM이 다른 사용자의 것
		OrderMessage message = new OrderMessage(
				List.of(new Header("USER01", "김민수", "서울"), new Header("USER02", "이서연", "부산"),
						new Header("USER03", "박지훈", "대전")),
				List.of(new Item(1, "USER02", "ITEM20", "무선 마우스", "25000"),
						new Item(2, "USER01", "ITEM10", "노트북 거치대, 블랙", "32000"),
						new Item(3, "USER01", "ITEM11", "USB 허브", "18000"),
						new Item(4, "USER09", "ITEM30", "키보드", "45000")));

		OrderAssembly assembly = OrderAssembly.of(message, LIMIT);

		assertThat(assembly.accepted()).extracting(a -> a.seq() + ":" + a.header().name())
			.containsExactly("1:이서연", "2:김민수", "3:김민수");
		assertThat(assembly.rejected())
			.containsExactly(new Rejected(4, "USER09", "ITEM30", IssueReason.HEADER_NOT_FOUND, null));
		assertThat(assembly.warnings()).containsExactly(new Warning("USER03", IssueReason.HEADER_WITHOUT_ITEM));
	}

	@Test
	void rejectsIncompleteOrders() {
		OrderMessage message = new OrderMessage(
				List.of(new Header("U1", "김", "서울"), new Header("U2", "이", "부산"), new Header("U2", "이", "대구"),
						new Header(null, "익명", "어딘가")),
				List.of(new Item(1, null, "I1", "상품", "1000"), new Item(2, "U1", null, "상품", "1000"),
						new Item(3, "U2", "I3", "상품", "1000"), new Item(4, "U1", "I4", "상품", "1,000"),
						new Item(5, "U1", "I5", "상품", null), new Item(6, "U1", "I6", "가".repeat(34), "1000"),
						new Item(7, "U1", "I7", "가".repeat(33), "1000")));

		OrderAssembly assembly = OrderAssembly.of(message, LIMIT);

		assertThat(assembly.rejected()).extracting(Rejected::seq, Rejected::reason, Rejected::field)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(1, IssueReason.MISSING_USER_ID, null),
					org.assertj.core.groups.Tuple.tuple(2, IssueReason.MISSING_ITEM_ID, null),
					org.assertj.core.groups.Tuple.tuple(3, IssueReason.DUPLICATE_HEADER, null),
					org.assertj.core.groups.Tuple.tuple(4, IssueReason.PRICE_NOT_NUMBER, null),
					org.assertj.core.groups.Tuple.tuple(5, IssueReason.PRICE_NOT_NUMBER, null),
					org.assertj.core.groups.Tuple.tuple(6, IssueReason.VALUE_TOO_LONG, "ITEM_NAME"));
		// 99바이트(한글 33자)는 통과한다
		assertThat(assembly.accepted()).extracting(OrderAssembly.Accepted::seq).containsExactly(7);
		assertThat(assembly.warnings()).containsExactly(new Warning(null, IssueReason.MISSING_USER_ID));
	}

	@Test
	void sameMessageAlwaysAssemblesTheSame() {
		OrderMessage message = new OrderMessage(List.of(new Header("U1", "김", "서울")),
				List.of(new Item(1, "U1", "I1", "상품", "1000"), new Item(2, "U1", "I2", "상품", "2000")));

		assertThat(OrderAssembly.of(message, LIMIT)).isEqualTo(OrderAssembly.of(message, LIMIT));
	}

}
