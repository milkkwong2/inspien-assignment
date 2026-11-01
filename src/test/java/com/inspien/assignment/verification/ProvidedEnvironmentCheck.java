package com.inspien.assignment.verification;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 제공 환경 읽기 전용 확인 (설계서 §11, D-11). 앱의 일부가 아닌 점검 도구다: {@code ./gradlew checkProvided}
 *
 * <ul>
 * <li>Oracle: 읽기 전용 연결(SET TRANSACTION READ ONLY)에서 SELECT만 하고 끝에 롤백한다.</li>
 * <li>FTP: 로그인, FEAT, 경로 이동, 목록 조회만 한다. 업로드·디렉토리 생성은 하지 않는다.</li>
 * <li>비밀번호, 호스트, 지원자 키, 다른 지원자의 이름·행 값은 출력하지 않는다. 건수와 메타데이터만 출력한다.</li>
 * </ul>
 */
public final class ProvidedEnvironmentCheck {

	private ProvidedEnvironmentCheck() {
	}

	public static void main(String[] args) throws Exception {
		JsonNode conn = JsonMapper.builder().build().readTree(Files.readString(Path.of("config/connection.json")));
		String applicantKey = conn.get("APPLICANT_KEY").asString();
		JsonNode order = conn.get("ORDER_TB_CONN");
		JsonNode shipment = conn.get("SHIPMENT_TB_CONN");
		boolean sameDb = order.get("URL").asString().equals(shipment.get("URL").asString())
				&& order.get("ID").asString().equals(shipment.get("ID").asString());

		section("Oracle (ORDER_TB_CONN)" + (sameDb ? " — SHIPMENT_TB_CONN과 같은 인스턴스·계정" : ""));
		checkOracle(order, applicantKey);
		if (!sameDb) {
			section("Oracle (SHIPMENT_TB_CONN)");
			checkOracle(shipment, applicantKey);
		}
		section("FTP");
		checkFtp(conn.get("FTP_CONN"));
	}

