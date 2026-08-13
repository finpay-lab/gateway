package com.finpay.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Propagates the verified principal to downstream services via trusted gateway
 * headers and removes the inbound {@code Authorization} header (ADR-0006:
 * "propagate principal via trusted headers"). Internal services must not trust
 * an arbitrary externally-issued JWT that bypassed the gateway; they consume
 * {@code X-User-Id}/{@code X-User-Roles} instead and may re-verify on sensitive
 * endpoints.
 */
@Component
public class PrincipalPropagationFilter implements GlobalFilter, Ordered {

    public static final String X_USER_ID = "X-User-Id";
    public static final String X_USER_ROLES = "X-User-Roles";
    public static final String AUTHORIZATION = HttpHeaders.AUTHORIZATION;

    @Override
    public int getOrder() {
        return 20_000;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(authentication -> authentication != null
                        && authentication.getPrincipal() instanceof Jwt)
                .flatMap(authentication -> {
                    ServerWebExchange decorated = decorateRequest(exchange, (Jwt) authentication.getPrincipal());
                    return chain.filter(decorated);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    static ServerWebExchange decorateRequest(ServerWebExchange exchange, Jwt jwt) {
        HttpHeaders decoratedHeaders = new HttpHeaders();
        decoratedHeaders.putAll(exchange.getRequest().getHeaders());
        decoratedHeaders.remove(AUTHORIZATION);
        if (jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            decoratedHeaders.set(X_USER_ID, jwt.getSubject());
        }
        String roles = roles(jwt);
        if (roles != null) {
            decoratedHeaders.set(X_USER_ROLES, roles);
        }
        return exchange.mutate()
                .request(exchange.getRequest().mutate().headers(h -> {
                    h.clear();
                    h.putAll(decoratedHeaders);
                }).build())
                .build();
    }

    /**
     * Keycloak puts roles in the {@code realm_access.roles} claim; fall back to
     * the {@code scope} claim for non-Keycloak issuers.
     */
    static String roles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map
                && map.get("roles") instanceof List<?> roles && !roles.isEmpty()) {
            return roles.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isBlank()) {
            return scope;
        }
        return null;
    }
}