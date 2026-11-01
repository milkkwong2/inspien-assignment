package com.inspien.assignment.verification;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import com.inspien.assignment.support.LocalOracle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검증 (설계서 §11, D-25): Hibernate @TenantId가 복합 PK 구성 컬럼(APPLICANT_KEY)에서 동작한다.
 *
 * <p>다른 지원자의 행이 들어 있는 로컬 Oracle에서 조회·저장·벌크 갱신·PK 조회에 APPLICANT_KEY 조건이 붙는지 확인한다.
 * 앱의 Oracle 엔티티 매니저는 이 방식으로 APPLICANT_KEY를 강제한다.
 */
class TenantIdCompositeKeyTest {

	static final String MINE = "VERIFY-TENANT-1";

	static final ThreadLocal<String> tenant = ThreadLocal.withInitial(() -> MINE);

	static HikariDataSource dataSource;

	static EntityManagerFactory emf;

	static JdbcTemplate jdbc;

	@BeforeAll
	static void setUp() {
		dataSource = new HikariDataSource();
		dataSource.setJdbcUrl(LocalOracle.jdbcUrl());
		dataSource.setUsername(LocalOracle.APP_USER);
		dataSource.setPassword(LocalOracle.APP_PASSWORD);
		jdbc = new JdbcTemplate(dataSource);

		LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
		factory.setDataSource(dataSource);
		factory.setPackagesToScan(ProbeOrder.class.getPackageName());
		factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
		Map<String, Object> properties = new HashMap<>();
		properties.put("hibernate.hbm2ddl.auto", "none");
		properties.put("hibernate.tenant_identifier_resolver", new CurrentTenantIdentifierResolver<String>() {
			@Override
			public String resolveCurrentTenantIdentifier() {
				return tenant.get();
			}

			@Override
			public boolean validateExistingCurrentSessions() {
				return false;
			}
		});
		factory.setJpaPropertyMap(properties);
		factory.afterPropertiesSet();
		emf = factory.getObject();
	}

	@AfterAll
	static void tearDown() {
		emf.close();
		dataSource.close();
	}

	@Test
	void queryIsFilteredByTenant() {
		long mine = inTx(em -> em.createQuery("select count(o) from ProbeOrder o", Long.class).getSingleResult());
		tenant.set("OTHER-APPLICANT-2");
		long other = inTx(em -> em.createQuery("select count(o) from ProbeOrder o", Long.class).getSingleResult());
		tenant.remove();

		assertThat(mine).isLessThan(150);
		assertThat(other).isEqualTo(150);
	}

	@Test
	void persistFillsTenantColumnAndBulkUpdateIsFiltered() {
		run(em -> em.persist(new ProbeOrder("A001", "검증", "N")));
		String storedKey = jdbc.queryForObject(
				"SELECT APPLICANT_KEY FROM ORDER_TB WHERE ORDER_ID = 'A001' AND NAME = '검증'", String.class);
		assertThat(storedKey).isEqualTo(MINE);

		int updated = inTx(em -> em.createQuery("update ProbeOrder o set o.name = '변경' where o.orderId = 'A001'")
			.executeUpdate());
		String otherName = jdbc.queryForObject(
				"SELECT NAME FROM ORDER_TB WHERE ORDER_ID = 'A001' AND APPLICANT_KEY = 'OTHER-APPLICANT-1'",
				String.class);
		assertThat(otherName).isEqualTo("김철수");
		assertThat(updated).isEqualTo(1);

		ProbeOrder found = inTx(em -> em.find(ProbeOrder.class, new ProbeOrder.Key("A001", MINE)));
		assertThat(found).isNotNull();

		ProbeOrder others = inTx(em -> em.find(ProbeOrder.class, new ProbeOrder.Key("A001", "OTHER-APPLICANT-1")));
		assertThat(others).as("다른 지원자의 키로 PK 조회").isNull();

		String max = inTx(em -> em.createQuery("select max(o.orderId) from ProbeOrder o", String.class)
			.getSingleResult());
		assertThat(max).isEqualTo("A001");
	}

	static void run(Consumer<EntityManager> work) {
		inTx(em -> {
			work.accept(em);
			return null;
		});
	}

	static <T> T inTx(Function<EntityManager, T> work) {
		EntityManager em = emf.createEntityManager();
		try {
			em.getTransaction().begin();
			T result = work.apply(em);
			em.getTransaction().commit();
			return result;
		}
		finally {
			em.close();
		}
	}

}
