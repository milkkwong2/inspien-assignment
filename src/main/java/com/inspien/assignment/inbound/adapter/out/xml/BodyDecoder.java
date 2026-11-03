package com.inspien.assignment.inbound.adapter.out.xml;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.inspien.assignment.inbound.domain.MessageParseException;

/**
 * 본문 바이트의 인코딩을 정하고 엄격하게 디코딩한다 (D-20).
 *
 * <pre>
 * ① BOM → ② Content-Type의 charset → ③ XML 선언의 encoding → ④ 기본값(EUC-KR)
 * </pre>
 *
 * ④는 의도적으로 XML 표준(UTF-8)과 다르다. 이 송신자는 표시 없이 EUC-KR로 보낸다. 선언은 디코딩 전에 바이트에서
 * 읽는다. 허용하는 문자셋이 모두 ASCII와 호환되어 선언은 바이트 그대로 읽힌다.
 */
final class BodyDecoder {

	/**
	 * EUC-KR 계열은 MS949로 읽는다. EUC-KR은 "똠" 같은 확장 완성형 글자가 없고, 레거시가 "EUC-KR"이라고 내보내는
	 * 데이터에는 확장 완성형이 섞이는 경우가 많다. Charset.forName("CP949")는 MS949가 아니라 IBM949라 쓰지 않는다.
	 */
	static final Charset MS949 = Charset.forName("x-windows-949");

	private static final Map<String, Charset> ALLOWED = Map.of("UTF-8", StandardCharsets.UTF_8, "UTF8",
			StandardCharsets.UTF_8, "EUC-KR", MS949, "KS_C_5601-1987", MS949, "MS949", MS949, "CP949", MS949,
			"X-WINDOWS-949", MS949);

	private static final byte[] UTF8_BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

	private static final Pattern CHARSET_PARAM = Pattern.compile("(?i);\\s*charset\\s*=\\s*\"?([^\";\\s]+)\"?");

	private static final Pattern DECLARATION = Pattern.compile("^<\\?xml\\s[^>]*?\\?>");

	/**
	 * XML 선언은 본문 맨 앞에만 올 수 있어 앞부분만 본다. 가장 긴 선언(version·encoding·standalone)도 100바이트를 넘지
	 * 않아 넉넉히 잡았다. 본문 전체를 읽으면 큰 메시지에서 낭비다.
	 */
	private static final int DECLARATION_SCAN_BYTES = 200;

	private static final Pattern DECLARED_VERSION = Pattern.compile("version\\s*=\\s*[\"']([^\"']*)[\"']");

	private static final Pattern DECLARED_ENCODING = Pattern.compile("encoding\\s*=\\s*[\"']([^\"']*)[\"']");

	private final String defaultCharset;

	BodyDecoder(String defaultCharset) {
		this.defaultCharset = defaultCharset;
		resolve(defaultCharset, "기본 문자셋 설정");
	}

	/**
	 * @return 맨 앞의 XML 선언을 지운 문자열
	 */
	String decode(byte[] body, String contentType) {
		int offset = 0;
		String label;
		String source;
		if (startsWith(body, UTF8_BOM)) {
			offset = UTF8_BOM.length;
			label = "UTF-8";
			source = "BOM";
		}
		else if (isUtf16Bom(body)) {
			throw new MessageParseException("허용하지 않는 문자셋입니다: UTF-16 (BOM). UTF-8 또는 EUC-KR로 보내 주세요.");
		}
		else {
			label = charsetOf(contentType);
			source = "Content-Type";
			if (label == null) {
				label = declaredEncoding(body);
				source = "XML 선언";
			}
			if (label == null) {
				label = this.defaultCharset;
				source = "기본값";
			}
		}
		Charset charset = resolve(label, source);
		String text = strictDecode(body, offset, charset, label);
		return removeDeclaration(text);
	}

	private static Charset resolve(String label, String source) {
		Charset charset = ALLOWED.get(label.strip().toUpperCase(Locale.ROOT));
		if (charset == null) {
			throw new MessageParseException(
					"허용하지 않는 문자셋입니다: " + label + " (" + source + "). UTF-8 또는 EUC-KR로 보내 주세요.");
		}
		return charset;
	}

	static String charsetOf(String contentType) {
		if (contentType == null) {
			return null;
		}
		Matcher matcher = CHARSET_PARAM.matcher(contentType);
		return matcher.find() ? matcher.group(1) : null;
	}

	/**
	 * 본문 맨 앞의 선언에서 encoding을 바이트 그대로 읽는다.
	 */
	private static String declaredEncoding(byte[] body) {
		String head = new String(body, 0, Math.min(body.length, DECLARATION_SCAN_BYTES), StandardCharsets.ISO_8859_1);
		Matcher declaration = DECLARATION.matcher(head);
		if (!declaration.find()) {
			return null;
		}
		Matcher encoding = DECLARED_ENCODING.matcher(declaration.group());
		return encoding.find() ? encoding.group(1) : null;
	}

	/**
	 * 대체 문자 없이 디코딩한다. 깨진 글자를 저장하면 지울 수 없는 ORDER_TB에 남는다 (D-07, D-20).
	 */
	private static String strictDecode(byte[] body, int offset, Charset charset, String label) {
		CharsetDecoder decoder = charset.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT);
		ByteBuffer in = ByteBuffer.wrap(body, offset, body.length - offset);
		CharBuffer out = CharBuffer.allocate((int) Math.ceil((body.length - offset) * (double) decoder.maxCharsPerByte()) + 1);
		CoderResult result = decoder.decode(in, out, true);
		if (result.isError()) {
			throw new MessageParseException(
					"%s로 읽을 수 없는 바이트가 있습니다: %d번째 바이트(0부터)".formatted(label, in.position()));
		}
		result = decoder.flush(out);
		if (result.isError()) {
			throw new MessageParseException("%s로 읽을 수 없는 바이트가 있습니다: 본문 끝".formatted(label));
		}
		return out.flip().toString();
	}

	/**
	 * 맨 앞의 선언 하나만 확인하고 지운다. 남긴 채 감싸면 선언이 루트 안에 들어가 파싱 오류가 난다.
	 */
	private static String removeDeclaration(String text) {
		Matcher declaration = DECLARATION.matcher(text);
		if (!declaration.find()) {
			return text;
		}
		Matcher version = DECLARED_VERSION.matcher(declaration.group());
		if (version.find() && !"1.0".equals(version.group(1))) {
			throw new MessageParseException("XML 버전 " + version.group(1) + "은 받지 않습니다. 1.0으로 보내 주세요.");
		}
		return text.substring(declaration.end());
	}

	private static boolean startsWith(byte[] body, byte[] prefix) {
		if (body.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (body[i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}

	private static boolean isUtf16Bom(byte[] body) {
		return body.length >= 2 && ((body[0] == (byte) 0xFE && body[1] == (byte) 0xFF)
				|| (body[0] == (byte) 0xFF && body[1] == (byte) 0xFE));
	}

}
