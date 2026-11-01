package com.inspien.assignment.inbound.adapter.out.xml;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.inspien.assignment.inbound.domain.MessageParseException;
import com.inspien.assignment.inbound.domain.OrderMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 샘플과 같은 특징(선언·루트 없음, EUC-KR, 순서 섞임)을 가진 가상 데이터로 검증한다 (D-20).
 */
class XmlOrderMessageParserTest {

	static final Charset EUC_KR = Charset.forName("EUC-KR");

	static final Charset MS949 = Charset.forName("x-windows-949");

	static final String SAMPLE_LIKE = """
			<HEADER>
			    <USER_ID>USER01</USER_ID>
			    <NAME>김민수</NAME>
			    <ADDRESS>서울특별시 강남구</ADDRESS>
			</HEADER>

			<ITEM>
			    <USER_ID>USER02</USER_ID>
			    <ITEM_ID>ITEM20</ITEM_ID>
			    <ITEM_NAME>무선 마우스</ITEM_NAME>
			    <PRICE>25000</PRICE>
			</ITEM>
			<HEADER><USER_ID>USER02</USER_ID><NAME>이서연</NAME><ADDRESS>부산광역시 해운대구</ADDRESS></HEADER>
			<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM10</ITEM_ID><ITEM_NAME>노트북 거치대, 블랙</ITEM_NAME><PRICE>32000</PRICE></ITEM>
			""";

	final XmlOrderMessageParser parser = new XmlOrderMessageParser("EUC-KR");

	@Test
	void readsSampleShapeWithoutRootOrDeclarationAsEucKr() {
		OrderMessage message = this.parser.parse(SAMPLE_LIKE.getBytes(EUC_KR), null);

		assertThat(message.headers()).containsExactly(new OrderMessage.Header("USER01", "김민수", "서울특별시 강남구"),
				new OrderMessage.Header("USER02", "이서연", "부산광역시 해운대구"));
		assertThat(message.items()).containsExactly(
				new OrderMessage.Item(1, "USER02", "ITEM20", "무선 마우스", "25000"),
				new OrderMessage.Item(2, "USER01", "ITEM10", "노트북 거치대, 블랙", "32000"));
	}

	@Test
	void readsExtendedHangulWithEucKrLabelAsMs949() {
		// "똠"은 EUC-KR(완성형 2,350자)에 없고 MS949에만 있다
		byte[] body = "<HEADER><USER_ID>U1</USER_ID><NAME>김똠</NAME></HEADER>".getBytes(MS949);

		assertThat(this.parser.parse(body, "application/xml; charset=EUC-KR").headers().getFirst().name())
			.isEqualTo("김똠");
	}

	@Test
	void contentTypeCharsetWinsOverDeclaration() {
		byte[] body = "<?xml version=\"1.0\" encoding=\"EUC-KR\"?><HEADER><USER_ID>U1</USER_ID><NAME>홍길동</NAME></HEADER>"
			.getBytes(StandardCharsets.UTF_8);

		assertThat(this.parser.parse(body, "text/plain;charset=\"utf-8\"").headers().getFirst().name())
			.isEqualTo("홍길동");
	}

	@Test
	void declarationEncodingIsUsedWhenHeaderHasNone() {
		byte[] body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><HEADER><USER_ID>U1</USER_ID><NAME>홍길동</NAME></HEADER>"
			.getBytes(StandardCharsets.UTF_8);

		assertThat(this.parser.parse(body, "application/xml").headers().getFirst().name()).isEqualTo("홍길동");
	}

	@Test
	void utf8BomWinsOverEverything() {
		byte[] text = "<HEADER><USER_ID>U1</USER_ID><NAME>홍길동</NAME></HEADER>".getBytes(StandardCharsets.UTF_8);
		byte[] body = new byte[text.length + 3];
		body[0] = (byte) 0xEF;
		body[1] = (byte) 0xBB;
		body[2] = (byte) 0xBF;
		System.arraycopy(text, 0, body, 3, text.length);

		assertThat(this.parser.parse(body, "application/xml; charset=EUC-KR").headers().getFirst().name())
			.isEqualTo("홍길동");
	}

	@Test
	void unreadableBytesAreRejectedWithPosition() {
		byte[] body = "<HEADER><NAME>홍</NAME></HEADER>".getBytes(StandardCharsets.UTF_8);

		// UTF-8 한글을 EUC-KR(기본값)로 읽으면 읽을 수 없는 바이트가 나온다. 대체 문자로 바꾸지 않는다
		assertThatThrownBy(() -> new XmlOrderMessageParser("EUC-KR").parse(body, "application/xml; charset=UTF-8x"))
			.isInstanceOf(MessageParseException.class)
			.hasMessageContaining("허용하지 않는 문자셋");
		assertThatThrownBy(() -> this.parser.parse(new byte[] { '<', 'A', '>', (byte) 0xFF, (byte) 0xFF }, null))
			.isInstanceOf(MessageParseException.class)
			.hasMessageContaining("3번째 바이트");
	}

	@ParameterizedTest
	@ValueSource(strings = { "ISO-8859-1", "UTF-16", "US-ASCII", "nope" })
	void otherCharsetsAreRejected(String charset) {
		assertThatThrownBy(() -> this.parser.parse("<ITEM/>".getBytes(StandardCharsets.US_ASCII),
				"application/xml; charset=" + charset))
			.isInstanceOf(MessageParseException.class)
			.hasMessageContaining("허용하지 않는 문자셋");
	}

	@Test
	void doctypeIsRejected() {
		String xxe = "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><HEADER><NAME>&e;</NAME></HEADER>";

		assertThatThrownBy(() -> this.parser.parse(xxe.getBytes(StandardCharsets.UTF_8), "charset=UTF-8"))
			.isInstanceOf(MessageParseException.class);
	}

	@Test
	void xmlVersionOtherThan10IsRejected() {
		byte[] body = "<?xml version=\"1.1\"?><HEADER/>".getBytes(StandardCharsets.UTF_8);

		assertThatThrownBy(() -> this.parser.parse(body, null)).isInstanceOf(MessageParseException.class)
			.hasMessageContaining("1.1");
	}

	@Test
	void malformedXmlIsRejectedWithLineAndColumn() {
		assertThatThrownBy(() -> this.parser.parse("<HEADER><NAME>홍</HEADER>".getBytes(EUC_KR), null))
			.isInstanceOf(MessageParseException.class)
			.hasMessageContaining("행")
			.hasMessageContaining("열");
	}

	@Test
	void onlyTopLevelHeadersAndItemsCount() {
		String body = """
				<ITEM><USER_ID>U1</USER_ID><ITEM_ID>I1</ITEM_ID>
				  <HEADER><USER_ID>U1</USER_ID><NAME>끼어든 헤더</NAME></HEADER>
				</ITEM>
				<UNKNOWN>무시</UNKNOWN>
				""";

		OrderMessage message = this.parser.parse(body.getBytes(EUC_KR), null);

		assertThat(message.headers()).isEmpty();
		assertThat(message.items()).hasSize(1);
	}

	@Test
	void blankOrMissingValuesAreNull() {
		OrderMessage message = this.parser
			.parse("<ITEM><USER_ID>  U1 </USER_ID><ITEM_ID> </ITEM_ID></ITEM>".getBytes(EUC_KR), null);

		assertThat(message.items().getFirst()).isEqualTo(new OrderMessage.Item(1, "U1", null, null, null));
	}

	@Test
	void secureFactoryCanBeBuilt() {
		assertThat(XmlOrderMessageParser.secureFactory()).isNotNull();
	}

}
