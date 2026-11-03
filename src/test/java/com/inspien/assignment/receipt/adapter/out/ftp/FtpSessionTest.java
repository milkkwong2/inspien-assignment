package com.inspien.assignment.receipt.adapter.out.ftp;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.Map;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.junit.jupiter.api.Test;

import com.inspien.assignment.receipt.application.port.ReceiptUploader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 데이터 연결(LIST·STOR) 실패를 응답 코드로 가르는지 확인한다 (D-10, D-22). 실제 서버에서 425·426을 재현하기 어려워
 * {@code FTPClient}를 흉내 낸다. 흉내는 commons-net 3.13의 동작을 따른다: 데이터 연결을 열지 못하면 빈 목록·{@code false}를
 * 돌려주고, 전송을 마치면 마지막 응답(226·426)을 이미 읽어 둔다.
 */
class FtpSessionTest {

	final FTPClient client = mock(FTPClient.class);

	final FtpReceiptUploader.FtpSession session = new FtpReceiptUploader.FtpSession(this.client);

	@Test
	void listWithoutDataConnectionIsConnectionFailure() throws IOException {
		when(this.client.listFiles()).thenReturn(new FTPFile[0]);
		replies(FTPReply.CANNOT_OPEN_DATA_CONNECTION, "425 Can't open data connection.");

		assertThatThrownBy(this.session::list).isInstanceOf(ReceiptUploader.ConnectionFailedException.class)
			.hasMessageContaining("425");
	}

	@Test
	void listAbortedMidTransferIsConnectionFailure() throws IOException {
		FTPFile partial = file("영수증_홍길동_A001.csv", 10);
		when(this.client.listFiles()).thenReturn(new FTPFile[] { partial });
		replies(FTPReply.TRANSFER_ABORTED, "426 Connection closed; transfer aborted.");

		assertThatThrownBy(this.session::list).isInstanceOf(ReceiptUploader.ConnectionFailedException.class)
			.hasMessageContaining("426");
	}

	@Test
	void listCompletedReturnsFilesOnly() throws IOException {
		FTPFile receipt = file("영수증_홍길동_A001.csv", 10);
		FTPFile folder = new FTPFile();
		folder.setName("old");
		folder.setType(FTPFile.DIRECTORY_TYPE);
		when(this.client.listFiles()).thenReturn(new FTPFile[] { receipt, folder });
		replies(FTPReply.CLOSING_DATA_CONNECTION, "226 Directory send OK.");

		Map<String, Long> listing = this.session.list();

		assertThat(listing).containsExactly(Map.entry("영수증_홍길동_A001.csv", 10L));
	}

	@Test
	void listIoErrorIsConnectionFailure() throws IOException {
		when(this.client.listFiles()).thenThrow(new SocketException("Connection reset"));

		assertThatThrownBy(this.session::list).isInstanceOf(ReceiptUploader.ConnectionFailedException.class);
	}

	@Test
	void uploadWithoutDataConnectionIsConnectionFailure() throws IOException {
		when(this.client.storeFile(anyString(), any(InputStream.class))).thenReturn(false);
		replies(FTPReply.CANNOT_OPEN_DATA_CONNECTION, "425 Can't open data connection.");

		assertThatThrownBy(() -> this.session.upload("영수증_홍길동_A001.csv", new byte[] { 1 }))
			.isInstanceOf(ReceiptUploader.ConnectionFailedException.class)
			.hasMessageContaining("425");
	}

	@Test
	void uploadAbortedMidTransferCountsOnceAndEndsCycle() throws IOException {
		when(this.client.storeFile(anyString(), any(InputStream.class))).thenReturn(false);
		replies(FTPReply.TRANSFER_ABORTED, "426 Failure writing network stream.");

		assertThatThrownBy(() -> this.session.upload("영수증_홍길동_A001.csv", new byte[] { 1 }))
			.isInstanceOfSatisfying(ReceiptUploader.UploadFailedException.class,
					ex -> assertThat(ex.connectionLost()).isTrue())
			.hasMessageContaining("426");
	}

	@Test
	void uploadRejectedByServerIsOrderFailure() throws IOException {
		when(this.client.storeFile(anyString(), any(InputStream.class))).thenReturn(false);
		replies(FTPReply.FILE_NAME_NOT_ALLOWED, "553 Could not create file.");

		assertThatThrownBy(() -> this.session.upload("영수증_홍길동_A001.csv", new byte[] { 1 }))
			.isInstanceOfSatisfying(ReceiptUploader.UploadFailedException.class,
					ex -> assertThat(ex.connectionLost()).isFalse())
			.hasMessageContaining("553");
	}

	@Test
	void uploadIoErrorIsConnectionLost() throws IOException {
		when(this.client.storeFile(anyString(), any(InputStream.class)))
			.thenThrow(new SocketException("Broken pipe"));

		assertThatThrownBy(() -> this.session.upload("영수증_홍길동_A001.csv", new byte[] { 1 }))
			.isInstanceOfSatisfying(ReceiptUploader.UploadFailedException.class,
					ex -> assertThat(ex.connectionLost()).isTrue());
	}

	@Test
	void uploadStoredDoesNotThrow() throws IOException {
		when(this.client.storeFile(anyString(), any(InputStream.class))).thenReturn(true);
		replies(FTPReply.CLOSING_DATA_CONNECTION, "226 Transfer complete.");

		this.session.upload("영수증_홍길동_A001.csv", new byte[] { 1 });
	}

	private void replies(int code, String text) {
		when(this.client.getReplyCode()).thenReturn(code);
		when(this.client.getReplyString()).thenReturn(text + "\r\n");
	}

	private static FTPFile file(String name, long size) {
		FTPFile file = new FTPFile();
		file.setName(name);
		file.setType(FTPFile.FILE_TYPE);
		file.setSize(size);
		return file;
	}

}
