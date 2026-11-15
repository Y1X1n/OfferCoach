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

    // AI 工具服务之间零依赖，可各自独立构建、部署与扩容；共享能力只放 oc-ai-core（com.offercoach.ai.tools）。
    @ArchTest
    static final ArchRule asr_should_not_depend_on_other_ai_services = noClasses()
            .that()
            .resideInAPackage("com.offercoach.ai.asr..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.offercoach.ai.tts..",
                    "com.offercoach.ai.ocr..",
                    "com.offercoach.ai.search..",
                    "com.offercoach.ai.fetch..");

    @ArchTest
    static final ArchRule tts_should_not_depend_on_other_ai_services = noClasses()
            .that()
            .resideInAPackage("com.offercoach.ai.tts..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.offercoach.ai.asr..",
                    "com.offercoach.ai.ocr..",
                    "com.offercoach.ai.search..",
                    "com.offercoach.ai.fetch..");

    @ArchTest
    static final ArchRule ocr_should_not_depend_on_other_ai_services = noClasses()
            .that()
            .resideInAPackage("com.offercoach.ai.ocr..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.offercoach.ai.asr..",
                    "com.offercoach.ai.tts..",
                    "com.offercoach.ai.search..",
                    "com.offercoach.ai.fetch..");

    @ArchTest
    static final ArchRule search_should_not_depend_on_other_ai_services = noClasses()
            .that()
            .resideInAPackage("com.offercoach.ai.search..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.offercoach.ai.asr..",
                    "com.offercoach.ai.tts..",
                    "com.offercoach.ai.ocr..",
                    "com.offercoach.ai.fetch..");

    @ArchTest
    static final ArchRule fetch_should_not_depend_on_other_ai_services = noClasses()
            .that()
            .resideInAPackage("com.offercoach.ai.fetch..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.offercoach.ai.asr..",
                    "com.offercoach.ai.tts..",
                    "com.offercoach.ai.ocr..",
                    "com.offercoach.ai.search..");

    // AI 域单向：业务模块可依赖 oc-ai，AI 域不得反向依赖业务模块。
    @ArchTest
    static final ArchRule ai_should_not_depend_on_business_modules = noClasses()
            .that()
            .resideInAPackage("com.offercoach.ai..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.offercoach.interview..",
                    "com.offercoach.admin..",
                    "com.offercoach.identity..",
                    "com.offercoach.report..");
}
