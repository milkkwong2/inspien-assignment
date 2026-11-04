package com.inspien.assignment.receipt.application.port;

import com.inspien.assignment.receipt.domain.Receipt;

/**
 * 영수증을 회계팀이 받을 파일 내용으로 바꾼다 (D-09, D-21). 수신 본문 파서(OrderMessageParser)와 방향만 반대인
 * 변환기다. 앱 안에서는 영수증을 정규 모델로만 다루고, 바깥 형식(지금은 CSV)은 구현이 만든다. 받는 쪽이 형식이나
 * 인코딩을 정해 주면 구현만 고친다.
 */
public interface ReceiptFormatter {

	/**
	 * 같은 영수증이면 항상 같은 바이트를 돌려준다. 업로드 전 완전성 확인이 이 길이를 FTP 목록의 크기와 견주기 때문이다
	 * (UploadPlan, D-09). 만든 시각처럼 영수증에 없는 값을 넣지 않는다.
	 */
	byte[] format(Receipt receipt);

}
