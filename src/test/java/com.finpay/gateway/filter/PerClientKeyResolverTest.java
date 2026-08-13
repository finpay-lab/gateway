package com.finpay.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

class PerClientKeyResolverTest {

    private final PerClientKeyResolver resolver = new PerClientKeyResolver();

    @Test
    void uses_jwt_subject_for_authenticated_request() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("user-42").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/accounts/1").build());

        String key = resolver.resolve(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new TestingAuthenticationToken(jwt, "n/a")))
                .block();

        assertThat(key).isEqualTo("user-42");
    }

    @Test
    void falls_back_to_client_ip_for_anonymous_request() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health")
                .remoteAddress(new InetSocketAddress("10.1.2.3", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("10.1.2.3");
    }
}