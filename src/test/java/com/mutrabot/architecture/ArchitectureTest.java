package com.mutrabot.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.mutrabot", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_depends_only_on_jdk_and_itself =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideOutsideOfPackages("java..", "com.mutrabot.domain..");

    @ArchTest
    static final ArchRule application_depends_only_on_domain_and_jdk =
            noClasses().that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideOutsideOfPackages("java..", "com.mutrabot.domain..", "com.mutrabot.application..");

    @ArchTest
    static final ArchRule inbound_adapters_do_not_depend_on_outbound_adapters =
            noClasses().that().resideInAPackage("..adapter.in..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..adapter.out..");

    @ArchTest
    static final ArchRule outbound_adapters_do_not_depend_on_inbound_adapters =
            noClasses().that().resideInAPackage("..adapter.out..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..adapter.in..");

    @ArchTest
    static final ArchRule only_bootstrap_depends_on_bootstrap =
            noClasses().that().resideOutsideOfPackage("..bootstrap..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..bootstrap..");

    @ArchTest
    static final ArchRule domain_does_not_depend_on_libraries =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("net.dv8tion..", "com.sedmelluq..", "dev.lavalink..", "org.slf4j..");
}
