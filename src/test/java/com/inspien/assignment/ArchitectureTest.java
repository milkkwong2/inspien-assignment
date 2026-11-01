package com.inspien.assignment;

import java.util.List;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 패키지 의존 규칙 (D-21). 규칙은 문서로만 두면 무너지므로 테스트로 강제한다.
 *
 * <pre>
 * 도메인 안쪽:  adapter → application → domain
 * 도메인 사이:  다른 도메인의 application·domain만 쓴다. adapter는 그 도메인 것이다
 * receipt ↔ shipment: 서로 모른다. ORDER_TB.STATUS로만 이어진다 (D-06)
 * </pre>
 */
@AnalyzeClasses(packages = "com.inspien.assignment", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	static final String ROOT = "com.inspien.assignment";

	static final List<String> DOMAINS = List.of("order", "inbound", "receipt", "shipment", "tracking", "numbering",
			"retry");

	@ArchTest
	static final ArchRule domainIsPureJava = noClasses().that()
		.resideInAPackage(ROOT + ".*.domain..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.querydsl..", ROOT + ".*.application..",
				ROOT + ".*.adapter..")
		.allowEmptyShould(true);

	@ArchTest
	static final ArchRule applicationDoesNotKnowAdapters = noClasses().that()
		.resideInAPackage(ROOT + ".*.application..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage(ROOT + ".*.adapter..")
		.allowEmptyShould(true);

	@ArchTest
	static final ArchRule receiptDoesNotKnowShipment = noClasses().that()
		.resideInAPackage(ROOT + ".receipt..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage(ROOT + ".shipment..")
		.allowEmptyShould(true);

	@ArchTest
	static final ArchRule shipmentDoesNotKnowReceipt = noClasses().that()
		.resideInAPackage(ROOT + ".shipment..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage(ROOT + ".receipt..")
		.allowEmptyShould(true);

	/**
	 * retry는 단계 진행 상태만 맡는다. order(마감)·receipt·shipment·tracking(재처리 API)이 retry를 쓰고, retry는 그들을
	 * 모른다 (D-21, D-26).
	 */
	@ArchTest
	static final ArchRule retryDoesNotKnowOtherDomains = noClasses().that()
		.resideInAPackage(ROOT + ".retry..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage(ROOT + ".order..", ROOT + ".inbound..", ROOT + ".receipt..", ROOT + ".shipment..",
				ROOT + ".tracking..", ROOT + ".numbering..")
		.allowEmptyShould(true);

	@ArchTest
	static final ArchRule noCyclesBetweenTopLevelPackages = slices().matching(ROOT + ".(*)..")
		.should()
		.beFreeOfCycles()
		.allowEmptyShould(true);

	/**
	 * JPA 엔티티와 Spring Data 리포지토리는 어댑터 안쪽에 숨는다. 서비스는 정규 모델만 본다.
	 */
	@ArchTest
	static final ArchRule entitiesLiveInPersistenceAdapters = classes().that()
		.areAnnotatedWith(jakarta.persistence.Entity.class)
		.or()
		.areAssignableTo(org.springframework.data.repository.Repository.class)
		.should()
		.resideInAPackage(ROOT + ".*.adapter.out.persistence..")
		.allowEmptyShould(true);

	/**
	 * Oracle 어댑터는 네이티브 SQL을 쓰지 않는다. @TenantId의 APPLICANT_KEY 조건은 JPQL·QueryDSL에만 붙는다 (D-01, D-25).
	 */
	@ArchTest
	static final ArchRule oracleAdaptersDoNotUseNativeSql = noClasses().that()
		.resideInAnyPackage(ROOT + ".order..", ROOT + ".shipment..")
		.should()
		.callMethodWhere(com.tngtech.archunit.base.DescribedPredicate.describe("createNativeQuery",
				(com.tngtech.archunit.core.domain.JavaMethodCall call) -> call.getName().startsWith("createNative")))
		.orShould()
		.dependOnClassesThat()
		.resideInAPackage("org.springframework.jdbc..")
		.allowEmptyShould(true);

	/**
	 * DB가 셋이고 기본 트랜잭션 매니저가 없으므로 이름 없는 @Transactional을 쓰지 않는다 (D-19).
	 */
	@ArchTest
	static final ArchRule transactionsUseNamedAnnotations = noClasses().that()
		.resideOutsideOfPackage(ROOT + ".global.transaction..")
		.should()
		.dependOnClassesThat()
		.haveFullyQualifiedName("org.springframework.transaction.annotation.Transactional");

	/**
	 * 부트스트랩은 별도 진입점의 일회성 도구다. Spring을 쓰지 않고, 앱이 부트스트랩을 쓰지도 않는다.
	 */
	@ArchTest
	static final ArchRule bootstrapIsIsolated = noClasses().that()
		.resideInAPackage(ROOT + ".bootstrap..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework..")
		.orShould()
		.dependOnClassesThat()
		.resideInAnyPackage(ROOT + ".global..");

	@ArchTest
	static final ArchRule appDoesNotUseBootstrap = noClasses().that()
		.resideOutsideOfPackage(ROOT + ".bootstrap..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage(ROOT + ".bootstrap..");

	/**
	 * 다른 도메인의 adapter는 쓰지 않는다. 다른 도메인은 application(서비스, 포트)과 domain(정규 모델)으로만 만난다.
	 */
	@ArchTest
	static void adaptersArePrivateToTheirDomain(JavaClasses classes) {
		for (String domain : DOMAINS) {
			noClasses().that()
				.resideOutsideOfPackage(ROOT + "." + domain + "..")
				.should()
				.dependOnClassesThat()
				.resideInAPackage(ROOT + "." + domain + ".adapter..")
				.allowEmptyShould(true)
				.check(classes);
		}
	}

}
