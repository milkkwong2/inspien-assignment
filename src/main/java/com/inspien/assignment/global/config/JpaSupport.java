package com.inspien.assignment.global.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

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

	/**
	 * 공용 테이블(Oracle)용 엔티티 매니저. 엔티티의 @TenantId 컬럼(APPLICANT_KEY)에 내 지원자 키를 쓴다.
	 *
	 * <p>Hibernate가 모든 JPQL·QueryDSL 조회와 벌크 갱신, PK 조회의 WHERE에 APPLICANT_KEY 조건을 붙이고, 저장할 때 값을
	 * 채운다. 복합 PK 구성 컬럼에서도 동작함을 로컬 Oracle에서 확인했다 (D-01, D-25). 네이티브 SQL에는 붙지 않으므로
	 * Oracle 어댑터는 네이티브 SQL을 쓰지 않는다 (ArchitectureTest).
	 */
	static LocalContainerEntityManagerFactoryBean sharedTableEntityManagerFactory(String unitName,
			DataSource dataSource, String applicantKey, String... packagesToScan) {
		LocalContainerEntityManagerFactoryBean factory = entityManagerFactory(unitName, dataSource, packagesToScan);
		factory.getJpaPropertyMap()
			.put("hibernate.tenant_identifier_resolver", new FixedTenantResolver(applicantKey));
		return factory;
	}

	/**
	 * 앱 하나가 지원자 하나를 대신하므로 테넌트는 고정이다.
	 */
	record FixedTenantResolver(String applicantKey) implements CurrentTenantIdentifierResolver<String> {

		@Override
		public String resolveCurrentTenantIdentifier() {
			return this.applicantKey;
		}

		@Override
		public boolean validateExistingCurrentSessions() {
			return false;
		}

	}

}
