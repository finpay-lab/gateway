package com.finpay.gateway.web;

import com.finpay.gateway.domain.GuardDecision;
import com.finpay.gateway.domain.RequestGuard;
import com.finpay.common.web.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * AI guardrail at the gateway edge (FP-64 / AI-7). Scores inbound requests for
 * prompt-injection / anomalous patterns via {@link RequestGuard}, then:
 *   - logs + attaches X-Guardrail-Risk / X-Guardrail-Reason headers (always),
 *   - blocks (403) only when the decision is {@link GuardDecision#block()}.
 *
 * Runs AFTER JWT auth and BEFORE routing (per spec). Non-blocking by default.
 *
 * The request is wrapped in a {@link ContentCachingRequestWrapper} so the body
 * can be read here without consuming the servlet input stream (which would
 * break downstream controllers / other filters that also read the body).
 */
@Component
@Order(25)
public class GuardrailFilter extends OncePerRequestFilter {

    public static final String HEADER_RISK = "X-Guardrail-Risk";
    public static final String HEADER_REASON = "X-Guardrail-Reason";

    private static final Logger log = LoggerFactory.getLogger(GuardrailFilter.class);

    private final RequestGuard guard;

    public GuardrailFilter(RequestGuard guard) {
        this.guard = guard;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // Cache the body so it can be read repeatedly without exhausting the stream.
        ContentCachingRequestWrapper wrapped =
                new ContentCachingRequestWrapper(request, 1_000_000);

        String body = readBody(wrapped);
        Map<String, String> headers = collectHeaders(wrapped);
        GuardDecision decision = guard.evaluate(
                wrapped.getMethod(), wrapped.getServletPath(), headers, body);

        response.setHeader(HEADER_RISK, String.format("%.2f", decision.riskScore()));
        if (decision.reason() != null && !decision.reason().isBlank()) {
            response.setHeader(HEADER_REASON, decision.reason());
        }

        if (decision.block()) {
            log.warn("guardrail BLOCKED {} {} risk={} reason={}",
                    wrapped.getMethod(), wrapped.getServletPath(),
                    decision.riskScore(), decision.reason());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.getWriter().write("{\"error\":\"" + ErrorCode.RISK_REJECTED.name() + "\"}");
            return;
        }
        if (decision.riskScore() > 0.0) {
            log.info("guardrail FLAGGED {} {} risk={} reason={}",
                    wrapped.getMethod(), wrapped.getServletPath(),
                    decision.riskScore(), decision.reason());
        }
        chain.doFilter(wrapped, response);
    }

    private static Map<String, String> collectHeaders(HttpServletRequest req) {
        var m = new java.util.HashMap<String, String>();
        var names = req.getHeaderNames();
        if (names == null) return Collections.emptyMap();
        while (names.hasMoreElements()) {
            String n = names.nextElement();
            m.put(n.toLowerCase(java.util.Locale.ROOT), req.getHeader(n));
        }
        return m;
    }

    private static String readBody(ContentCachingRequestWrapper req) {
        byte[] content = req.getContentAsByteArray();
        if (content == null || content.length == 0) return "";
        return new String(content, StandardCharsets.UTF_8);
    }
}
