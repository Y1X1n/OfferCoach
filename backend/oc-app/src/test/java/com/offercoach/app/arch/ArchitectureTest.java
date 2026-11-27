package com.offercoach.app.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 模块与分层架构约束。
 */
@AnalyzeClasses(packages = "com.offercoach")
class ArchitectureTest {

    @ArchTest
    static final ArchRule modules_should_be_free_of_cycles =
            slices().matching("com.offercoach.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule api_should_not_depend_on_persistence = noClasses()
            .that()
            .resideInAPackage("..api..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_outer_layers = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..api..", "..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule application_should_not_depend_on_infrastructure = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..");

    // AI 域单向：业务模块可依赖 oc-ai，AI 域不得反向依赖业务模块。
    // 工具服务之间的零依赖由 Maven enforcer 在 oc-ai/pom.xml 强制（它们的类不在本模块 classpath 上，
    // ArchUnit 无法在此检测跨服务依赖）。
    @ArchTest
    static final ArchRule ai_should_not_depend_on_business_modules = noClasses()
            .that()
            .resideInAPackage("com.offercoach.ai..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.offercoach.interview..", "com.offercoach.admin..", "com.offercoach.identity..");
}
