package com.finpay.gateway.ratelimit;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpay.common.web.error.ErrorCode;
import com.finpay.gateway.config.GatewayBeans;
import com.finpay.gateway.config.GatewayProperties;
import com.finpay.gateway.config.GatewayProperties.RateLimitPolicy;
import com.finpay.gateway.config.GatewayProperties.Route;
import com.finpay.gateway.routing.RouteRegistry;
import com.finpay.gateway.security.GatewayPrincipal;
import com.finpay.gateway.security.JwtAuthenticationFilter;
import com.finpay.gateway.web.ProblemDetailWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate limiting (SECURITY.md: per-client / per-token / per-IP token bucket in
 * Redis). Checks the caller IP and, for authenticated requests, the token
 * subject; the matched route selects the policy (critical financial routes have
 * tighter buckets). A limited request gets 429 + {@code Retry-After}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final GatewayProperties properties;
    private final RateLimitService rateLimitService;
    private final RouteRegistry routeRegistry;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            GatewayProperties properties,
            RateLimitService rateLimitService,
            RouteRegistry routeRegistry,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.routeRegistry = routeRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Route route = routeRegistry.resolve(request.getRequestURI()).orElse(null);
        if (route == null || isOpenPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        RateLimitPolicy policy = GatewayBeans.policyFor(properties, route.id());
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }
        long now = System.currentTimeMillis();

        if (!rateLimitService.tryAcquire("ip:" + clientIp(request), policy, now).allowed()) {
            reject(response, "Too many requests from this IP");
            return;
        }
        GatewayPrincipal principal = (GatewayPrincipal) request.getAttribute(JwtAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (principal != null
                && !rateLimitService.tryAcquire("subject:" + principal.subject(), policy, now).allowed()) {
            reject(response, "Too many requests for this client");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setHeader("Retry-After", "1");
        log.warn("Rate limited: {}", message);
        ProblemDetailWriter.write(response, objectMapper, 429, ErrorCode.RATE_LIMITED.name(), message);
    }

    private boolean isOpenPath(String uri) {
        return properties.auth().openPaths() != null
                && properties.auth().openPaths().stream()
                        .anyMatch(p -> uri.equals(p) || uri.startsWith(p + "/"));
    }

    /** Real client IP: honour a single-value X-Forwarded-For in front of a trusted LB. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}