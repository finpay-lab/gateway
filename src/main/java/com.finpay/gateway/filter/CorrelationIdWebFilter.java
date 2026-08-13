package com.finpay.gateway.filter;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive counterpart of {@code com.finpay:common-web}'s
 * {@code CorrelationIdFilter}, kept wire-compatible on purpose.
 *
 * <p>Why not reuse common-web directly: that library targets the servlet
 * (MVC) stack, while Spring Cloud Gateway is a reactive (WebFlux) application
 * that cannot load {@code jakarta.servlet} filters. The gateway therefore
 * reimplements the same contract: every inbound request is assigned an
 * {@code X-Correlation-Id} (generated if absent), the id is echoed on the
 * response, placed in the MDC for structured logging, and forwarded on the
 * request so downstream services keep the same correlation id end-to-end
 * (OBSERVABILITY.md). Documented deviation, see PR_BODY / ADR-0006.
 *
 * <p>Known limitation: reactor may hop threads between subscription and
 * completion, so the MDC value may not survive every hand-off; the header
 * propagation (the part that matters for cross-service correlation) is exact.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        ServerWebExchange decorated = exchange.mutate()
                .request(exchange.getRequest().mutate().header(HEADER, correlationId).build())
                .build();
        decorated.getResponse().getHeaders().set(HEADER, correlationId);
        return chain.filter(decorated)
                .doFinally(signal -> MDC.remove(MDC_KEY));
    }
}