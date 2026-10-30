package com.inspien.assignment.bootstrap;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import tools.jackson.databind.JsonNode;

/**
 * 부트스트랩 전체 흐름. 과제 API 호출 → 원본 응답 저장 → 복호화 → 결과 저장.
 *
 * <p>원본 응답을 복호화보다 먼저 저장해서, 복호화 단계가 실패해도 API를 다시 부르지 않고
 * {@link Mode#DECRYPT_ONLY}로 다시 처리할 수 있게 한다.
 */
public final class BootstrapProcess {

	public enum Mode {

		/** 과제 API를 호출한다. 이미 받은 응답이 있으면 거부한다. */
		FETCH,

		/** API를 호출하지 않고 저장된 응답으로 복호화만 다시 한다. */
		DECRYPT_ONLY

	}

	private final BootstrapInput input;

	private final BootstrapStore store;

	private final Function<BootstrapInput, String> apiCall;

	private final PrintStream out;

	public BootstrapProcess(BootstrapInput input, BootstrapStore store, Function<BootstrapInput, String> apiCall,
			PrintStream out) {
		this.input = input;
		this.store = store;
		this.apiCall = apiCall;
		this.out = out;
	}

	public ResponseProcessor.Result run(Mode mode) {
		if (mode == Mode.FETCH) {
			this.store.ensureNoRawResponse();
			this.out.println("과제 API 호출: " + this.input.apiUrl());
			String body = this.apiCall.apply(this.input);
			this.store.saveRawResponse(body);
			this.out.println("원본 응답 저장: " + this.store.rawResponsePath());
		}
		ResponseProcessor.Result result = new ResponseProcessor(new ConnInfoDecryptor(this.input.phoneNumber()))
			.process(this.store.readRawResponse());
		this.store.saveConnection(result.connectionJson());
		this.store.saveSampleOrder(result.sampleOrder());
		printSummary(result);
		return result;
	}

	/**
	 * 접속 정보 값은 출력하지 않고, 어떤 항목이 들어 있는지만 보여준다.
	 */
	private void printSummary(ResponseProcessor.Result result) {
		this.out.println("APPLICANT_KEY: " + mask(result.applicantKey()));
		for (String field : ResponseProcessor.CONNECTION_FIELDS) {
			this.out.println(field + " 복호화 완료: " + describe(result.connection().get(field)));
		}
		this.out.println("접속 정보 저장: " + this.store.connectionPath());
		this.out.println("샘플 주문 XML 저장 (EUC-KR, " + result.sampleOrder().length + " bytes): "
				+ this.store.sampleOrderPath());
	}

	private static String describe(JsonNode node) {
		if (node.isObject()) {
			List<String> names = new ArrayList<>(node.propertyNames());
			return "항목 " + names;
		}
		return node.getNodeType() + " (" + node.asString().length() + "자)";
	}

	static String mask(String value) {
		if (value.length() <= 4) {
			return "****";
		}
		return value.substring(0, 4) + "*".repeat(value.length() - 4);
	}

}
