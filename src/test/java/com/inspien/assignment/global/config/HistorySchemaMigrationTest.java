package com.inspien.assignment.global.config;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기존 H2 파일에 지금 스키마를 실행하면 새 컬럼이 더해지고 기존 행이 채워진다 (D-27). 스키마는 기동마다 실행된다.
 */
class HistorySchemaMigrationTest {

	@Test
	void addsCommonTimeColumnsToExistingFileAndFillsOldRows() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:migration;DB_CLOSE_DELAY=-1");
		JdbcTemplate h2 = new JdbcTemplate(dataSource);
		run(dataSource, "h2/schema-before-d27.sql");
		h2.update("INSERT INTO ORDER_STAGE (ORDER_ID, STAGE, UPDATED_AT) VALUES ('A001', 'RECEIPT', CURRENT_TIMESTAMP)");
		h2.update("INSERT INTO PROCESS_EVENT (ID, OCCURRED_AT, RESULT) VALUES ('0000000000001', CURRENT_TIMESTAMP, 'SUCCESS')");

		run(dataSource, "schema-h2.sql");
		run(dataSource, "schema-h2.sql");

		for (String table : new String[] { "RECEIVED_MESSAGE", "MESSAGE_ORDER", "ORDER_STAGE", "PROCESS_EVENT" }) {
			assertThat(h2.queryForList("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?",
					String.class, table))
				.as(table)
				.contains("CREATED_AT", "UPDATED_AT");
		}
		assertThat(h2.queryForObject("SELECT CREATED_AT FROM ORDER_STAGE", OffsetDateTime.class)).isNotNull();
		assertThat(h2.queryForObject("SELECT CREATED_AT FROM PROCESS_EVENT", OffsetDateTime.class)).isNotNull();
	}

	private static void run(DriverManagerDataSource dataSource, String script) {
		DatabasePopulatorUtils.execute(new ResourceDatabasePopulator(new ClassPathResource(script)), dataSource);
	}

}
