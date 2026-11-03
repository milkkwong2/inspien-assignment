package com.inspien.assignment.bootstrap;

/**
 * 과제 API가 2xx가 아닌 상태로 응답했다.
 *
 * <p>오류 본문은 보낸 개인정보(이름, 전화번호, 이메일)나 서버 내부 정보를 되돌려줄 수 있으므로 메시지에 넣지 않는다.
 * 본문은 {@link BootstrapProcess}가 파일로 남긴다.
 */
public class RecruitingApiErrorException extends BootstrapException {

	private final String body;

	public RecruitingApiErrorException(int statusCode, String body) {
		super("과제 API 응답 오류: HTTP " + statusCode);
		this.body = body;
	}

	public String body() {
		return this.body;
	}

}
