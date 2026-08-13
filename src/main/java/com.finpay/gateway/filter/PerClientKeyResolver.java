package com.finpay.gateway.filter;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Rate-limit key: the authenticated principal (JWT subject) when a token was
 * presented, falling back to the client IP for anonymous/permit-all traffic
 * (per-client / per-token / per-IP, SECURITY.md).
 */
@Component
public class PerClientKeyResolver implements KeyResolver {

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(this::principalKey)
                .defaultIfEmpty(clientIp(exchange));
    }

    private String principalKey(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            return jwt.getSubject();
        }
        if (principal instanceof String name && !name.isBlank()) {
            return name;
        }
        if (authentication.getName() != null && !authentication.getName().isBlank()) {
            return authentication.getName();
        }
        return "anonymous";
    }

    private String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null
                ? remote.getAddress().getHostAddress()
                : "unknown";
    }
}