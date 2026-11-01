package com.inspien.assignment.verification;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import com.inspien.assignment.global.error.DbFailures;
import com.inspien.assignment.support.LocalOracle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검증 (설계서 §11, D-25): JPA를 거친 Oracle 예외가 어느 Spring 예외로 나오고, {@link DbFailures}가 어떻게 나누는가.
 *
 * <p>트랜잭션 안(어댑터가 보는 예외)과 밖(서비스가 받는 예외)을 모두 본다. 연결이 끊기면 롤백도 실패해, 밖에서는 원래
 * 예외 대신 롤백 예외가 보이기 때문이다. 수신의 503/500 구분과 스케줄러의 연결 실패/건별 실패 구분이 이 결과에 기댄다.
 */
class OracleFailureClassificationTest {

	static final String KEY = "VERIFY-FAILURE-1";

	static final HibernateJpaDialect dialect = new HibernateJpaDialect();

	static Env normal;

	RuntimeException inside;

	@BeforeAll
	static void setUp() {
		normal = new Env(LocalOracle.jdbcUrl(), 5, 5000);
	}

	@AfterAll
	static void tearDown() {
		normal.close();
	}

	@AfterEach
	void resume() {
		LocalOracle.unpause();
	}

	@Test
	void valueTooLongIsRejectedNotIntegrityViolation() {
		RuntimeException outside = attempt(normal, em -> {
			em.persist(new ProbeOrder("A101", "가".repeat(40), "RECEIVED"));
			em.flush();
		});

		assertThat(outside).isInstanceOf(JpaSystemException.class).rootCause().hasMessageContaining("ORA-12899");
		assertThat(DbFailures.isConnectionOrResource(outside)).isFalse();
	}

	@Test
	void missingPrivilegeIsRejected() {
		RuntimeException outside = attempt(normal,
				em -> em.createNativeQuery("DELETE FROM ORDER_TB WHERE 1 = 0").executeUpdate());

		assertThat(outside).isInstanceOf(JpaSystemException.class).rootCause().hasMessageContaining("ORA-41900");
		assertThat(DbFailures.isConnectionOrResource(outside)).isFalse();
	}

	@Test
	void primaryKeyConflictIsIntegrityViolation() {
		normal.tx.executeWithoutResult(s -> normal.em.persist(new ProbeOrder("A102", "dup", "RECEIVED")));

		RuntimeException flushed = attempt(normal, em -> {
			em.persist(new ProbeOrder("A102", "dup", "RECEIVED"));
			em.flush();
		});
		RuntimeException atCommit = attempt(normal, em -> em.persist(new ProbeOrder("A102", "dup", "RECEIVED")));

		assertThat(flushed).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(atCommit).isInstanceOf(DataIntegrityViolationException.class);
		assertThat(DbFailures.isConnectionOrResource(flushed)).isFalse();
	}

	@Test
	void readTimeoutIsConnectionFailureInsideAndOutsideTransaction() {
		RuntimeException outside = attempt(normal,
				em -> em.createNativeQuery("BEGIN DBMS_SESSION.SLEEP(5); END;").executeUpdate());

		assertThat(this.inside).isInstanceOf(DataAccessResourceFailureException.class);
		// 끊긴 연결에서 롤백이 실패해 원래 예외를 덮어쓴다
		assertThat(outside).isInstanceOf(JpaSystemException.class)
			.hasRootCauseMessage("Connection is closed");
		assertThat(DbFailures.isConnectionOrResource(this.inside)).isTrue();
		assertThat(DbFailures.isConnectionOrResource(outside)).isTrue();
	}

	@Test
	void unresponsiveDbIsConnectionFailure() {
		RuntimeException outside = attempt(normal, em -> {
			LocalOracle.pause();
			em.createQuery("select count(o) from ProbeOrder o").getSingleResult();
		});

		assertThat(DbFailures.isConnectionOrResource(this.inside)).isTrue();
		assertThat(DbFailures.isConnectionOrResource(outside)).isTrue();
	}

	@Test
	void commitTimeoutIsConnectionFailureWithUnknownResult() {
		RuntimeException outside = attempt(normal, em -> {
			em.persist(new ProbeOrder("A103", "commit", "RECEIVED"));
			em.flush();
			// 커밋 직전에 DB를 멈추고, 커밋이 시간 초과(2초)된 뒤 되살린다
			LocalOracle.pause();
			unpauseLater();
		});

		assertThat(outside).isInstanceOf(RecoverableDataAccessException.class);
		assertThat(DbFailures.isConnectionOrResource(outside)).isTrue();
	}

