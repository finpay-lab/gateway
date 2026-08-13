package com.finpay.gateway.web.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpay.gateway.filter.CorrelationIdWebFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.UncheckedIOException;
import java.util.Map;

/** Serializes a {@link ProblemDetail} to a JSON error response. */
@Component
public class ProblemDetailWriter {

    private final ObjectMapper objectMapper;

    public ProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, int status, GatewayErrorCode code) {
        exchange.getResponse().setStatusCode(HttpStatus.valueOf(status));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String traceId = exchange.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER);
        ProblemDetail problem = new ProblemDetail(
                status, code.name(), code.defaultMessage(), traceId, Map.of());
        return exchange.getResponse().writeWith(Mono.fromSupplier(() -> {
            try {
                return exchange.getResponse()
                        .bufferFactory()
                        .wrap(objectMapper.writeValueAsBytes(problem));
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException("Failed to serialize problem detail", e);
            }
        }));
    }
}