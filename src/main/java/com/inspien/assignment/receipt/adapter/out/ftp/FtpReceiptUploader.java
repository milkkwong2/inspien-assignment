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

	private final FtpProperties ftp;

	FtpReceiptUploader(FtpProperties ftp) {
		this.ftp = ftp;
	}

	@Override
	public Session open() {
		FTPClient client = new FTPClient();
		client.setControlEncoding(this.ftp.filenameEncoding().name());
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

	/**
	 * 한 주기의 FTP 연결. 데이터 연결(LIST·STOR) 실패는 응답 코드로 가른다 (D-10, D-22).
	 *
	 * <p>{@code FTPClient}는 데이터 연결을 열지 못하면(PASV·명령 응답이 425 등) 예외 없이 빈 목록이나 {@code false}를
	 * 돌려준다. 그 경우와 전송을 마친 뒤의 경우 모두 {@code getReplyCode()}가 그 명령의 마지막 응답이다 (commons-net
	 * 3.13: 전송을 마치면 내부에서 {@code completePendingCommand()}로 226·426 등을 이미 읽는다).
	 */
	record FtpSession(FTPClient client) implements Session {

		/**
		 * 마지막 응답이 완료(2xx)가 아니면 목록을 믿지 않는다. 데이터 연결을 못 열었거나(425) 전송이 중간에 끊긴(426)
		 * 목록으로 판단하면 있는 파일을 없다고 보고 다시 올리려다 주문마다 실패를 센다.
		 *
		 * @throws ConnectionFailedException 목록을 끝까지 받지 못함. 주문의 문제가 아니다
		 */
		@Override
		public Map<String, Long> list() {
			try {
				FTPFile[] files = this.client.listFiles();
				if (files == null) {
					throw new ConnectionFailedException("FTP 목록 조회 실패: " + lastReply(), null);
				}
				int replyCode = this.client.getReplyCode();
				if (!FTPReply.isPositiveCompletion(replyCode)) {
					throw new ConnectionFailedException("FTP 목록 조회 실패: " + lastReply(), null);
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

		/**
		 * 서버가 거절하면 응답 코드로 가른다.
		 * <ul>
		 * <li>425 데이터 연결을 열지 못함: 아무것도 보내지 않았다. 연결 실패로 주기를 끝내고 횟수를 세지 않는다.</li>
		 * <li>426 전송 도중 끊김: 업로드 중 연결이 끊긴 것과 같다. 건별 실패 1회를 세고 주기를 끝낸다.</li>
		 * <li>그 밖(550, 553 등): 서버가 이 파일을 거절했다. 그 주문만 실패다.</li>
		 * </ul>
		 *
		 * @throws ConnectionFailedException 데이터 연결을 열지 못함 (425)
		 * @throws UploadFailedException 이 파일을 올리지 못함
		 */
		@Override
		public void upload(String fileName, byte[] content) {
			boolean stored;
			try {
				stored = this.client.storeFile(fileName, new ByteArrayInputStream(content));
			}
			catch (IOException ex) {
				throw UploadFailedException.connectionLost(ex);
			}
			if (stored) {
				return;
			}
			int replyCode = this.client.getReplyCode();
			if (replyCode == FTPReply.CANNOT_OPEN_DATA_CONNECTION) {
				throw new ConnectionFailedException("FTP 데이터 연결 실패: " + lastReply(), null);
			}
			if (replyCode == FTPReply.TRANSFER_ABORTED) {
				throw UploadFailedException.transferAborted(lastReply());
			}
			throw UploadFailedException.rejected(lastReply());
		}

		private String lastReply() {
			String reply = this.client.getReplyString();
			if (reply == null) {
				return "";
			}
			return reply.strip();
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
