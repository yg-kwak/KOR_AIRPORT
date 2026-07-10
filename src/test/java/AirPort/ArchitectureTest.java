package AirPort;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 구조 테스트 — AGENTS.md §4 계층 경계 불변식을 테스트로 강제한다.
 *
 * <p>위반 시 실패 메시지의 규칙 설명을 읽고 의존 방향을 바로잡을 것. 규칙 원천: docs/architecture.md, docs/conventions.md.
 */
@AnalyzeClasses(packages = "AirPort", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  // ── 계층 단방향: controller → service → mapper ─────────────────────────

  @ArchTest
  static final ArchRule controller_는_mapper_를_직접_호출하지_않는다 =
      noClasses()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..mapper..")
          .because("Controller 는 Service 를 통해서만 데이터에 접근한다 (AGENTS §4 계층 경계)");

  @ArchTest
  static final ArchRule service_는_controller_에_의존하지_않는다 =
      noClasses()
          .that()
          .resideInAPackage("..service..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..controller..")
          .because("의존 방향은 controller → service 단방향이다");

  @ArchTest
  static final ArchRule mapper_는_상위_계층에_의존하지_않는다 =
      noClasses()
          .that()
          .resideInAPackage("..mapper..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..controller..", "..service..")
          .because("mapper 는 최하위 계층이다");

  @ArchTest
  static final ArchRule model_은_계층_로직에_의존하지_않는다 =
      noClasses()
          .that()
          .resideInAPackage("..model..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..controller..", "..service..", "..mapper..")
          .because("model 은 순수 DTO/VO 다");

  // ── 네이밍 규칙 (docs/conventions.md) ────────────────────────────────

  @ArchTest
  static final ArchRule controller_네이밍 =
      classes()
          .that()
          .resideInAPackage("..controller..")
          .should()
          .haveSimpleNameEndingWith("Controller")
          .orShould()
          .haveSimpleNameEndingWith("Advice")
          .because("controller 패키지는 *Controller / *Advice 만 둔다");

  @ArchTest
  static final ArchRule service_네이밍 =
      classes()
          .that()
          .resideInAPackage("..service..")
          .should()
          .haveSimpleNameEndingWith("Service")
          .because("service 패키지는 *Service 만 둔다");

  @ArchTest
  static final ArchRule mapper_네이밍 =
      classes()
          .that()
          .resideInAPackage("..mapper..")
          .should()
          .haveSimpleNameEndingWith("Mapper")
          .andShould()
          .beInterfaces()
          .because("mapper 패키지는 Tb*Mapper 인터페이스만 둔다 (SQL 은 XML 에)");
}
