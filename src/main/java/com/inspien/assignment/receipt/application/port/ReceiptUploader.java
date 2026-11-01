package com.inspien.assignment.receipt.application.port;

import java.util.Map;

/**
 * 회계팀 FTP. 권한은 목록 조회와 신규 업로드뿐이다 (덮어쓰기·다운로드·삭제·이름 변경 불가).
 */
public interface ReceiptUploader {

	/**
	 * 주기당 한 번, 처리할 주문이 있을 때만 맺는다 (D-12).
	 *
	 * @throws ConnectionFailedException 접속·로그인·경로 이동 실패. 주문의 문제가 아니다
	 */
	Session open();

	interface Session extends AutoCloseable {

		/**
		 * 업로드 경로의 파일명 → 크기.
		 *
		 * @throws ConnectionFailedException 목록을 받지 못함
		 */
		Map<String, Long> list();

		/**
		 * @throws UploadFailedException 이 파일을 올리지 못함
		 */
		void upload(String fileName, byte[] content);

		@Override
		void close();

	}

	class ConnectionFailedException extends RuntimeException {

		public ConnectionFailedException(String message, Throwable cause) {
			super(message, cause);
		}

	}

	/**
	 * 연결된 뒤 그 파일의 업로드가 실패했다 (건별 실패).
	 */
	class UploadFailedException extends RuntimeException {

		private final boolean connectionLost;

		/**
		 * @param connectionLost 도중에 연결이 끊김. 원인이 파일인지 네트워크인지 구분할 수 없어 건별 실패 1회를 세고
		 * 주기를 끝낸다
		 */
		public UploadFailedException(String message, boolean connectionLost, Throwable cause) {
			super(message, cause);
			this.connectionLost = connectionLost;
		}

		public boolean connectionLost() {
			return this.connectionLost;
		}

	}

}
