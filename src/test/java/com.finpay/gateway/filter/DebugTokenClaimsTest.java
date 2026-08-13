package com.finpay.gateway.filter;

import com.finpay.gateway.config.TokenClaimsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

class DebugTokenClaimsTest {

    @Test
    void dump() {
        String issuer = "http://localhost:8081/realms/finpay";
        String audience = "finpay-api";
        String azp = "finpay-client";
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256")
                .issuer(issuer).subject("user-1")
                .audience(List.of(audience))
                .claim("azp", azp).build();
        TokenClaimsProperties p = new TokenClaimsProperties(issuer, audience, azp);
        throw new AssertionError("JWT iss=" + jwt.getIssuer()
                + " | aud=" + jwt.getAudience()
                + " | azp=" + jwt.getClaimAsString("azp")
                + " | props=" + p
                + " | isConfigured=" + p.isConfigured()
                + " | issMatch=" + p.expectedIssuer().equals(jwt.getIssuer())
                + " | validate=" + TokenClaimsValidationFilter.validate(jwt, p)
                + " | claims=" + jwt.getClaims());
    }
}