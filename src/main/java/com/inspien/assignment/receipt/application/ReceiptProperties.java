package com.inspien.assignment.receipt.application;

import java.nio.charset.Charset;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.inspien.assignment.global.config.PropertyChecks;

/**
 * 영수증 CSV (D-09). 문자셋은 Charset으로 받아 이름이 틀리면 바인딩할 때(기동할 때) 실패한다.
 *
 * @param contentCharset 내용 인코딩
 * @param bom BOM을 붙일지. 없으면 한국어 엑셀이 CP949로 읽어 한글이 깨진다
 */
@ConfigurationProperties("app.receipt")
public record ReceiptProperties(Charset contentCharset, boolean bom) {

	public ReceiptProperties {
		PropertyChecks.required("app.receipt.content-charset", contentCharset);
	}

}
