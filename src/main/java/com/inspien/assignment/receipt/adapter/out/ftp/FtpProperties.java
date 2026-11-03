package com.inspien.assignment.receipt.adapter.out.ftp;

import java.nio.charset.Charset;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.inspien.assignment.global.config.PropertyChecks;

/**
 * FTP (회계팀 영수증). 타임아웃은 상대가 응답 없이 멈췄을 때 실패로 바꾸려고 둔다 (D-12, D-19). 접속 정보는 프로필이
 * 채운다. 비밀번호가 들어 있으므로 toString은 가린다.
 *
 * @param filenameEncoding 제어 연결 인코딩(한글 파일명). 제공 환경에서 UTF-8로 확인했다 (설계서 §11.1)
 */
@ConfigurationProperties("app.ftp")
public record FtpProperties(String host, int port, String username, String password, String path,
		Duration connectTimeout, Duration commandTimeout, Duration dataTimeout, Charset filenameEncoding) {

	public FtpProperties {
		PropertyChecks.requiredFromProfile("app.ftp.host", host);
		PropertyChecks.positive("app.ftp.port", port);
		PropertyChecks.requiredFromProfile("app.ftp.username", username);
		PropertyChecks.requiredFromProfile("app.ftp.password", password);
		PropertyChecks.requiredFromProfile("app.ftp.path", path);
		PropertyChecks.positive("app.ftp.connect-timeout", connectTimeout);
		PropertyChecks.positive("app.ftp.command-timeout", commandTimeout);
		PropertyChecks.positive("app.ftp.data-timeout", dataTimeout);
		PropertyChecks.required("app.ftp.filename-encoding", filenameEncoding);
	}

	@Override
	public String toString() {
		return "FtpProperties[host=" + this.host + ", port=" + this.port + ", username=" + this.username
				+ ", password=****, path=" + this.path + "]";
	}

}
