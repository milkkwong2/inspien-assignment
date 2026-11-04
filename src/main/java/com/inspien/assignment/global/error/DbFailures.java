package com.inspien.assignment.global.error;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Oracle 오류를 "연결·자원 장애"와 "DB가 거절한 오류"로 나눈다 (D-17, D-22, D-25).
 *
 * <ul>
 * <li>연결·자원 장애: 다시 하면 풀릴 수 있다. 수신은 503, 스케줄러는 연결 실패(횟수를 세지 않고 주기 종료).</li>
 * <li>그 외: DB가 이 요청을 거절했다(길이 초과, 권한 없음, 제약 위반). 수신은 500 ORDER_DB_REJECTED, 스케줄러는 건별
 * 실패.</li>
 * </ul>
 *
 * <p>기준은 로컬 Oracle에서 실험한 결과다 (D-25). 예외 타입 하나로는 나눌 수 없어 원인 사슬 전체를 본다.
 * <ul>
 * <li>ORA-12899(길이 초과)·ORA-41900(권한 없음)은 무결성 위반이 아니라 JpaSystemException으로 나온다.</li>
 * <li>응답 시간 초과로 연결이 끊기면 롤백도 실패해, 트랜잭션 밖에서는 원래 예외 대신 "Connection is closed"
 * 롤백 예외(hibernate TransactionException)가 보인다.</li>
 * </ul>
 *
 * <p>트랜잭션 바깥(Facade, 주기 도구 CycleRunner)에서만 부른다. 위의 이유로 트랜잭션 안에서는 원인이 보이지 않는다.
 * Oracle 포트(OrderStore·ShipmentStore)의 실패 약속이 Spring DB 예외 계층이라, 포트 구현의 영속성 기술이 바뀌어도 이
 * 분류는 그대로다 (D-25 2026-11-04 보완).
 */
public final class DbFailures {

	private DbFailures() {
	}

	public static boolean isConnectionOrResource(Throwable ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (isConnectionOrResourceType(cause)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isConnectionOrResourceType(Throwable cause) {
		// 풀 대기 초과, 접속 거부 (트랜잭션 시작 시점)
		return cause instanceof CannotCreateTransactionException
				// 작업 중 연결 끊김·응답 시간 초과, 커밋 중 시간 초과(결과 불명)
				|| cause instanceof DataAccessResourceFailureException
				|| cause instanceof RecoverableDataAccessException
				|| cause instanceof TransientDataAccessResourceException || cause instanceof QueryTimeoutException
				// 끊긴 연결에서 롤백·커밋이 실패해 원래 예외를 덮어쓴 경우
				|| cause instanceof org.hibernate.TransactionException
				|| cause instanceof SQLRecoverableException || cause instanceof SQLTransientConnectionException
				|| cause instanceof SQLNonTransientConnectionException
				// SQLState 08xxx: 연결 예외 클래스
				|| (cause instanceof SQLException sql && sql.getSQLState() != null
						&& sql.getSQLState().startsWith("08"))
				|| cause instanceof SocketTimeoutException || cause instanceof ConnectException;
	}

}
