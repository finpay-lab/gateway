package com.finpay.gateway.filter;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    private MockServerWebExchange run(String correlationId) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get("/transfers/1");
        if (correlationId != null) {
            builder.header(CorrelationIdWebFilter.HEADER, correlationId);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(builder.build());
        AtomicReference<ServerWebExchange> downstream = new AtomicReference<>();
        WebFilterChain chain = chainExchange -> {
            downstream.set(chainExchange);
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void propagates_inbound_correlation_id_and_echoes_on_response() {
        MockServerWebExchange exchange = run("corr-123");

        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.HEADER))
                .isEqualTo("corr-123");
        assertThat(exchange.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER))
                .isEqualTo("corr-123");
        assertThat(MDC.get(CorrelationIdWebFilter.MDC_KEY)).isEqualTo("corr-123");
    }

    @Test
    void generates_correlation_id_when_absent() {
        MockServerWebExchange exchange = run(null);

        String responseHeader = exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.HEADER);
        assertThat(responseHeader).isNotNull().isNotEmpty();
        assertThat(UUID.fromString(responseHeader)).isNotNull();
        assertThat(exchange.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER))
                .isEqualTo(responseHeader);
        assertThat(MDC.get(CorrelationIdWebFilter.MDC_KEY)).isEqualTo(responseHeader);
    }

    @Test
    void clears_mdc_after_request_completes() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/").build());
        filter.filter(exchange, chainExchange -> Mono.empty()).block();

        assertThat(MDC.get(CorrelationIdWebFilter.MDC_KEY)).isNull();
    }
}