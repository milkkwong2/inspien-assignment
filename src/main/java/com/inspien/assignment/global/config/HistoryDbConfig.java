package com.inspien.assignment.global.config;

import java.nio.file.Path;

import javax.sql.DataSource;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import com.inspien.assignment.global.transaction.HistoryTx;

/**
 * 앱 소유 H2 파일 DB: 수신 원본, 채번, 재시도 상태, 처리 이벤트 (D-04, D-13).
 *
 * <p>여러 도메인이 함께 쓰므로 각 도메인의 persistence 패키지를 나열한다. 빠뜨리면 기동 시 실패해 조용히 틀리지 않고,
 * 이 목록이 "어느 도메인이 H2를 쓰는지"를 보여 준다 (D-21).
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(
		basePackages = { HistoryDbConfig.INBOUND, HistoryDbConfig.NUMBERING, HistoryDbConfig.RETRY,
				HistoryDbConfig.TRACKING },
		entityManagerFactoryRef = "historyEntityManagerFactory", transactionManagerRef = HistoryTx.MANAGER)
class HistoryDbConfig {

	static final String INBOUND = "com.inspien.assignment.inbound.adapter.out.persistence";

	static final String NUMBERING = "com.inspien.assignment.numbering.adapter.out.persistence";

	static final String RETRY = "com.inspien.assignment.retry.adapter.out.persistence";

	static final String TRACKING = "com.inspien.assignment.tracking.adapter.out.persistence";

	private static final Logger log = LoggerFactory.getLogger(HistoryDbConfig.class);

	/**
	 * 스키마는 DataSource를 만들 때 바로 실행해, 채번기·스케줄러가 쓰기 전에 끝나게 한다. Oracle에는 어떤 스크립트도 실행하지
	 * 않는다 (D-19).
	 */
	@Bean(destroyMethod = "close")
	HikariDataSource historyDataSource(AppProperties properties) {
		Path file = Path.of(properties.historyDb().path()).toAbsolutePath().normalize();
		HikariDataSource dataSource = new HikariDataSource();
		dataSource.setPoolName("history-db");
		// WRITE_DELAY=0: 커밋 즉시 디스크에 쓴다. 기본값이면 커밋 후 최대 0.5초 동안 안 쓰여서, 그 사이 앱이 죽으면
		// 미리 배정한 주문번호 기록이 사라지고 재전송이 중복 저장된다 (D-19)
		dataSource.setJdbcUrl("jdbc:h2:file:" + file + ";WRITE_DELAY=0");
		dataSource.setUsername("sa");
		log.info("처리 이력 DB(H2): {}.mv.db", file);
		DatabasePopulatorUtils.execute(new ResourceDatabasePopulator(new ClassPathResource("schema-h2.sql")),
				dataSource);
		return dataSource;
	}

	@Bean
	LocalContainerEntityManagerFactoryBean historyEntityManagerFactory(
			@Qualifier("historyDataSource") DataSource dataSource) {
		return JpaSupport.entityManagerFactory("history", dataSource, INBOUND, NUMBERING, RETRY, TRACKING);
	}

	@Bean(HistoryTx.MANAGER)
	JpaTransactionManager historyTransactionManager(
			@Qualifier("historyEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
		return new JpaTransactionManager(entityManagerFactory);
	}

	@Bean
	JPAQueryFactory historyQueryFactory(
			@Qualifier("historyEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
		return new JPAQueryFactory(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
	}

}
