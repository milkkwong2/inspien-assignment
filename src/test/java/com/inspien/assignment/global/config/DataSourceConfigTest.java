package com.inspien.assignment.global.config;

import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.inspien.assignment.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class DataSourceConfigTest {

	@Autowired
	@Qualifier("orderDataSource")
	DataSource orderDataSource;

	@Autowired
	@Qualifier("historyDataSource")
	DataSource historyDataSource;

	@Autowired
	Map<String, PlatformTransactionManager> transactionManagers;

	@Test
	void hasThreeNamedTransactionManagersWithoutDefault() {
		assertThat(this.transactionManagers).containsOnlyKeys("orderTx", "shipmentTx", "historyTx");
	}

	@Test
	void appAccountCannotDeleteOrCreateTables() {
		JdbcTemplate oracle = new JdbcTemplate(this.orderDataSource);

		assertThat(oracle.queryForObject("SELECT COUNT(*) FROM ORDER_TB WHERE APPLICANT_KEY = 'OTHER-APPLICANT-2'",
				Integer.class))
			.isEqualTo(150);
		assertThatThrownBy(() -> oracle.update("DELETE FROM ORDER_TB WHERE ORDER_ID = 'NONE'"))
			.hasMessageContaining("ORA-41900");
		assertThatThrownBy(() -> oracle.execute("CREATE TABLE T1 (A NUMBER)")).rootCause()
			.hasMessageContaining("ORA-01031");
	}

	@Test
	void historyDbHasSchemaAndWritesImmediately() {
		JdbcTemplate h2 = new JdbcTemplate(this.historyDataSource);

		assertThat(h2.queryForList("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
				String.class))
			.contains("RECEIVED_MESSAGE", "MESSAGE_ORDER", "ORDER_STAGE", "PROCESS_EVENT");
		assertThat(h2.queryForList(
				"SELECT DISTINCT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME = 'WRITE_DELAY'",
				String.class))
			.containsExactly("0");
	}

}
