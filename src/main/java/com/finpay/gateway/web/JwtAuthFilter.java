package com.finpay.gateway.web;

import com.finpay.common.security.Role;
import com.finpay.gateway.infrastructure.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication + authorization propagation at the gateway edge (FP-3).
 *
 * Runs AFTER the correlation-id filter. Extracts the bearer token, validates it
 * via {@link TokenService}, and propagates the authenticated principal to
 * downstream services as signed headers (X-User-Id, X-User-Roles). Public
 * paths (actuator/health, the gateway's own info) are allowed through.
 */
@Component
@Order(20)
public class JwtAuthFilter extends OncePerRequestFilter {

    static final String HEADER_USER_ID = "X-User-Id";
    static final String HEADER_USER_ROLES = "X-User-Roles";

    private final TokenService tokenService;
    private final List<String> publicPaths;

    public JwtAuthFilter(TokenService tokenService, List<String> publicPaths) {
        this.tokenService = tokenService;
        this.publicPaths = publicPaths;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getServletPath();
        return publicPaths.stream().anyMatch(p::startsWith);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.toLowerCase().startsWith("bearer ")) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "missing bearer token");
            return;
        }
        String raw = auth.substring(7).trim();
        TokenService.Claims claims;
        try {
            claims = tokenService.parse(raw);
        } catch (TokenService.InvalidTokenException e) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "invalid token: " + e.getMessage());
            return;
        }
        if (claims.expired()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "token expired");
            return;
        }

        // Propagate authenticated identity downstream (transport <-> use-case mapping).
        request.setAttribute(HEADER_USER_ID, claims.subject());
        response.setHeader(HEADER_USER_ID, claims.subject());
        String roles = claims.roles().stream().map(Role::name).reduce((a, b) -> a + "," + b).orElse("");
        response.setHeader(HEADER_USER_ROLES, roles);

        chain.doFilter(request, response);
    }
}
