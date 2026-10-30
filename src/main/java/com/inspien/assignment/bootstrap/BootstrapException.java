package com.inspien.assignment.bootstrap;

/**
 * 부트스트랩(과제 API 호출, 복호화, 파일 저장) 과정의 실패.
 * 메시지는 콘솔에 그대로 출력되므로 비밀번호나 접속 정보 같은 민감한 값을 담지 않는다.
 */
public class BootstrapException extends RuntimeException {

	public BootstrapException(String message) {
		super(message);
	}

	public BootstrapException(String message, Throwable cause) {
		super(message, cause);
	}

}
