package com.inspien.assignment.receipt.domain;

import java.util.Map;
import java.util.OptionalInt;

/**
 * 업로드 전 완전성 확인 (설계서 §5.2, D-09). FTP는 지울 수도 덮어쓸 수도 없어 목록으로 판단한다.
 *
 * <pre>
 * 이 주문의 파일 없음                      → 기본 이름으로 업로드
 * 가장 큰 번호 파일의 크기 = 예상 크기     → 이미 업로드됨 (업로드 없이 H2에 영수증 단계 완료만 기록)
 * 가장 큰 번호 파일의 크기 ≠ 예상 크기     → 반쪽 파일. 다음 번호 _r(N+1)로 업로드
 * </pre>
 *
 * 같은 주문은 재시도 번호가 가장 큰 파일이 정본이다.
 *
 * @param fileName 올릴(또는 이미 올린) 파일명
 * @param upload 올려야 하는가
 * @param replacesPartial 반쪽 파일을 대신하는 재업로드인가
 */
public record UploadPlan(String fileName, boolean upload, boolean replacesPartial) {

	/** 이 주문의 파일이 아직 없다. 기본 이름으로 올린다 */
	public static UploadPlan initialUpload(String fileName) {
		return new UploadPlan(fileName, true, false);
	}

	/** 같은 크기의 파일이 이미 있다. 올리지 않는다 */
	public static UploadPlan alreadyUploaded(String fileName) {
		return new UploadPlan(fileName, false, false);
	}

	/** 반쪽 파일이 있다. 다음 번호로 다시 올린다 */
	public static UploadPlan replacePartial(String fileName) {
		return new UploadPlan(fileName, true, true);
	}

	/**
	 * @param listing 업로드 경로의 파일명 → 크기
	 */
	public static UploadPlan of(Receipt receipt, Map<String, Long> listing, long expectedSize) {
		int latest = -1;
		Long latestSize = null;
		for (Map.Entry<String, Long> file : listing.entrySet()) {
			OptionalInt revision = receipt.revisionOf(file.getKey());
			if (revision.isPresent() && revision.getAsInt() > latest) {
				latest = revision.getAsInt();
				latestSize = file.getValue();
			}
		}
		if (latest < 0) {
			return initialUpload(receipt.fileName(0));
		}
		if (latestSize != null && latestSize == expectedSize) {
			return alreadyUploaded(receipt.fileName(latest));
		}
		return replacePartial(receipt.fileName(latest + 1));
	}

}
