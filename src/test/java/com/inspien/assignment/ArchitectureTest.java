package com.inspien.assignment;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import org.hibernate.annotations.TenantId;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import com.inspien.assignment.global.event.ProcessEvents;
import com.inspien.assignment.order.application.CloseFacade;
import com.inspien.assignment.order.application.OrderService;
import com.inspien.assignment.receipt.application.port.ReceiptUploader;

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
 * application:  …Facade(흐름, 트랜잭션 없음) → …Service(DB 하나의 짧은 트랜잭션) → 포트
 * </pre>
 */
@AnalyzeClasses(packages = "com.inspien.assignment", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	static final String ROOT = "com.inspien.assignment";

	/**
	 * 이름 있는 트랜잭션 애너테이션(@OrderTx·@ShipmentTx·@HistoryTx)이 있는 패키지. 하나씩 적지 않고 패키지로 보므로 새로
	 * 생겨도 빠지지 않는다.
	 */
	static final String TX_PACKAGE = ROOT + ".global.transaction";

	static final List<String> DOMAINS = List.of("order", "inbound", "receipt", "shipment", "tracking", "numbering",
			"stage", "cycle");

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
	 * <li>stage, numbering, cycle은 다른 도메인을 모른다. 여럿이 함께 쓰는 핵심이다. cycle은 스케줄러 주기 도구라 global만
	 * 쓴다.</li>
	 * <li>order → numbering, shipment → numbering의 일부는 채번기 포트 IssuedIdReader 구현이다(OrderService,
	 * ShipmentRecordService). 반대로 부르면 순환이다.</li>
	 * </ul>
	 */
	static final Map<String, Set<String>> ALLOWED = Map.of(
			"numbering", Set.of(),
			"stage", Set.of(),
			"cycle", Set.of(),
			"order", Set.of("numbering", "stage", "cycle"),
			"inbound", Set.of("order", "numbering"),
			"receipt", Set.of("order", "inbound", "stage", "cycle"),
			"shipment", Set.of("order", "inbound", "numbering", "stage", "cycle"),
			"tracking", Set.of("order", "inbound", "stage"));

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
	 * Spring의 DB 예외(org.springframework.dao)와 트랜잭션 예외는 막지 않는다. Oracle 포트의 실패 약속이고, Facade가
	 * 트랜잭션 밖에서 받아 장애를 나눈다 (D-22, D-25 2026-11-04 보완).
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
	static final ArchRule onlyCloseFacadeClosesOrders = noClasses().that()
		.doNotHaveFullyQualifiedName(CloseFacade.class.getName())
		.should()
		.callMethodWhere(DescribedPredicate.describe("OrderService.close(..)",
				(JavaMethodCall call) -> call.getName().equals("close")
						&& call.getTarget().getOwner().isEquivalentTo(OrderService.class)))
		.as("OrderService.close(..)는 CloseFacade만 부른다 (D-26)");

	/**
	 * 처리 이벤트는 최선 노력 이력이라 자기 트랜잭션에서 남는다 (EventRecordService, REQUIRES_NEW). 업무 트랜잭션 안에서
	 * 발행하면 이력은 먼저 커밋되고 업무는 나중에 롤백되어, 일어나지 않은 성공이 기록에 남을 수 있다. 그래서 이벤트를 발행하는
	 * 클래스는 트랜잭션을 걸지 않고, 트랜잭션이 끝난 뒤 결과를 보고 발행한다 (D-04, D-13).
	 */
	@ArchTest
	static final ArchRule eventPublishersAreNotTransactional = classes().that(dependOn(ProcessEvents.class))
		.should(notBeAnnotatedInPackage(TX_PACKAGE))
		.as("ProcessEvents로 발행하는 클래스는 @OrderTx·@ShipmentTx·@HistoryTx를 걸지 않는다 (D-04, D-13)");

	/**
	 * 역할 이름 규약 (D-21 6번 2026-11-04 보완). Facade는 쓰임새의 흐름을 지휘한다. 여러 DB의 Service, 바깥 시스템 포트(FTP,
	 * 파서), 이벤트를 차례로 부르고, Spring DB 예외를 트랜잭션이 끝난 밖에서 받아 연결 장애와 거절로 나눈다. 커밋 때의
	 * 예외는 밖에서만 보이기 때문이다 (D-25). 그래서 트랜잭션을 걸지 않는다. 걸면 짧은 트랜잭션(D-19)이 흐름 전체로 늘어나고,
	 * 바깥 호출과 다른 DB의 작업이 한 트랜잭션 안에 들어간다.
	 */
	@ArchTest
	static final ArchRule facadesAreNotTransactional = classes().that()
		.haveSimpleNameEndingWith("Facade")
		.should(notBeAnnotatedInPackage(TX_PACKAGE))
		.as("…Facade는 트랜잭션을 걸지 않는다 (D-19, D-21 6번, D-25)");

	/**
	 * 트랜잭션을 거는 클래스는 Service이고, DB 하나의 애너테이션만 쓴다 (D-19, D-21 6번). DB 셋은 한 트랜잭션으로 묶을 수
	 * 없으므로, 두 DB의 애너테이션이 한 클래스에 있으면 그 클래스가 흐름까지 맡고 있다는 뜻이다. 흐름은 Facade로 나눈다.
	 */
	@ArchTest
	static final ArchRule transactionalClassesAreServices = classes().that(useTransactions())
		.should()
		.haveSimpleNameEndingWith("Service")
		.andShould(useTransactionsOfOneDatabase())
		.as("트랜잭션을 거는 클래스는 …Service이고 DB 하나의 애너테이션만 쓴다 (D-19, D-21 6번)");

	/**
	 * 거꾸로 Service라는 이름은 트랜잭션 클래스에만 쓴다. 트랜잭션 없는 메서드가 섞이면 같은 이름이 두 역할을 하게 되어,
	 * 호출하는 쪽이 이름만 보고 "이 호출은 커밋된다"를 믿을 수 없다. private·static 메서드는 프록시를 거치지 않아 보지 않는다.
	 */
	@ArchTest
	static final ArchRule servicesRunEveryMethodInTransaction = classes().that()
		.haveSimpleNameEndingWith("Service")
		.should(runEveryMethodInTransaction())
		.as("…Service의 메서드는 모두 트랜잭션 안에서 돈다 (D-21 6번)");

	/**
	 * Service는 Facade를 부르지 않는다. 부르면 트랜잭션 안에서 다른 DB의 트랜잭션과 바깥 호출이 열린다 (D-19, D-21 6번).
	 */
	@ArchTest
	static final ArchRule servicesDoNotUseFacades = noClasses().that()
		.haveSimpleNameEndingWith("Service")
		.should()
		.dependOnClassesThat()
		.haveSimpleNameEndingWith("Facade")
		.as("…Service는 …Facade를 부르지 않는다 (D-21 6번)");

	/**
	 * FTP 업로드는 느리고 끝을 모른다. 트랜잭션 안에서 부르면 그동안 DB 연결을 잡고 있어 작은 커넥션 풀(D-19)이 마른다.
	 * 업로드는 Facade가 트랜잭션 밖에서 하고, 결과만 Service로 적는다 (D-19, D-21 6번).
	 */
	@ArchTest
	static final ArchRule transactionsDoNotCallFtp = noClasses().that(useTransactions())
		.should()
		.dependOnClassesThat(belongTo(ReceiptUploader.class))
		.as("트랜잭션을 거는 클래스는 ReceiptUploader(FTP)를 부르지 않는다 (D-19, D-21 6번)");

	/**
	 * Facade는 DB에 Service로만 닿는다. 저장소 포트를 직접 부르면 트랜잭션 없이 읽게 되어, 같은 DB의 다른 읽기와 경계가
	 * 달라진다 (D-19, D-21 6번).
	 *
	 * <p>채번기 포트 IssuedIdReader는 막지 않는다. 저장소가 아니라 도메인 사이 포트이고, 구현은 다른 도메인의 트랜잭션
	 * Service(OrderService, ShipmentRecordService)다. 채번기가 그 도메인들을 모르게 의존을 뒤집은 것이라, NumberingSyncFacade가
	 * 부르는 것은 Service 경계를 거친 읽기다 (D-21).
	 */
	@ArchTest
	static final ArchRule facadesReachDatabasesThroughServices = noClasses().that()
		.haveSimpleNameEndingWith("Facade")
		.should()
		.dependOnClassesThat(storePorts())
		.as("…Facade는 저장소 포트(…Store)를 직접 쓰지 않는다 (D-21 6번)");

	/**
	 * 들어오는 쪽(컨트롤러, 스케줄러)은 Facade를 부른다. 트랜잭션 Service를 직접 부르면 흐름과 D-25의 DB 예외 분류가 어댑터로
	 * 샌다 (D-21 6번).
	 *
	 * <p>예외는 {@code adapter.in.event} 하나다. 처리 이벤트 하나를 EventRecordService로 남기는, 쓰임새가 아니라 트랜잭션
	 * 하나를 부르는 진입점이라 Facade를 두지 않는다. 지휘할 흐름이 없어 Facade는 그대로 넘기기만 한다. 실패는 로그만 남기고
	 * 삼킨다 (D-04, D-13). 기동 때 채번 동기화는 두 Oracle을 읽고 H2를 올리는 흐름이라 NumberingSyncFacade를 부른다.
	 */
	@ArchTest
	static final ArchRule entryPointsUseFacades = noClasses().that()
		.resideInAPackage(ROOT + ".*.adapter.in..")
		.and()
		.resideOutsideOfPackage(ROOT + ".*.adapter.in.event..")
		.should()
		.dependOnClassesThat(useTransactions())
		.as("컨트롤러·스케줄러는 트랜잭션 Service가 아니라 …Facade를 부른다 (D-21 6번)");

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

	/**
	 * 클래스나 그 메서드·생성자에 붙은 트랜잭션 애너테이션의 이름. 비어 있으면 트랜잭션을 걸지 않는 클래스다.
	 */
	static Set<String> transactionAnnotations(JavaClass javaClass) {
		Stream<JavaClass> onClass = javaClass.getAnnotations().stream().map(annotation -> annotation.getRawType());
		Stream<JavaClass> onCodeUnits = javaClass.getCodeUnits()
			.stream()
			.flatMap(codeUnit -> codeUnit.getAnnotations().stream().map(annotation -> annotation.getRawType()));
		return Stream.concat(onClass, onCodeUnits)
			.filter(type -> type.getPackageName().equals(TX_PACKAGE))
			.map(JavaClass::getSimpleName)
			.collect(Collectors.toCollection(TreeSet::new));
	}

	static DescribedPredicate<JavaClass> useTransactions() {
		return DescribedPredicate.describe("use @OrderTx·@ShipmentTx·@HistoryTx",
				javaClass -> !transactionAnnotations(javaClass).isEmpty());
	}

	static ArchCondition<JavaClass> useTransactionsOfOneDatabase() {
		return new ArchCondition<>("use transaction annotations of one database") {

			@Override
			public void check(JavaClass javaClass, ConditionEvents events) {
				Set<String> used = transactionAnnotations(javaClass);
				if (used.size() > 1) {
					String message = javaClass.getName() + "가 DB 여럿의 트랜잭션을 건다: " + used;
					events.add(SimpleConditionEvent.violated(javaClass, message));
				}
			}

		};
	}

	/**
	 * 클래스에 트랜잭션 애너테이션이 있거나, private·static이 아닌 메서드마다 있어야 한다. 컴파일러가 만든 메서드(람다 본문,
	 * 브리지)는 보지 않는다.
	 */
	static ArchCondition<JavaClass> runEveryMethodInTransaction() {
		return new ArchCondition<>("run every non-private method in a transaction") {

			@Override
			public void check(JavaClass javaClass, ConditionEvents events) {
				if (javaClass.getAnnotations().stream().anyMatch(ArchitectureTest::isTransaction)) {
					return;
				}
				for (JavaMethod method : javaClass.getMethods()) {
					Set<JavaModifier> modifiers = method.getModifiers();
					if (modifiers.contains(JavaModifier.PRIVATE) || modifiers.contains(JavaModifier.STATIC)
							|| modifiers.contains(JavaModifier.SYNTHETIC) || modifiers.contains(JavaModifier.BRIDGE)) {
						continue;
					}
					if (method.getAnnotations().stream().noneMatch(ArchitectureTest::isTransaction)) {
						String message = method.getFullName() + "에 트랜잭션 애너테이션이 없다";
						events.add(SimpleConditionEvent.violated(javaClass, message));
					}
				}
			}

		};
	}

	static boolean isTransaction(JavaAnnotation<?> annotation) {
		return annotation.getRawType().getPackageName().equals(TX_PACKAGE);
	}

	/**
	 * 그 타입과 안에 든 타입(ReceiptUploader.Session, 예외들).
	 */
	static DescribedPredicate<JavaClass> belongTo(Class<?> type) {
		return DescribedPredicate.describe(type.getSimpleName() + " or its nested types",
				javaClass -> javaClass.getName().equals(type.getName())
						|| javaClass.getName().startsWith(type.getName() + "$"));
	}

	/**
	 * 저장소 포트(…Store): 어댑터가 DB를 트랜잭션 없이 직접 다루는 포트. FTP·파서 포트는 DB가 아니고, 채번기 포트
	 * IssuedIdReader는 트랜잭션 Service가 구현한다.
	 */
	static DescribedPredicate<JavaClass> storePorts() {
		return DescribedPredicate.describe("store ports (…Store)",
				javaClass -> javaClass.getPackageName().endsWith(".application.port")
						&& javaClass.getSimpleName().endsWith("Store"));
	}

}
