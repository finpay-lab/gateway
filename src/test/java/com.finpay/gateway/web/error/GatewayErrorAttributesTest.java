package com.finpay.gateway.web.error;

import com.finpay.gateway.filter.CorrelationIdWebFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.support.DefaultServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayErrorAttributesTest {

    private final GatewayErrorAttributes attributes = new GatewayErrorAttributes();

    private static final String ERROR_ATTRIBUTE =
            "org.springframework.boot.web.reactive.error.DefaultErrorAttributes.ERROR";

    private ServerRequest withError(MockServerWebExchange exchange, Throwable error) {
        exchange.getAttributes().put(ERROR_ATTRIBUTE, error);
        return ServerRequest.create(exchange, new DefaultServerCodecConfigurer().getReaders());
    }

    @Test
    void produces_problem_detail_shape_without_leaking_internal_text() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/transfers/1").header(CorrelationIdWebFilter.HEADER, "trace-1"));
        ServerRequest request = withError(exchange, new IllegalStateException("internal db secret"));

        Map<String, Object> result = attributes.getErrorAttributes(request, ErrorAttributeOptions.defaults());

        assertThat(result).containsEntry("status", 500);
        assertThat(result).containsEntry("code", "INTERNAL_ERROR");
        assertThat(result).containsEntry("traceId", "trace-1");
        assertThat(result.get("message")).isEqualTo(GatewayErrorCode.INTERNAL_ERROR.defaultMessage());
        assertThat(String.valueOf(result.get("message"))).doesNotContain("secret");
        assertThat(result.get("details")).isInstanceOf(Map.class);
    }

    @Test
    void derives_status_and_code_from_response_status_exception() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/transfers/1"));
        ServerRequest request = withError(exchange, new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE));

        Map<String, Object> result = attributes.getErrorAttributes(request, ErrorAttributeOptions.defaults());

        assertThat(result).containsEntry("status", 503);
        assertThat(result).containsEntry("code", "SERVICE_UNAVAILABLE");
    }
}