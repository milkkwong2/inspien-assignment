package com.inspien.assignment.bootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 과제 API 원본 응답을 풀어 APPLICANT_KEY, 복호화한 접속 정보, 샘플 XML을 만든다.
 *
 * <p>접속 정보 필드는 명세상 "{암호화 정보}"로만 적혀 있어 형태가 확실하지 않다. 그래서 두 형태를 모두 받는다.
 * <ul>
 * <li>문자열: 통째로 복호화한다. 결과가 JSON이면 객체로, 아니면 문자열로 저장한다.</li>
 * <li>객체: 문자열 값을 각각 복호화한다.</li>
 * </ul>
 * 본 애플리케이션이 읽을 형식은 실제 응답을 확인한 뒤 정한다.
 */
public final class ResponseProcessor {

	static final String APPLICANT_KEY = "APPLICANT_KEY";

	static final List<String> CONNECTION_FIELDS = List.of("ORDER_TB_CONN", "SHIPMENT_TB_CONN", "FTP_CONN");

	static final String SAMPLE_DATA = "SAMPLE_DATA";

	private final ConnInfoDecryptor decryptor;

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public ResponseProcessor(ConnInfoDecryptor decryptor) {
		this.decryptor = decryptor;
	}

	public Result process(String rawResponse) {
		JsonNode root = parse(rawResponse);
		requireFields(root);
		ObjectNode connection = this.jsonMapper.createObjectNode();
		connection.put(APPLICANT_KEY, root.get(APPLICANT_KEY).asString());
		for (String field : CONNECTION_FIELDS) {
			connection.set(field, decryptField(field, root.get(field)));
		}
		byte[] sampleOrder = SampleDataDecoder.decodeBytes(root.get(SAMPLE_DATA).asString());
		String connectionJson = this.jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(connection);
		return new Result(root.get(APPLICANT_KEY).asString(), connection, connectionJson, sampleOrder);
	}

	private JsonNode parse(String rawResponse) {
		try {
			JsonNode root = this.jsonMapper.readTree(rawResponse);
			if (root == null || !root.isObject()) {
				throw new BootstrapException("과제 API 응답이 JSON 객체가 아닙니다");
			}
			return root;
		}
		catch (JacksonException ex) {
			throw new BootstrapException("과제 API 응답을 JSON으로 읽지 못했습니다 (원본은 raw-response.json에 저장되어 있습니다)", ex);
		}
	}

	private static void requireFields(JsonNode root) {
		List<String> missing = new ArrayList<>();
		for (String field : requiredFields()) {
			JsonNode node = root.get(field);
			if (node == null || node.isNull()) {
				missing.add(field);
			}
		}
		if (!missing.isEmpty()) {
			throw new BootstrapException("과제 API 응답에 필드가 없습니다: " + missing);
		}
	}

	private static List<String> requiredFields() {
		List<String> fields = new ArrayList<>();
		fields.add(APPLICANT_KEY);
		fields.addAll(CONNECTION_FIELDS);
		fields.add(SAMPLE_DATA);
		return fields;
	}

	private JsonNode decryptField(String field, JsonNode node) {
		if (node.isString()) {
			return toJsonIfPossible(this.decryptor.decrypt(node.asString()));
		}
		if (node.isObject()) {
			ObjectNode decrypted = this.jsonMapper.createObjectNode();
			for (Map.Entry<String, JsonNode> entry : node.properties()) {
				JsonNode value = entry.getValue();
				decrypted.set(entry.getKey(),
						value.isString() ? toJsonIfPossible(this.decryptor.decrypt(value.asString())) : value);
			}
			return decrypted;
		}
		throw new BootstrapException(field + " 형식을 알 수 없습니다: " + node.getNodeType());
	}

	private JsonNode toJsonIfPossible(String plainText) {
		String trimmed = plainText.strip();
		if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
			try {
				return this.jsonMapper.readTree(trimmed);
			}
			catch (JacksonException ex) {
				// JSON처럼 보이지만 아니면 문자열 그대로 둔다
			}
		}
		return this.jsonMapper.getNodeFactory().stringNode(plainText);
	}

	/**
	 * @param applicantKey 지원자 키
	 * @param connection 복호화한 접속 정보 (APPLICANT_KEY 포함)
	 * @param connectionJson connection.json에 저장할 문자열
	 * @param sampleOrder 샘플 주문 XML 원본 바이트 (EUC-KR)
	 */
	public record Result(String applicantKey, JsonNode connection, String connectionJson, byte[] sampleOrder) {

		@Override
		public String toString() {
			return "Result[applicantKey=" + this.applicantKey + ", sampleOrder=" + this.sampleOrder.length + " bytes]";
		}

	}

}
