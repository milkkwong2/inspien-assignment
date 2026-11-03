package com.inspien.assignment.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.inspien.assignment.receipt.application.port.ReceiptUploader;

/**
 * 메모리 FTP. 실제 FTP와 같이 같은 이름에는 다시 올릴 수 없다.
 */
public class FakeFtp implements ReceiptUploader {

	public final Map<String, byte[]> files = new LinkedHashMap<>();

	public final List<String> uploads = new ArrayList<>();

	public volatile boolean down;

	public volatile String failUploadsContaining;

	public volatile int opened;

	@Override
	public Session open() {
		if (this.down) {
			throw new ConnectionFailedException("FTP 접속 실패: 연결 거부", null);
		}
		this.opened++;
		return new Session() {

			@Override
			public Map<String, Long> list() {
				Map<String, Long> listing = new LinkedHashMap<>();
				FakeFtp.this.files.forEach((name, content) -> listing.put(name, (long) content.length));
				return listing;
			}

			@Override
			public void upload(String fileName, byte[] content) {
				if (FakeFtp.this.failUploadsContaining != null && fileName.contains(FakeFtp.this.failUploadsContaining)) {
					throw UploadFailedException.rejected("553 Could not create file.");
				}
				if (FakeFtp.this.files.containsKey(fileName)) {
					throw UploadFailedException.rejected("553 Could not create file. (덮어쓰기 불가)");
				}
				FakeFtp.this.files.put(fileName, content);
				FakeFtp.this.uploads.add(fileName);
			}

			@Override
			public void close() {
			}

		};
	}

}
