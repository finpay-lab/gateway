package com.finpay.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpay.gateway.config.TokenClaimsProperties;
import com.finpay.gateway.web.error.GatewayErrorCode;
import com.finpay.gateway.web.error.ProblemDetailWriter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TokenClaimsValidationFilterTest {

    private static final String ISSUER = "http://localhost:8081/realms/finpay";
    private static final String AUDIENCE = "finpay-api";
    private static final String AZP = "finpay-client";

    private final TokenClaimsProperties properties =
            new TokenClaimsProperties(ISSUER, AUDIENCE, AZP);
    private final TokenClaimsValidationFilter filter = new TokenClaimsValidationFilter(
            properties, new ProblemDetailWriter(new ObjectMapper()));

    private static Jwt jwt(String issuer, List<String> audience, String azp) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("user-1")
                .audience(audience)
                .claim("azp", azp)
                .build();
    }

    @Test
    void accepts_token_matching_all_expected_claims() {
        assertThat(TokenClaimsValidationFilter.validate(jwt(ISSUER, List.of(AUDIENCE), AZP), properties))
                .isEmpty();
    }

    @Test
    void rejects_token_with_wrong_issuer() {
        assertThat(TokenClaimsValidationFilter.validate(
                jwt("http://evil.example/realms/finpay", List.of(AUDIENCE), AZP), properties))
                .contains(GatewayErrorCode.UNAUTHORIZED);
    }

    @Test
    void rejects_token_with_wrong_audience() {
        assertThat(TokenClaimsValidationFilter.validate(
                jwt(ISSUER, List.of("some-other-api"), AZP), properties))
                .contains(GatewayErrorCode.UNAUTHORIZED);
    }

    @Test
    void rejects_token_with_wrong_azp() {
        assertThat(TokenClaimsValidationFilter.validate(
                jwt(ISSUER, List.of(AUDIENCE), "evil-client"), properties))
                .contains(GatewayErrorCode.UNAUTHORIZED);
    }

    @Test
    void passes_when_no_claim_expectations_configured() {
        TokenClaimsProperties none = new TokenClaimsProperties(null, null, null);
        assertThat(TokenClaimsValidationFilter.validate(
                jwt("http://whatever", List.of("x"), "y"), none))
                .isEmpty();
    }

    @Test
    void authenticated_request_with_invalid_claims_gets_401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/transfers/1").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainExchange -> {
                    chainCalled.set(true);
                    return Mono.empty();
                })
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new TestingAuthenticationToken(jwt("http://evil.example", List.of(AUDIENCE), AZP), "n/a")))
                .block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chainCalled).isFalse();
    }

    @Test
    void authenticated_request_with_valid_claims_is_routed() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/transfers/1").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainExchange -> {
                    chainCalled.set(true);
                    return Mono.empty();
                })
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new TestingAuthenticationToken(jwt(ISSUER, List.of(AUDIENCE), AZP), "n/a")))
                .block();

        assertThat(chainCalled).isTrue();
    }

    @Test
    void anonymous_request_passes_through() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.filter(exchange, chainExchange -> {
                    chainCalled.set(true);
                    return Mono.empty();
                })
                .block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}