	static void checkOracle(JsonNode db, String applicantKey) {
		Properties props = new Properties();
		props.setProperty("user", db.get("ID").asString());
		props.setProperty("password", db.get("PASSWORD").asString());
		props.setProperty("oracle.net.CONNECT_TIMEOUT", "5000");
		props.setProperty("oracle.jdbc.ReadTimeout", "20000");
		long start = System.nanoTime();
		try (Connection c = DriverManager.getConnection(db.get("URL").asString(), props)) {
			print("접속 시간", millis(start) + "ms");
			c.setAutoCommit(false);
			c.setReadOnly(true);
			try {
				query(c, "버전", "SELECT PRODUCT, VERSION_FULL FROM PRODUCT_COMPONENT_VERSION");
				query(c, "버전(v$version)", "SELECT BANNER_FULL FROM V$VERSION");
				query(c, "DB 문자셋·길이 단위",
						"SELECT PARAMETER, VALUE FROM NLS_DATABASE_PARAMETERS WHERE PARAMETER IN "
								+ "('NLS_CHARACTERSET', 'NLS_NCHAR_CHARACTERSET', 'NLS_LENGTH_SEMANTICS')");
				query(c, "접속 계정", "SELECT USER FROM DUAL");
				query(c, "테이블 소유", "SELECT OWNER, TABLE_NAME FROM ALL_TABLES WHERE TABLE_NAME IN ('ORDER_TB', 'SHIPMENT_TB')");
				query(c, "동의어", "SELECT SYNONYM_NAME, TABLE_OWNER, TABLE_NAME FROM ALL_SYNONYMS "
						+ "WHERE SYNONYM_NAME IN ('ORDER_TB', 'SHIPMENT_TB') AND OWNER IN (USER, 'PUBLIC')");
				query(c, "컬럼 (길이 단위 CHAR_USED: B=바이트, C=글자)",
						"SELECT OWNER, TABLE_NAME, COLUMN_NAME, DATA_TYPE, DATA_LENGTH, CHAR_LENGTH, CHAR_USED, NULLABLE "
								+ "FROM ALL_TAB_COLUMNS WHERE TABLE_NAME IN ('ORDER_TB', 'SHIPMENT_TB') "
								+ "ORDER BY OWNER, TABLE_NAME, COLUMN_ID");
				query(c, "제약", "SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE, SEARCH_CONDITION_VC FROM ALL_CONSTRAINTS "
						+ "WHERE TABLE_NAME IN ('ORDER_TB', 'SHIPMENT_TB') ORDER BY TABLE_NAME, CONSTRAINT_TYPE");
				query(c, "PK 컬럼", "SELECT c.TABLE_NAME, c.CONSTRAINT_NAME, c.COLUMN_NAME, c.POSITION FROM ALL_CONS_COLUMNS c "
						+ "JOIN ALL_CONSTRAINTS k ON k.OWNER = c.OWNER AND k.CONSTRAINT_NAME = c.CONSTRAINT_NAME "
						+ "WHERE k.CONSTRAINT_TYPE = 'P' AND c.TABLE_NAME IN ('ORDER_TB', 'SHIPMENT_TB') "
						+ "ORDER BY c.TABLE_NAME, c.POSITION");
				query(c, "기본값", "SELECT TABLE_NAME, COLUMN_NAME, DATA_DEFAULT FROM ALL_TAB_COLUMNS "
						+ "WHERE TABLE_NAME IN ('ORDER_TB', 'SHIPMENT_TB') AND DATA_DEFAULT IS NOT NULL");
				query(c, "트리거", "SELECT TABLE_NAME, TRIGGER_NAME, TRIGGER_TYPE, TRIGGERING_EVENT, STATUS FROM ALL_TRIGGERS "
						+ "WHERE TABLE_NAME IN ('ORDER_TB', 'SHIPMENT_TB')");
				query(c, "인덱스", "SELECT TABLE_NAME, INDEX_NAME, UNIQUENESS FROM ALL_INDEXES "
						+ "WHERE TABLE_NAME IN ('ORDER_TB', 'SHIPMENT_TB')");
				query(c, "현재 스키마", "SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') FROM DUAL");
				query(c, "내 계정의 테이블 권한", "SELECT TABLE_NAME, PRIVILEGE FROM USER_TAB_PRIVS "
						+ "WHERE TABLE_NAME IN ('ORDER_TB', 'SHIPMENT_TB') ORDER BY TABLE_NAME, PRIVILEGE");
				query(c, "세션 한도", "SELECT RESOURCE_NAME, LIMIT FROM USER_RESOURCE_LIMITS "
						+ "WHERE RESOURCE_NAME = 'SESSIONS_PER_USER'");
				query(c, "프로필 세션 한도", "SELECT p.RESOURCE_NAME, p.LIMIT FROM DBA_PROFILES p JOIN USER_USERS u "
						+ "ON p.PROFILE = u.PROFILE WHERE p.RESOURCE_NAME = 'SESSIONS_PER_USER'");
				query(c, "ORDER_TB 전체 (건수, 지원자 수)",
						"SELECT COUNT(*), COUNT(DISTINCT APPLICANT_KEY) FROM ORDER_TB");
				query(c, "ORDER_TB 상태값 분포 (전체)", "SELECT STATUS, COUNT(*) FROM ORDER_TB GROUP BY STATUS ORDER BY 2 DESC");
				query(c, "SHIPMENT_TB 전체 (건수, 지원자 수)",
						"SELECT COUNT(*), COUNT(DISTINCT APPLICANT_KEY) FROM SHIPMENT_TB");
				queryWithKey(c, "내 ORDER_TB (건수, 최대 ORDER_ID)",
						"SELECT COUNT(*), MAX(ORDER_ID) FROM ORDER_TB WHERE APPLICANT_KEY = ?", applicantKey);
				queryWithKey(c, "내 SHIPMENT_TB (건수, 최대 SHIPMENT_ID)",
						"SELECT COUNT(*), MAX(SHIPMENT_ID) FROM SHIPMENT_TB WHERE APPLICANT_KEY = ?", applicantKey);
				long t = System.nanoTime();
				query(c, null, "SELECT 1 FROM DUAL");
				print("단순 쿼리 응답 시간", millis(t) + "ms");
			}
			finally {
				c.rollback();
			}
		}
		catch (SQLException ex) {
			print("접속 실패", "ORA-" + ex.getErrorCode() + " " + firstLine(ex.getMessage()) + " (" + millis(start) + "ms)");
		}
	}

