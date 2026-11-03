package com.inspien.assignment;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import org.hibernate.annotations.TenantId;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import com.inspien.assignment.global.event.ProcessEvents;
import com.inspien.assignment.order.application.CloseService;
import com.inspien.assignment.order.application.OrderService;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

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
	 * 도메인이 아닌 최상위 패키지. global은 공통 계약, bootstrap은 별도 진입점의 일회성 도구다.
	 */
	static final Set<String> NOT_DOMAINS = Set.of("global", "bootstrap");

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

	/**
	 * 손으로 적은 도메인 목록이 실제 패키지와 같은지 본다. 새 도메인 패키지를 만들고 목록에 넣지 않으면 아래 규칙들이 그
	 * 패키지를 보지 않는다.
	 */
	@ArchTest
	static void domainListMatchesTopLevelPackages(JavaClasses classes) {
		Set<String> topLevelPackages = classes.stream()
			.map(JavaClass::getPackageName)
			.filter(packageName -> packageName.startsWith(ROOT + "."))
			.map(packageName -> packageName.substring(ROOT.length() + 1).split("\\.")[0])
			.filter(topLevel -> !NOT_DOMAINS.contains(topLevel))
			.collect(Collectors.toSet());

		assertThat(topLevelPackages).as("최상위 패키지(global, bootstrap 제외)와 DOMAINS").isEqualTo(Set.copyOf(DOMAINS));
	}

	@ArchTest
	static final ArchRule domainIsPureJava = noClasses().that()
		.resideInAPackage(ROOT + ".*.domain..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.querydsl..", ROOT + ".*.application..",
				ROOT + ".*.adapter..");

	@ArchTest
	static final ArchRule applicationDoesNotKnowAdapters = noClasses().that()
		.resideInAPackage(ROOT + ".*.application..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage(ROOT + ".*.adapter..");

	/**
	 * 서비스는 정규 모델과 포트만 본다. 영속성 기술(JPA, QueryDSL, Hibernate, Spring Data, JDBC)은 어댑터 안쪽에 둔다.
	 * Spring의 DB 예외(org.springframework.dao)와 트랜잭션 예외는 서비스가 장애를 나누려고 잡으므로 막지 않는다 (D-22).
	 */
	@ArchTest
	static final ArchRule applicationDoesNotKnowPersistence = noClasses().that()
		.resideInAPackage(ROOT + ".*.application..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("jakarta.persistence..", "com.querydsl..", "org.hibernate..", "org.springframework.data..",
				"java.sql..", "org.springframework.jdbc..")
		.as("application은 영속성 기술을 모른다");

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
		.resideInAnyPackage(DOMAINS.stream().map(domain -> ROOT + "." + domain + "..").toArray(String[]::new));

	@ArchTest
	static final ArchRule noCyclesBetweenTopLevelPackages = slices().matching(ROOT + ".(*)..")
		.should()
		.beFreeOfCycles();

	/**
	 * JPA 엔티티와 Spring Data 리포지토리는 어댑터 안쪽에 숨는다. 서비스는 정규 모델만 본다.
	 */
	@ArchTest
	static final ArchRule entitiesLiveInPersistenceAdapters = classes().that()
		.areAnnotatedWith(Entity.class)
		.or()
		.areAssignableTo(org.springframework.data.repository.Repository.class)
		.should()
		.resideInAPackage(ROOT + ".*.adapter.out.persistence..");

	/**
	 * 공용 Oracle 테이블의 엔티티는 APPLICANT_KEY를 @TenantId로 둔다. 그래야 Hibernate가 모든 조회·갱신 조건과 INSERT 값에
	 * 내 지원자 키를 넣는다 (D-01, D-25). 빠뜨리면 다른 지원자의 행을 읽거나 바꾼다.
	 */
	@ArchTest
	static final ArchRule sharedTableEntitiesHaveTenantId = classes().that()
		.areAnnotatedWith(Entity.class)
		.and()
		.resideInAnyPackage(ROOT + ".order..adapter.out.persistence..", ROOT + ".shipment..adapter.out.persistence..")
		.should(haveFieldAnnotatedWith(TenantId.class))
		.as("공용 테이블 엔티티는 @TenantId 필드를 가진다 (D-01, D-25)");

	/**
	 * Oracle 어댑터는 네이티브 SQL을 쓰지 않는다. @TenantId의 APPLICANT_KEY 조건은 JPQL·QueryDSL에만 붙는다 (D-01, D-25).
	 */
	@ArchTest
	static final ArchRule oracleAdaptersDoNotUseNativeSql = noClasses().that()
		.resideInAnyPackage(ROOT + ".order..", ROOT + ".shipment..")
		.should()
		.callMethodWhere(DescribedPredicate.describe("createNativeQuery",
				(JavaMethodCall call) -> call.getName().startsWith("createNative")))
		.orShould()
		.dependOnClassesThat()
		.resideInAPackage("org.springframework.jdbc..");

	/**
	 * Spring Data {@code save()}는 ID를 직접 넣은 엔티티를 기존 엔티티로 보고 {@code merge}한다. merge는 같은 PK 행이
	 * 있으면 UPDATE하므로, PK 충돌이 나지 않고 남의 행을 덮어쓴다. 설계는 PK 충돌을 감지해 다시 조회·비교하므로 INSERT는
	 * {@code persist}로만 한다 (설계서 §4.1 WARNING, D-16, D-19).
	 */
	@ArchTest
	static final ArchRule noSaveOrMerge = noClasses().should()
		.callMethodWhere(DescribedPredicate.describe("Spring Data 리포지토리의 save*",
				(JavaMethodCall call) -> call.getName().startsWith("save") && call.getTarget()
					.getOwner()
					.isAssignableTo(org.springframework.data.repository.Repository.class)))
		.orShould()
		.callMethodWhere(DescribedPredicate.describe("EntityManager.merge",
				(JavaMethodCall call) -> call.getName().equals("merge")
						&& call.getTarget().getOwner().isAssignableTo(EntityManager.class)))
		.as("save()·merge()로 쓰지 않는다: 같은 PK 행을 덮어쓴다 (D-16, D-19)");

	/**
	 * STATUS를 Y로 쓰는 곳은 마감 스케줄러 하나다 (D-26). 영수증·배송이 각자 Y를 쓰면 서로를 알아야 하고, Y 직전에 멈춘 주문을
	 * 위한 복구 경로가 따로 생긴다.
	 */
	@ArchTest
	static final ArchRule onlyCloseServiceClosesOrders = noClasses().that()
		.doNotHaveFullyQualifiedName(CloseService.class.getName())
		.should()
		.callMethodWhere(DescribedPredicate.describe("OrderService.close(..)",
				(JavaMethodCall call) -> call.getName().equals("close")
						&& call.getTarget().getOwner().isEquivalentTo(OrderService.class)))
		.as("OrderService.close(..)는 CloseService만 부른다 (D-26)");

	/**
	 * 처리 이벤트는 최선 노력 이력이라 자기 트랜잭션에서 남는다 (EventRecorder, REQUIRES_NEW). 업무 트랜잭션 안에서 발행하면
	 * 이력은 먼저 커밋되고 업무는 나중에 롤백되어, 일어나지 않은 성공이 기록에 남을 수 있다. 그래서 이벤트를 발행하는 클래스는
	 * 트랜잭션을 걸지 않고, 트랜잭션이 끝난 뒤 결과를 보고 발행한다 (D-04, D-13).
	 */
	@ArchTest
	static final ArchRule eventPublishersAreNotTransactional = classes().that(dependOn(ProcessEvents.class))
		.should(notBeAnnotatedInPackage(ROOT + ".global.transaction"))
		.as("ProcessEvents로 발행하는 클래스는 @OrderTx·@ShipmentTx·@HistoryTx를 걸지 않는다 (D-04, D-13)");

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
				.check(classes);
		}
	}

	static ArchCondition<JavaClass> haveFieldAnnotatedWith(Class<? extends java.lang.annotation.Annotation> annotation) {
		return new ArchCondition<>("have a field annotated with @" + annotation.getSimpleName()) {

			@Override
			public void check(JavaClass javaClass, ConditionEvents events) {
				boolean found = javaClass.getAllFields().stream().anyMatch(field -> field.isAnnotatedWith(annotation));
				if (!found) {
					String message = javaClass.getName() + "에 @" + annotation.getSimpleName() + " 필드가 없다";
					events.add(SimpleConditionEvent.violated(javaClass, message));
				}
			}

		};
	}

	static DescribedPredicate<JavaClass> dependOn(Class<?> target) {
		return DescribedPredicate.describe("depend on " + target.getSimpleName(),
				javaClass -> javaClass.getDirectDependenciesFromSelf()
					.stream()
					.anyMatch(dependency -> dependency.getTargetClass().isEquivalentTo(target)));
	}

	/**
	 * 클래스나 그 메서드·생성자에 그 패키지의 애너테이션이 없어야 한다. 이름 있는 트랜잭션 애너테이션을 하나씩 적지 않고
	 * 패키지로 보므로 새로 생겨도 빠지지 않는다.
	 */
	static ArchCondition<JavaClass> notBeAnnotatedInPackage(String annotationPackage) {
		return new ArchCondition<>("not be annotated (class or methods) with annotations in " + annotationPackage) {

			@Override
			public void check(JavaClass javaClass, ConditionEvents events) {
				for (JavaAnnotation<?> annotation : javaClass.getAnnotations()) {
					if (annotation.getRawType().getPackageName().equals(annotationPackage)) {
						String message = javaClass.getName() + "에 @" + annotation.getRawType().getSimpleName();
						events.add(SimpleConditionEvent.violated(javaClass, message));
					}
				}
				for (JavaCodeUnit codeUnit : javaClass.getCodeUnits()) {
					for (JavaAnnotation<?> annotation : codeUnit.getAnnotations()) {
						if (annotation.getRawType().getPackageName().equals(annotationPackage)) {
							String message = codeUnit.getFullName() + "에 @" + annotation.getRawType().getSimpleName();
							events.add(SimpleConditionEvent.violated(javaClass, message));
						}
					}
				}
			}

		};
	}

}
