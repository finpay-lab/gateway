package com.finpay.gateway.web;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpay.common.web.error.ProblemDetail;
import com.finpay.common.web.filter.CorrelationIdFilter;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes the consistent RFC-9457 error model ({@code code}, {@code message},
 * {@code traceId}) used across the platform. Never leaks internal exception
 * text (SECURITY.md: internal exceptions never reach clients).
 */
public final class ProblemDetailWriter {

    private ProblemDetailWriter() {
    }

    public static void write(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            String code,
            String message
    ) throws IOException {
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        response.setStatus(status);
        response.setContentType("application/problem+json");
        objectMapper.writeValue(
                response.getWriter(),
                new ProblemDetail(status, code, message, traceId, Map.of()));
    }
}