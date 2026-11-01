package com.inspien.assignment.global.transaction;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * H2 처리 이력 트랜잭션.
 *
 * <p>DB가 셋이고 기본 트랜잭션 매니저를 두지 않으므로 이름 없는 {@code @Transactional}은 쓰지 않는다 (D-19).
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Transactional(transactionManager = HistoryTx.MANAGER)
public @interface HistoryTx {

	String MANAGER = "historyTx";

	@AliasFor(annotation = Transactional.class)
	boolean readOnly() default false;

	@AliasFor(annotation = Transactional.class)
	Propagation propagation() default Propagation.REQUIRED;

}
