package com.finpay.gateway.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpay.gateway.config.GatewayProperties;
import com.finpay.gateway.config.GatewayProperties.Auth;
import com.finpay.gateway.web.ProblemDetailWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gateway AuthN enforcement (SECURITY.md): validates the bearer JWT and attaches
 * the verified principal to the request context. Rejects with 401 when the
 * token is missing or invalid. Runs after correlation/security-header filters
 * and before rate limiting + forwarding.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    public static final String PRINCIPAL_ATTRIBUTE = GatewayPrincipal.class.getName();
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator validator;
    private final Auth auth;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtValidator validator, GatewayProperties properties, ObjectMapper objectMapper) {
        this.validator = validator;
        this.auth = properties.auth();
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isOpenPath(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = bearerToken(request);
        if (token == null) {
            log.warn("Unauthenticated request to {} rejected", request.getRequestURI());
            ProblemDetailWriter.write(response, objectMapper, 401, "UNAUTHENTICATED",
                    "Missing or malformed Authorization header");
            return;
        }
        try {
            GatewayPrincipal principal = validator.validate(token);
            request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT for {}: {}", request.getRequestURI(), e.getMessage());
            ProblemDetailWriter.write(response, objectMapper, 401, "UNAUTHENTICATED",
                    "Invalid or expired access token");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isOpenPath(String uri) {
        if (auth.openPaths() == null) {
            return false;
        }
        return auth.openPaths().stream()
                .anyMatch(p -> uri.equals(p) || uri.startsWith(p + "/"));
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length()).trim();
    }
}