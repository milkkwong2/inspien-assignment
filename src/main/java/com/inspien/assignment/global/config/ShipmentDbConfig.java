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

import com.inspien.assignment.global.transaction.ShipmentTx;

/**
 * 운송회사 Oracle (SHIPMENT_TB) (D-02).
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackages = ShipmentDbConfig.PERSISTENCE_PACKAGE,
		entityManagerFactoryRef = "shipmentEntityManagerFactory", transactionManagerRef = ShipmentTx.MANAGER)
class ShipmentDbConfig {

	static final String PERSISTENCE_PACKAGE = "com.inspien.assignment.shipment.adapter.out.persistence";

	@Bean(destroyMethod = "close")
	HikariDataSource shipmentDataSource(AppProperties properties) {
		return JpaSupport.oracleDataSource("shipment-db", properties.shipmentDb(), properties.oracle());
	}

	@Bean
	LocalContainerEntityManagerFactoryBean shipmentEntityManagerFactory(
			@Qualifier("shipmentDataSource") DataSource dataSource) {
		return JpaSupport.entityManagerFactory("shipment", dataSource, PERSISTENCE_PACKAGE);
	}

	@Bean(ShipmentTx.MANAGER)
	JpaTransactionManager shipmentTransactionManager(
			@Qualifier("shipmentEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
		return new JpaTransactionManager(entityManagerFactory);
	}

	@Bean
	JPAQueryFactory shipmentQueryFactory(
			@Qualifier("shipmentEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
		return new JPAQueryFactory(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
	}

}
