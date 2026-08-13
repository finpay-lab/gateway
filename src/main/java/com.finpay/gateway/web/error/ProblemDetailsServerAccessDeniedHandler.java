package com.finpay.gateway.web.error;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Renders 403 responses as problem details (consistent error model, SECURITY.md)
 * instead of Spring Security's empty body.
 */
@Component
public class ProblemDetailsServerAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ProblemDetailWriter writer;

    public ProblemDetailsServerAccessDeniedHandler(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return writer.write(exchange, HttpStatus.FORBIDDEN.value(), GatewayErrorCode.FORBIDDEN);
    }
}