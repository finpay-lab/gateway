package com.finpay.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PrincipalPropagationFilterTest {

    private final PrincipalPropagationFilter filter = new PrincipalPropagationFilter();

    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("user-1");
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void propagates_principal_and_strips_authorization() {
        Jwt jwt = jwtWithClaims(Map.of("realm_access", Map.of("roles", List.of("CUSTOMER", "ADMIN"))));
        MockServerHttpRequest request = MockServerHttpRequest.get("/accounts/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer abc")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, chainExchange -> {
                    downstream.set(chainExchange);
                    return Mono.empty();
                })
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new TestingAuthenticationToken(jwt, "n/a")))
                .block();

        HttpHeaders forwarded = downstream.get().getRequest().getHeaders();
        assertThat(forwarded.getFirst(PrincipalPropagationFilter.X_USER_ID)).isEqualTo("user-1");
        assertThat(forwarded.getFirst(PrincipalPropagationFilter.X_USER_ROLES))
                .isEqualTo("CUSTOMER,ADMIN");
        assertThat(forwarded.get(PrincipalPropagationFilter.AUTHORIZATION)).isEmpty();
    }

    @Test
    void falls_back_to_scope_when_no_realm_roles() {
        Jwt jwt = jwtWithClaims(Map.of("scope", "openid profile"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/accounts/1").build());
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, chainExchange -> {
                    downstream.set(chainExchange);
                    return Mono.empty();
                })
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new TestingAuthenticationToken(jwt, "n/a")))
                .block();

        HttpHeaders forwarded = downstream.get().getRequest().getHeaders();
        assertThat(forwarded.getFirst(PrincipalPropagationFilter.X_USER_ROLES)).isEqualTo("openid profile");
    }

    @Test
    void anonymous_request_is_not_decorated() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build());
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();

        filter.filter(exchange, chainExchange -> {
                    downstream.set(chainExchange);
                    return Mono.empty();
                })
                .block();

        assertThat(downstream.get()).isSameAs(exchange);
        assertThat(downstream.get().getRequest().getHeaders()
                .getFirst(PrincipalPropagationFilter.X_USER_ID)).isNull();
    }
}