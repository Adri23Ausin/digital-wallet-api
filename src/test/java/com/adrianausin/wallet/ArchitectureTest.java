package com.adrianausin.wallet;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

/**
 * Keeps the hexagonal architecture honest: dependencies only point inwards, towards the domain.
 */
@AnalyzeClasses(packages = "com.adrianausin.wallet", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule hexagonalLayers = onionArchitecture()
            .withOptionalLayers(true)
            .domainModels("..domain..")
            .applicationServices("..application..")
            .adapter("web", "..adapter.in.web..")
            .adapter("persistence", "..adapter.out.persistence..")
            .adapter("security", "..adapter.out.security..");

    @ArchTest
    static final ArchRule domainIsPlainJava = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..")
            .because("the domain model must not depend on any framework");

    @ArchTest
    static final ArchRule jpaStaysInThePersistenceAdapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.out.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework.data..")
            .because("only the persistence adapter knows how data is stored");

    @ArchTest
    static final ArchRule webTypesStayInTheWebAdapter = noClasses()
            .that().resideInAnyPackage("..domain..", "..application..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "org.springframework.http..",
                    "org.springframework.security..")
            .because("use cases must not know they are called over HTTP");
}
