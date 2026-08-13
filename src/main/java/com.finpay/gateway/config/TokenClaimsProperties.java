package com.finpay.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Expected JWT claims for tokens accepted by the gateway (ADR-0006: the gateway
 * validates signature/JWKS, exp, iss, aud, azp). Signature + exp are handled by
 * the resource-server decoder; iss/aud/azp are enforced by
 * {@code TokenClaimsValidationFilter} using these expected values. Each field is
 * optional; when null the corresponding check is skipped.
 */
@ConfigurationProperties(prefix = "finpay.gateway.security")
public record TokenClaimsProperties(
        String expectedIssuer,
        String expectedAudience,
        String expectedAzp) {

    public boolean isConfigured() {
        return expectedIssuer != null || expectedAudience != null || expectedAzp != null;
    }
}