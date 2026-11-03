package com.inspien.assignment;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * 어느 도메인을: 설계서 §3 그림의 화살표만 (ALLOWED)
 * </pre>
 */
@AnalyzeClasses(packages = "com.inspien.assignment", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	static final String ROOT = "com.inspien.assignment";

	static final List<String> DOMAINS = List.of("order", "inbound", "receipt", "shipment", "tracking", "numbering",
			"retry");

	/**
	 * 도메인 사이 허용 의존: 설계서 §3 그림과 같은 표다 (D-21, D-26). 여기 없는 도메인 의존은 실패한다. 그림을 바꿀 때 함께
	 * 바꾼다. global은 모든 도메인이 쓸 수 있다.
	 *
	 * <ul>
	 * <li>receipt와 shipment는 서로 모른다. 각자 자기 단계만 적고, 둘을 합쳐 보는 것은 order의 마감이다.</li>
	 * <li>retry와 numbering은 다른 도메인을 모른다. 여럿이 함께 쓰는 핵심이다.</li>
	 * <li>order → numbering, shipment → numbering의 일부는 채번기 포트 IssuedIdReader 구현이다. 반대로 부르면 순환이다.</li>
	 * </ul>
	 */
	static final Map<String, Set<String>> ALLOWED = Map.of(
			"numbering", Set.of(),
			"retry", Set.of(),
			"order", Set.of("numbering", "retry"),
			"inbound", Set.of("order", "numbering"),
			"receipt", Set.of("order", "inbound", "retry"),
			"shipment", Set.of("order", "inbound", "numbering", "retry"),
			"tracking", Set.of("order", "inbound", "retry"));

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

	/**
	 * 금지 목록이 아니라 허용 목록이다. 금지 목록은 새 의존을 막지 못해, 그림에 없는 화살표가 생겨도 순환만 아니면 통과한다.
	 */
	@ArchTest
	static void domainsDependOnlyOnAllowedDomains(JavaClasses classes) {
		if (!ALLOWED.keySet().equals(Set.copyOf(DOMAINS))) {
			throw new IllegalStateException("도메인 목록과 허용 표가 다르다: " + DOMAINS + " / " + ALLOWED.keySet());
		}
		for (String domain : DOMAINS) {
			String[] forbidden = DOMAINS.stream()
				.filter(other -> !other.equals(domain) && !ALLOWED.get(domain).contains(other))
				.map(other -> ROOT + "." + other + "..")
				.toArray(String[]::new);
			if (forbidden.length == 0) {
				continue;
			}
			noClasses().that()
				.resideInAPackage(ROOT + "." + domain + "..")
				.should()
				.dependOnClassesThat()
				.resideInAnyPackage(forbidden)
				.as(domain + "는 허용된 도메인 " + ALLOWED.get(domain) + "만 의존한다 (설계서 §3)")
				.allowEmptyShould(true)
				.check(classes);
		}
	}

	/**
	 * global은 공통 계약(응답, 오류 코드, 이벤트, 설정)만 둔다. 도메인을 알면 모든 도메인과 순환이 생길 수 있다.
	 */
	@ArchTest
	static final ArchRule globalDoesNotKnowDomains = noClasses().that()
		.resideInAPackage(ROOT + ".global..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage(DOMAINS.stream().map(domain -> ROOT + "." + domain + "..").toArray(String[]::new))
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
