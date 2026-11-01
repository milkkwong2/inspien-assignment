package com.inspien.assignment.global.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * DB 3개의 DataSource·엔티티 매니저를 같은 방식으로 만든다.
 *
 * <p>기본 매니저(@Primary)를 두지 않으므로 Spring Boot의 JPA 자동 설정이 빠진다. 그래서 Hibernate 속성을 여기서 직접
 * 지정한다 (D-19).
 */
final class JpaSupport {

	private JpaSupport() {
	}

	static HikariDataSource oracleDataSource(String poolName, AppProperties.Db db, AppProperties.Oracle oracle) {
		HikariDataSource dataSource = new HikariDataSource();
		dataSource.setPoolName(poolName);
		dataSource.setJdbcUrl(db.url());
		dataSource.setUsername(db.username());
		dataSource.setPassword(db.password());
		// 고정 크기 풀: 공용 계정이라 붙잡는 연결 수를 정해 둔다
		dataSource.setMaximumPoolSize(db.poolSize());
		dataSource.setMinimumIdle(db.poolSize());
		dataSource.setConnectionTimeout(oracle.poolWait().toMillis());
		dataSource.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(oracle.connectTimeout().toMillis()));
		dataSource.addDataSourceProperty("oracle.jdbc.ReadTimeout", String.valueOf(oracle.readTimeout().toMillis()));
		return dataSource;
	}

	static LocalContainerEntityManagerFactoryBean entityManagerFactory(String unitName, DataSource dataSource,
			String... packagesToScan) {
		LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
		factory.setPersistenceUnitName(unitName);
		factory.setDataSource(dataSource);
		factory.setPackagesToScan(packagesToScan);
		factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		Map<String, Object> properties = new HashMap<>();
		// 제공 Oracle에는 DDL 권한이 없다. H2 스키마도 Hibernate가 아니라 schema-h2.sql이 만든다
		properties.put("hibernate.hbm2ddl.auto", "none");
		factory.setJpaPropertyMap(properties);
		return factory;
	}

}
