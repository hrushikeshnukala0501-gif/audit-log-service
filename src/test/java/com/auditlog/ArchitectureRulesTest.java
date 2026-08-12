package com.auditlog;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Prevents the current, documented package boundaries from drifting unnoticed.
 * These rules deliberately do not claim a ports-and-adapters architecture.
 */
@AnalyzeClasses(packages = "com.auditlog")
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule controllers_do_not_access_persistence_directly = noClasses()
            .that().resideInAPackage("..api.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure.persistence.entity..",
                    "..infrastructure.persistence.repository..");

    @ArchTest
    static final ArchRule persistence_does_not_depend_on_api = noClasses()
            .that().resideInAPackage("..infrastructure.persistence..")
            .should().dependOnClassesThat().resideInAPackage("..api..");

    @ArchTest
    static final ArchRule support_does_not_depend_on_higher_layers = noClasses()
            .that().resideInAPackage("..support..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..api..",
                    "..application..",
                    "..infrastructure.persistence..",
                    "..config..");

    @ArchTest
    static final ArchRule configuration_does_not_depend_on_controllers = noClasses()
            .that().resideInAPackage("..config..")
            .should().dependOnClassesThat().resideInAPackage("..api.controller..");

}
