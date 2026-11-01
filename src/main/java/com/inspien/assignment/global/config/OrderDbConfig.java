package com.inspien.assignment.global.config;

import javax.sql.DataSource;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import com.inspien.assignment.global.transaction.OrderTx;

/**
 * 쇼핑몰 Oracle (ORDER_TB). 운송회사 DB와 같은 인스턴스라도 서로 다른 시스템으로 보고 분리한다 (D-02).
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackages = OrderDbConfig.PERSISTENCE_PACKAGE,
		entityManagerFactoryRef = "orderEntityManagerFactory", transactionManagerRef = OrderTx.MANAGER)
class OrderDbConfig {

	static final String PERSISTENCE_PACKAGE = "com.inspien.assignment.order.adapter.out.persistence";

	@Bean(destroyMethod = "close")
	HikariDataSource orderDataSource(AppProperties properties) {
		return JpaSupport.oracleDataSource("order-db", properties.orderDb(), properties.oracle());
	}

	@Bean
	LocalContainerEntityManagerFactoryBean orderEntityManagerFactory(@Qualifier("orderDataSource") DataSource dataSource) {
		return JpaSupport.entityManagerFactory("order", dataSource, PERSISTENCE_PACKAGE);
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
