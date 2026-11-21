package com.unimag.avicontrol.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.unimag.avicontrol.application.usecase.galpon.GalponEstadoService;
import com.unimag.avicontrol.domain.model.galpon.EstadoGalpon;
import com.unimag.avicontrol.domain.model.galpon.Galpon;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de arquitectura (T019): infrastructure → application → domain, dominio y aplicación sin JPA,
 * {@code @Transactional} solo en los casos de uso y una única puerta para escribir el estado del galpón.
 * Solo se analiza el código de producción.
 */
@AnalyzeClasses(packages = "com.unimag.avicontrol", importOptions = ImportOption.DoNotIncludeTests.class)
class ReglasArquitecturaTest {

    // ---------------------------------------------------- dirección de dependencias

    @ArchTest
    static final ArchRule dominioNoDependeDeAplicacionNiInfraestructura = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..")
            .because("la dependencia va infrastructure → application → domain");

    @ArchTest
    static final ArchRule aplicacionNoDependeDeInfraestructura = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .because("la dependencia va infrastructure → application → domain");

    // ------------------------------------------------------------- JPA ausente

    @ArchTest
    static final ArchRule dominioEsJavaPuro = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..", "org.hibernate..", "org.springframework..",
                    "tools.jackson..", "com.fasterxml.jackson..")
            .because("el dominio es Java puro: sin Spring, JPA ni Jackson");

    @ArchTest
    static final ArchRule aplicacionNoUsaJpa = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..", "org.hibernate..", "org.springframework.data..")
            .because("la aplicación accede a los datos solo mediante los puertos de salida del dominio");

    // ------------------------------------------------------ @Transactional acotado

    @ArchTest
    static final ArchRule transactionalSoloEnLosCasosDeUsoEnClases = noClasses()
            .that().resideOutsideOfPackage("..application.usecase..")
            .should().beAnnotatedWith(Transactional.class)
            .because("los límites transaccionales se definen solo en application/usecase");

    @ArchTest
    static final ArchRule transactionalSoloEnLosCasosDeUsoEnMetodos = noMethods()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage("..application.usecase..")
            .should().beAnnotatedWith(Transactional.class)
            .because("los límites transaccionales se definen solo en application/usecase");

    // --------------------------------------------- única puerta de cambio de estado

    @ArchTest
    static final ArchRule soloGalponEstadoServiceCambiaElEstadoDelGalpon = noClasses()
            .that().doNotHaveFullyQualifiedName(GalponEstadoService.class.getName())
            .should().callMethod(Galpon.class, "conEstado", EstadoGalpon.class)
            .because("solo GalponEstadoService escribe el estado del galpón (FR-012)");
}
