-- 공용 테이블 재현: 다른 지원자의 행을 미리 넣는다 (D-14).
-- 앱이 APPLICANT_KEY 조건을 빠뜨리면 이 행들을 읽거나 바꾸게 되므로 테스트에서 드러난다.
-- - OTHER-APPLICANT-1: 내 번호와 겹치는 ORDER_ID(A001~)와 여러 상태
-- - OTHER-APPLICANT-2: RECEIVED 주문 150건. 스케줄러 한 주기 처리 건수(100)보다 많아,
--   조회 후에 거르면 내 주문이 영영 처리되지 않는 문제가 드러난다 (D-21)

ALTER SESSION SET CONTAINER = FREEPDB1;

INSERT INTO TOTO.ORDER_TB VALUES ('A001', 'OTHER-APPLICANT-1', 'USER01', 'ITEM01', '김철수', '서울특별시 강남구', '선글라스', '95000', 'SHIPPED');
INSERT INTO TOTO.ORDER_TB VALUES ('A002', 'OTHER-APPLICANT-1', 'USER02', 'ITEM02', '이영희', '서울특별시 서초구', '청바지', '33000', 'RECEIPT_SENT');
INSERT INTO TOTO.ORDER_TB VALUES ('A003', 'OTHER-APPLICANT-1', 'USER03', 'ITEM03', '박민수', '부산광역시 해운대구', '샌들', '27000', 'RECEIVED');
INSERT INTO TOTO.ORDER_TB VALUES ('B500', 'OTHER-APPLICANT-1', 'USER04', 'ITEM04', '최지우', '대전광역시 유성구', '텀블러', '13000', 'RECEIPT_FAILED');

INSERT INTO TOTO.SHIPMENT_TB VALUES ('A001', 'OTHER-APPLICANT-1', 'A001', 'ITEM01', '서울특별시 강남구');

BEGIN
	FOR i IN 1..150 LOOP
		INSERT INTO TOTO.ORDER_TB VALUES ('A' || LPAD(i, 3, '0'), 'OTHER-APPLICANT-2', 'USER' || LPAD(MOD(i, 20), 2, '0'),
			'ITEM' || LPAD(i, 3, '0'), '홍길순', '인천광역시 연수구', '상품' || i, TO_CHAR(i * 1000), 'RECEIVED');
	END LOOP;
END;
/

COMMIT;
