package com.inspien.assignment.inbound.adapter.out.xml;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.inspien.assignment.global.config.AppProperties;
import com.inspien.assignment.inbound.application.port.OrderMessageParser;
import com.inspien.assignment.inbound.domain.MessageParseException;
import com.inspien.assignment.inbound.domain.OrderMessage;

/**
 * 주문 XML 파서 (D-20).
 *
 * <pre>
 * 받은 바이트 → 인코딩 결정·엄격 디코딩 → 맨 앞 선언 제거 → &lt;ROOT&gt;…&lt;/ROOT&gt; → DOM 파싱
 * </pre>
 *
 * 송신자는 선언·루트 없이 &lt;HEADER&gt;·&lt;ITEM&gt;을 나열해 보낸다. HEADER·ITEM은 감싼 루트 바로 아래에서만 찾는다.
 * 깊이와 상관없이 찾으면 ITEM 안에 끼어든 &lt;HEADER&gt;도 주문으로 읽힌다.
 */
@Component
class XmlOrderMessageParser implements OrderMessageParser {

	private static final String ROOT = "ROOT";

	private final BodyDecoder decoder;

	private final DocumentBuilderFactory factory;

	@Autowired
	XmlOrderMessageParser(AppProperties properties) {
		this(properties.inbound().defaultCharset());
	}

	XmlOrderMessageParser(String defaultCharset) {
		this.decoder = new BodyDecoder(defaultCharset);
		this.factory = secureFactory();
	}

	/**
	 * OWASP 권고 설정. 하나라도 적용되지 않으면 예외가 나서 앱이 뜨지 않는다. 외부 엔티티(XXE)·엔티티 폭탄이 막히지 않은
	 * 채 도는 것보다 낫다 (D-20).
	 */
	static DocumentBuilderFactory secureFactory() {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			factory.setNamespaceAware(false);
			return factory;
		}
		catch (ParserConfigurationException | IllegalArgumentException ex) {
			throw new IllegalStateException("XML 파서 보안 설정을 적용할 수 없습니다", ex);
		}
	}

	@Override
	public OrderMessage parse(byte[] body, String contentType) {
		String text = this.decoder.decode(body, contentType);
		Element root = parseWrapped(text).getDocumentElement();
		List<OrderMessage.Header> headers = new ArrayList<>();
		List<OrderMessage.Item> items = new ArrayList<>();
		for (Node node = root.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (!(node instanceof Element element)) {
				continue;
			}
			switch (element.getTagName()) {
				case "HEADER" -> headers.add(header(element));
				case "ITEM" -> items.add(item(element, items.size() + 1));
				default -> {
					// 모르는 최상위 요소는 무시한다 (Tolerant Reader, D-04)
				}
			}
		}
		return new OrderMessage(headers, items);
	}

	private Document parseWrapped(String text) {
		try {
			DocumentBuilder builder;
			synchronized (this.factory) {
				builder = this.factory.newDocumentBuilder();
			}
			builder.setErrorHandler(THROWING);
			return builder.parse(new InputSource(new StringReader("<" + ROOT + ">" + text + "</" + ROOT + ">")));
		}
		catch (SAXParseException ex) {
			if (ex.getMessage() != null && ex.getMessage().contains("DOCTYPE")) {
				throw new MessageParseException("DOCTYPE은 허용하지 않습니다.", ex);
			}
			throw new MessageParseException("XML 형식 오류 (%d행 %d열, 루트로 감싼 본문 기준): %s"
				.formatted(ex.getLineNumber(), ex.getColumnNumber(), ex.getMessage()), ex);
		}
		catch (SAXException | IOException | ParserConfigurationException ex) {
			throw new MessageParseException("XML을 읽을 수 없습니다: " + ex.getMessage(), ex);
		}
	}

	/**
	 * 바로 아래 자식 요소의 글자. 앞뒤 공백은 지우고, 요소가 없거나 비어 있으면 null.
	 */
	private static OrderMessage.Header header(Element element) {
		return new OrderMessage.Header(childText(element, "USER_ID"), childText(element, "NAME"),
				childText(element, "ADDRESS"));
	}

	/**
	 * @param seq 본문 안 ITEM 순서 (1부터). 주문번호와 짝을 맞추는 기준이다
	 */
	private static OrderMessage.Item item(Element element, int seq) {
		return new OrderMessage.Item(seq, childText(element, "USER_ID"), childText(element, "ITEM_ID"),
				childText(element, "ITEM_NAME"), childText(element, "PRICE"));
	}

	private static String childText(Element parent, String name) {
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element && element.getTagName().equals(name)) {
				String value = element.getTextContent().strip();
				return value.isEmpty() ? null : value;
			}
		}
		return null;
	}

	/**
	 * 기본 오류 처리기는 경고를 표준 오류로 찍기만 한다. 모두 예외로 바꾼다.
	 */
	private static final ErrorHandler THROWING = new ErrorHandler() {

		@Override
		public void warning(SAXParseException ex) throws SAXException {
			throw ex;
		}

		@Override
		public void error(SAXParseException ex) throws SAXException {
			throw ex;
		}

		@Override
		public void fatalError(SAXParseException ex) throws SAXException {
			throw ex;
		}

	};

}
