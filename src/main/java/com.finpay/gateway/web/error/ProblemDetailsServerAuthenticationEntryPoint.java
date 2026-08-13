package com.finpay.gateway.web.error;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Renders 401 responses as problem details (consistent error model, SECURITY.md)
 * instead of Spring Security's empty body.
 */
@Component
public class ProblemDetailsServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ProblemDetailWriter writer;

    public ProblemDetailsServerAuthenticationEntryPoint(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return writer.write(exchange, HttpStatus.UNAUTHORIZED.value(), GatewayErrorCode.UNAUTHORIZED);
    }
}