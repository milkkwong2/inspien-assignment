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

import com.inspien.assignment.global.transaction.ShipmentTx;

/**
 * 운송회사 Oracle (SHIPMENT_TB) (D-02). 쇼핑몰 DB처럼 Spring Data 리포지토리가 없어 엔티티 매니저의 스캔만 둔다.
 */
@Configuration(proxyBeanMethods = false)
class ShipmentDbConfig {

	static final String PERSISTENCE_PACKAGE = "com.inspien.assignment.shipment.adapter.out.persistence";

	@Bean(destroyMethod = "close")
	HikariDataSource shipmentDataSource(AppProperties properties) {
		return JpaSupport.oracleDataSource("shipment-db", properties.shipmentDb(), properties.oracle());
	}

	@Bean
	LocalContainerEntityManagerFactoryBean shipmentEntityManagerFactory(
			@Qualifier("shipmentDataSource") DataSource dataSource, ApplicantProperties applicant) {
		return JpaSupport.sharedTableEntityManagerFactory("shipment", dataSource, applicant.key(),
				PERSISTENCE_PACKAGE);
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