	static void checkFtp(JsonNode ftp) {
		String path = ftp.get("PATH").asString();
		FTPClient client = new FTPClient();
		client.setConnectTimeout(5000);
		client.setDefaultTimeout(20000);
		// 서버가 보낸 파일명 바이트를 그대로 받아 어떤 인코딩인지 판단한다
		client.setControlEncoding("ISO-8859-1");
		long start = System.nanoTime();
		try {
			client.connect(ftp.get("URL").asString(), Integer.parseInt(ftp.get("PORT").asString()));
			print("접속", client.getReplyCode() + " (" + millis(start) + "ms)");
			print("로그인", String.valueOf(client.login(ftp.get("ID").asString(), ftp.get("PASSWORD").asString())));
			print("SYST", firstLine(client.getSystemType()));
			client.feat();
			print("FEAT", client.getReplyString().replace("\r\n", " | ").strip());
			print("AUTH TLS(FTPS) 지원", String.valueOf(client.getReplyString().contains("AUTH TLS")));
			print("UTF8 지원", String.valueOf(client.getReplyString().contains("UTF8")));
			print("로그인 디렉토리", client.printWorkingDirectory());
			print("업로드 경로 이동", path + " → " + client.changeWorkingDirectory(path));
			client.enterLocalPassiveMode();
			long t = System.nanoTime();
			FTPFile[] files = client.listFiles();
			print("목록 조회", ((files != null) ? files.length : -1) + "개 (" + millis(t) + "ms)");
			if (files != null) {
				describeNames(files);
			}
			client.logout();
		}
		catch (IOException ex) {
			print("FTP 실패", ex.getClass().getSimpleName() + " " + ex.getMessage());
		}
		finally {
			if (client.isConnected()) {
				try {
					client.disconnect();
				}
				catch (IOException ex) {
					// 확인용 연결이다
				}
			}
		}
	}

	/**
	 * 파일명 바이트가 UTF-8인지 EUC-KR(MS949)인지 센다. 이름 자체는 출력하지 않고 한글을 ○로 가린 모양만 보여 준다.
	 */
	static void describeNames(FTPFile[] files) {
		int nonAscii = 0;
		int utf8 = 0;
		int ms949 = 0;
		int receiptPrefix = 0;
		List<String> shapes = new ArrayList<>();
		for (FTPFile file : files) {
			byte[] raw = file.getName().getBytes(StandardCharsets.ISO_8859_1);
			boolean ascii = true;
			for (byte b : raw) {
				ascii &= (b >= 0);
			}
			if (ascii) {
				continue;
			}
			nonAscii++;
			String asUtf8 = strict(raw, StandardCharsets.UTF_8);
			String asMs949 = strict(raw, Charset.forName("x-windows-949"));
			if (asUtf8 != null) {
				utf8++;
			}
			if (asMs949 != null) {
				ms949++;
			}
			String readable = (asUtf8 != null) ? asUtf8 : asMs949;
			if (readable != null && readable.startsWith("영수증_")) {
				receiptPrefix++;
			}
			if (readable != null && shapes.size() < 5) {
				shapes.add(readable.replaceAll("[가-힣]", "○"));
			}
		}
		print("비ASCII 파일명", nonAscii + "개");
		print("UTF-8로 읽히는 이름", utf8 + "개");
		print("MS949로 읽히는 이름", ms949 + "개");
		print("'영수증_'으로 시작", receiptPrefix + "개");
		print("이름 모양 (한글은 ○)", String.join(", ", shapes));
	}

	static String strict(byte[] raw, Charset charset) {
		try {
			return charset.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(raw))
				.toString();
		}
		catch (CharacterCodingException ex) {
			return null;
		}
	}

	static void query(Connection c, String title, String sql) {
		try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
			printRows(title, rs);
		}
		catch (SQLException ex) {
			if (title != null) {
				print(title, "조회 불가: ORA-" + ex.getErrorCode() + " " + firstLine(ex.getMessage()));
			}
		}
	}

	static void queryWithKey(Connection c, String title, String sql, String key) {
		try (PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, key);
			try (ResultSet rs = ps.executeQuery()) {
				printRows(title, rs);
			}
		}
		catch (SQLException ex) {
			print(title, "조회 불가: ORA-" + ex.getErrorCode() + " " + firstLine(ex.getMessage()));
		}
	}

	static void printRows(String title, ResultSet rs) throws SQLException {
		if (title == null) {
			return;
		}
		ResultSetMetaData meta = rs.getMetaData();
		List<String> rows = new ArrayList<>();
		while (rs.next()) {
			List<String> values = new ArrayList<>();
			for (int i = 1; i <= meta.getColumnCount(); i++) {
				values.add(String.valueOf(rs.getString(i)));
			}
			rows.add(String.join(" | ", values));
		}
		System.out.println("- " + title + ":" + (rows.isEmpty() ? " (없음)" : ""));
		rows.forEach(row -> System.out.println("    " + row));
	}

	static void section(String title) {
		System.out.println();
		System.out.println("## " + title);
	}

	static void print(String title, String value) {
		System.out.println("- " + title + ": " + value);
	}

	static long millis(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}

	static String firstLine(String text) {
		if (text == null) {
			return "";
		}
		int newline = text.indexOf('\n');
		return ((newline < 0) ? text : text.substring(0, newline)).strip();
	}

}