	@Test
	void poolWaitTimeoutIsConnectionFailure() throws Exception {
		try (Env env = new Env(LocalOracle.jdbcUrl(), 1, 500); Connection held = env.dataSource.getConnection()) {
			RuntimeException outside = attempt(env,
					em -> em.createQuery("select count(o) from ProbeOrder o").getSingleResult());

			assertThat(outside).isInstanceOf(CannotCreateTransactionException.class);
			assertThat(DbFailures.isConnectionOrResource(outside)).isTrue();
		}
	}

	@Test
	void connectionRefusedIsConnectionFailure() {
		try (Env env = new Env("jdbc:oracle:thin:@localhost:1/FREEPDB1", 2, 1000)) {
			RuntimeException outside = attempt(env,
					em -> em.createQuery("select count(o) from ProbeOrder o").getSingleResult());

			assertThat(outside).isInstanceOf(CannotCreateTransactionException.class);
			assertThat(DbFailures.isConnectionOrResource(outside)).isTrue();
		}
	}

	/**
	 * 트랜잭션 안에서는 @Repository 어댑터와 같은 방식(HibernateJpaDialect)으로 예외를 바꾸고, 밖으로 나온 예외를
	 * 돌려준다.
	 */
	RuntimeException attempt(Env env, Consumer<EntityManager> work) {
		this.inside = null;
		try {
			env.tx.executeWithoutResult(status -> {
				try {
					work.accept(env.em);
				}
				catch (RuntimeException ex) {
					DataAccessException translated = dialect.translateExceptionIfPossible(ex);
					this.inside = (translated != null) ? translated : ex;
					throw this.inside;
				}
			});
		}
		catch (RuntimeException ex) {
			return ex;
		}
		throw new AssertionError("예외가 나지 않았습니다");
	}

	static void unpauseLater() {
		Thread.ofVirtual().start(() -> {
			try {
				Thread.sleep(3000);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			LocalOracle.unpause();
		});
	}

	static final class Env implements AutoCloseable {

		final HikariDataSource dataSource = new HikariDataSource();

		final EntityManagerFactory emf;

		final EntityManager em;

		final TransactionTemplate tx;

		Env(String url, int poolSize, long poolWaitMillis) {
			this.dataSource.setJdbcUrl(url);
			this.dataSource.setUsername(LocalOracle.APP_USER);
			this.dataSource.setPassword(LocalOracle.APP_PASSWORD);
			this.dataSource.setMaximumPoolSize(poolSize);
			this.dataSource.setConnectionTimeout(poolWaitMillis);
			this.dataSource.setInitializationFailTimeout(-1);
			this.dataSource.addDataSourceProperty("oracle.net.CONNECT_TIMEOUT", "3000");
			this.dataSource.addDataSourceProperty("oracle.jdbc.ReadTimeout", "2000");

			LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
			factory.setDataSource(this.dataSource);
			factory.setPackagesToScan(ProbeOrder.class.getPackageName());
			factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
			Map<String, Object> properties = new HashMap<>();
			properties.put("hibernate.hbm2ddl.auto", "none");
			// 접속할 수 없는 DB로도 엔티티 매니저를 만들 수 있게 방언을 고정한다
			properties.put("hibernate.dialect", "org.hibernate.dialect.OracleDialect");
			properties.put("hibernate.boot.allow_jdbc_metadata_access", "false");
			properties.put("hibernate.tenant_identifier_resolver", new CurrentTenantIdentifierResolver<String>() {
				@Override
				public String resolveCurrentTenantIdentifier() {
					return KEY;
				}

				@Override
				public boolean validateExistingCurrentSessions() {
					return false;
				}
			});
			factory.setJpaPropertyMap(properties);
			factory.afterPropertiesSet();
			this.emf = factory.getObject();
			this.em = SharedEntityManagerCreator.createSharedEntityManager(this.emf);
			this.tx = new TransactionTemplate(new JpaTransactionManager(this.emf));
		}

		@Override
		public void close() {
			this.emf.close();
			this.dataSource.close();
		}

	}

}
