package com.inspien.assignment.global.config;

import javax.sql.DataSource;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import com.inspien.assignment.global.transaction.OrderTx;

/**
 * 쇼핑몰 Oracle (ORDER_TB). 운송회사 DB와 같은 인스턴스라도 서로 다른 시스템으로 보고 분리한다 (D-02).
 *
 * <p>어댑터가 QueryDSL과 엔티티 매니저만 쓰고 Spring Data 리포지토리가 없어 {@code @EnableJpaRepositories}를 두지
 * 않는다. 엔티티는 엔티티 매니저가 이 패키지를 스캔해 찾는다 (D-01).
 */
@Configuration(proxyBeanMethods = false)
class OrderDbConfig {

	static final String PERSISTENCE_PACKAGE = "com.inspien.assignment.order.adapter.out.persistence";

	@Bean(destroyMethod = "close")
	HikariDataSource orderDataSource(AppProperties properties) {
		return JpaSupport.oracleDataSource("order-db", properties.orderDb(), properties.oracle());
	}

	@Bean
	LocalContainerEntityManagerFactoryBean orderEntityManagerFactory(@Qualifier("orderDataSource") DataSource dataSource,
			ApplicantProperties applicant) {
		return JpaSupport.sharedTableEntityManagerFactory("order", dataSource, applicant.key(),
				PERSISTENCE_PACKAGE);
	}

	@Bean(OrderTx.MANAGER)
	JpaTransactionManager orderTransactionManager(
			@Qualifier("orderEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
		return new JpaTransactionManager(entityManagerFactory);
	}

	@Bean
	JPAQueryFactory orderQueryFactory(@Qualifier("orderEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
		return new JPAQueryFactory(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
	}

}
