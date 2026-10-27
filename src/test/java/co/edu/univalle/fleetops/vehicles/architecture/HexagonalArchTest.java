package co.edu.univalle.fleetops.vehicles.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Reglas de la arquitectura hexagonal: las dependencias apuntan siempre hacia el dominio. */
@AnalyzeClasses(packages = "co.edu.univalle.fleetops.vehicles", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchTest {

    private static final String DOMAIN = "co.edu.univalle.fleetops.vehicles.domain..";
    private static final String APPLICATION = "co.edu.univalle.fleetops.vehicles.application..";
    private static final String APPLICATION_PORT_IN = "co.edu.univalle.fleetops.vehicles.application.port.in..";
    private static final String APPLICATION_PORT_OUT =
            "co.edu.univalle.fleetops.vehicles.application.port.out..";
    private static final String INFRASTRUCTURE = "co.edu.univalle.fleetops.vehicles.infrastructure..";

    @ArchTest
    static final ArchRule domainDoesNotDependOnOuterLayers = noClasses().that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, INFRASTRUCTURE);

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses().that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..",
                    "javax..", "org.hibernate..", "com.fasterxml..", "lombok..");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure = noClasses().that()
            .resideInAPackage(APPLICATION).should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE);

    @ArchTest
    static final ArchRule applicationIsFrameworkFree = noClasses().that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta..",
                    "javax..", "org.hibernate..", "com.fasterxml..", "lombok..");

    @ArchTest
    static final ArchRule portsAreInterfaces = classes().that()
            .resideInAnyPackage(APPLICATION_PORT_IN, APPLICATION_PORT_OUT)
            .should().beInterfaces();

    @ArchTest
    static final ArchRule useCasesOnlyDependOnPortsNotOnEachOther = noClasses().that()
            .resideInAPackage("co.edu.univalle.fleetops.vehicles.application.usecase..").should().dependOnClassesThat()
            .resideInAPackage("co.edu.univalle.fleetops.vehicles.application.usecase..");

    @ArchTest
    static final ArchRule controllersOnlyUseInboundPorts = noClasses().that()
            .resideInAPackage("co.edu.univalle.fleetops.vehicles.infrastructure.web.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "co.edu.univalle.fleetops.vehicles.application.usecase..",
                    "co.edu.univalle.fleetops.vehicles.infrastructure.persistence..");

    @ArchTest
    static final ArchRule adaptersImplementOutboundPorts = classes().that()
            .resideInAnyPackage("co.edu.univalle.fleetops.vehicles.infrastructure.persistence.adapter..",
                    "co.edu.univalle.fleetops.vehicles.infrastructure.client..")
            .should().implement(com.tngtech.archunit.base.DescribedPredicate.describe("an outbound port",
                    port -> port.getPackageName().equals(
                            "co.edu.univalle.fleetops.vehicles.application.port.out")));
}
