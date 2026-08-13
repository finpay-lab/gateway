package com.finpay.gateway;

import com.finpay.common.test.ArchitectureRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * Enforces the shared platform architecture rules (consumed from the
 * finpay-platform composite build) plus gateway-specific constraints. The
 * gateway is pure transport: it must never grow a domain layer.
 */
@AnalyzeClasses(packages = "com.finpay.gateway")
class GatewayArchitectureTest {

    @ArchTest
    static final ArchRule domainIsIndependentOfInfrastructure =
            ArchitectureRules.domainIsIndependentOfInfrastructure();

    @ArchTest
    static final ArchRule gatewayHasNoDomainLayer = ArchRuleDefinition.noClasses()
            .that().resideInAPackage("com.finpay.gateway")
            .should().resideInAPackage("com.finpay.gateway..domain..");
}