package com.finpay.gateway.interfaces.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpay.common.web.error.ProblemDetail;
import com.finpay.gateway.application.guard.RequestGuardService;
import com.finpay.gateway.domain.guard.GuardDecision;
import com.finpay.gateway.infrastructure.guard.GuardProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Edge AI guardrail filter (AI-7). Runs after JWT/authentication filters and
 * before routing (the {@link org.springframework.web.servlet.DispatcherServlet})
 * by sitting in the servlet filter chain with an order just above Spring
 * Security's default. Non-blocking by default: it logs and attaches
 * {@code X-FinPay-Guard-Risk} / {@code X-FinPay-Guard-Reasons} headers. With
 * {@code gateway.guard.block=true}, a {@code HIGH} verdict is rejected with 403
 * and an RFC-9457 {@link ProblemDetail} body.
 *
 * <p>Idempotent: {@link OncePerRequestFilter} guarantees a single evaluation
 * per request, and the guard evaluation itself is a pure, stateless read of the
 * cached body.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 200)
public class RequestGuardFilter extends OncePerRequestFilter {

    public static final String HEADER_RISK = "X-FinPay-Guard-Risk";
    public static final String HEADER_REASONS = "X-FinPay-Guard-Reasons";

    private static final Logger log = LoggerFactory.getLogger(RequestGuardFilter.class);

    private final RequestGuardService guardService;
    private final GuardProperties properties;
    private final ObjectMapper objectMapper;

    public RequestGuardFilter(RequestGuardService guardService, GuardProperties properties, ObjectMapper objectMapper) {
        this.guardService = guardService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.enabled() || !isGuardable(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
        String body = cached.getBody();
        GuardDecision decision = guardService.guard(body);

        response.setHeader(HEADER_RISK, decision.risk().name());
        if (!decision.reasons().isEmpty()) {
            response.setHeader(HEADER_REASONS, String.join(",", decision.reasons()));
        }

        if (decision.risk().ordinal() >= 1) {
            log.warn("AI guardrail flagged {} risk on {} {}: {}",
                    decision.risk(), request.getMethod(), request.getRequestURI(), decision.reasons());
        }

        if (properties.block() && decision.isBlocked()) {
            reject(response, decision);
            return;
        }

        filterChain.doFilter(cached, response);
    }

    private boolean isGuardable(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null || path.startsWith("/actuator") || path.startsWith("/health")) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType == null) {
            return true;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.contains("json") || ct.contains("text") || ct.contains("xml") || ct.contains("form");
    }

    private void reject(HttpServletResponse response, GuardDecision decision) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json");
        ProblemDetail problem = new ProblemDetail(
                HttpStatus.FORBIDDEN.value(),
                "GUARD_REJECTED",
                "Request blocked by the gateway AI guardrail (high prompt-injection / anomaly risk)",
                String.valueOf(MDC.get("correlationId")),
                Map.of("risk", decision.risk().name(), "reasons", decision.reasons()));
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
