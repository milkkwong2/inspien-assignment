package com.inspien.assignment.inbound.domain;

/**
 * 거부·경고 사유 코드 (설계서 §5.1, D-07).
 */
public enum IssueReason {

	/** ITEM(거부) 또는 HEADER(경고)에 USER_ID가 없음 */
	MISSING_USER_ID,

	MISSING_ITEM_ID,

	/** 같은 USER_ID의 HEADER가 없어 NAME·ADDRESS를 채울 수 없음 */
	HEADER_NOT_FOUND,

	/** 같은 USER_ID의 HEADER가 둘 이상이라 어느 이름·주소인지 알 수 없음 */
	DUPLICATE_HEADER,

	PRICE_NOT_NUMBER,

	/** 값이 컬럼 길이를 넘음. 필드 이름을 함께 준다 */
	VALUE_TOO_LONG,

	/** 경고: ITEM이 없는 HEADER. 저장할 주문이 없다 */
	HEADER_WITHOUT_ITEM

}
