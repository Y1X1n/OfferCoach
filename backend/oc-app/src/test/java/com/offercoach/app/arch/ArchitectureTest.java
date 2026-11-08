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
}
