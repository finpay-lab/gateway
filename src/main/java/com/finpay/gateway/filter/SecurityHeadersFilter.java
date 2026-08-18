package com.finpay.gateway.filter;

import java.io.IOException;
import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.finpay.gateway.config.GatewayProperties;
import com.finpay.gateway.config.GatewayProperties.Cors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Edge threat protection (SECURITY.md): security headers (CSP, HSTS,
 * nosniff, frame/embedding deny) on every response, plus CORS handling for the
 * configured allowed origins. Runs right after correlation so even early
 * rejections (401/429) carry the headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final List<String> ALLOW_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> ALLOW_HEADERS =
            List.of("Authorization", "Content-Type", "X-Correlation-Id", "Idempotency-Key");

    private final Cors cors;

    public SecurityHeadersFilter(GatewayProperties properties) {
        this.cors = properties.cors();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        applySecurityHeaders(request, response);
        if (applyCors(request, response)) {
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void applySecurityHeaders(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
    }

    /** @return true when the request was a CORS preflight that must not continue. */
    private boolean applyCors(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin == null || !isAllowedOrigin(origin)) {
            return false;
        }
        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Vary", "Origin");
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeader("Access-Control-Request-Method") != null) {
            response.setHeader("Access-Control-Allow-Methods", String.join(", ", ALLOW_METHODS));
            response.setHeader("Access-Control-Allow-Headers", String.join(", ", ALLOW_HEADERS));
            response.setHeader("Access-Control-Max-Age", "3600");
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return true;
        }
        return false;
    }

    private boolean isAllowedOrigin(String origin) {
        return cors != null && cors.allowedOrigins() != null
                && (cors.allowedOrigins().contains("*") || cors.allowedOrigins().contains(origin));
    }
}