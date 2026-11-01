package com.inspien.assignment.receipt.adapter.out.ftp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import org.springframework.stereotype.Component;

import com.inspien.assignment.global.config.AppProperties;
import com.inspien.assignment.receipt.application.port.ReceiptUploader;

/**
 * 회계팀 FTP 어댑터 (Apache Commons Net).
 *
 * <ul>
 * <li>바이너리 모드: ASCII 모드는 줄바꿈을 바꿔 크기가 달라지고, 완전성 확인(예상 크기 비교)이 틀어진다 (D-09).</li>
 * <li>타임아웃: 접속·명령 응답·데이터 전송. 상대가 응답 없이 멈추면 실패로 바꿔 재시도 규칙을 따른다 (D-12, D-19).</li>
 * <li>제어 연결 인코딩은 한글 파일명용 설정값이다. FTP/FTPS 여부와 함께 제공 환경 확인 후 정한다 (설계서 §11).</li>
 * </ul>
 */
@Component
class FtpReceiptUploader implements ReceiptUploader {

	private final AppProperties.Ftp ftp;

	FtpReceiptUploader(AppProperties properties) {
		this.ftp = properties.ftp();
	}

	@Override
	public Session open() {
		FTPClient client = new FTPClient();
		client.setControlEncoding(this.ftp.filenameEncoding());
		client.setConnectTimeout((int) this.ftp.connectTimeout().toMillis());
		client.setDefaultTimeout((int) this.ftp.commandTimeout().toMillis());
		client.setDataTimeout(this.ftp.dataTimeout());
		try {
			client.connect(this.ftp.host(), this.ftp.port());
			require(client, FTPReply.isPositiveCompletion(client.getReplyCode()), "접속 거부");
			client.setSoTimeout((int) this.ftp.commandTimeout().toMillis());
			require(client, client.login(this.ftp.username(), this.ftp.password()), "로그인 실패");
			client.enterLocalPassiveMode();
			require(client, client.setFileType(FTP.BINARY_FILE_TYPE), "바이너리 모드 설정 실패");
			require(client, client.changeWorkingDirectory(this.ftp.path()), "업로드 경로 이동 실패");
			return new FtpSession(client);
		}
		catch (IOException ex) {
			disconnect(client);
			throw new ConnectionFailedException("FTP 접속 실패: " + ex.getMessage(), ex);
		}
	}

	private static void require(FTPClient client, boolean ok, String what) {
		if (!ok) {
			String reply = client.getReplyString();
			disconnect(client);
			throw new ConnectionFailedException("FTP " + what + ": " + ((reply != null) ? reply.strip() : ""), null);
		}
	}

	private static void disconnect(FTPClient client) {
		if (client.isConnected()) {
			try {
				client.disconnect();
			}
			catch (IOException ex) {
				// 이미 실패한 연결이다
			}
		}
	}

	private record FtpSession(FTPClient client) implements Session {

		@Override
		public Map<String, Long> list() {
			try {
				FTPFile[] files = this.client.listFiles();
				if (files == null) {
					throw new ConnectionFailedException("FTP 목록 조회 실패: " + this.client.getReplyString(), null);
				}
				Map<String, Long> listing = new LinkedHashMap<>();
				for (FTPFile file : files) {
					if (file != null && file.isFile()) {
						listing.put(file.getName(), file.getSize());
					}
				}
				return listing;
			}
			catch (IOException ex) {
				throw new ConnectionFailedException("FTP 목록 조회 실패: " + ex.getMessage(), ex);
			}
		}

		@Override
		public void upload(String fileName, byte[] content) {
			try {
				if (!this.client.storeFile(fileName, new ByteArrayInputStream(content))) {
					throw new UploadFailedException(this.client.getReplyString().strip(), false, null);
				}
			}
			catch (IOException ex) {
				throw new UploadFailedException(ex.getClass().getSimpleName() + " " + ex.getMessage(), true, ex);
			}
		}

		@Override
		public void close() {
			try {
				if (this.client.isConnected()) {
					this.client.logout();
				}
			}
			catch (IOException ex) {
				// 업로드는 끝났다. 끊기만 하면 된다
			}
			finally {
				disconnect(this.client);
			}
		}

	}

}
