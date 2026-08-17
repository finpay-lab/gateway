package com.finpay.gateway.routing;

import java.util.List;
import java.util.Optional;

import com.finpay.gateway.config.GatewayProperties.Route;

/**
 * Routing table (SERVICE_CATALOG.md: gateway routes to downstream services).
 * Longest-prefix match wins so {@code /payment/...} beats {@code /pay...}.
 */
public final class RouteRegistry {

    private final List<Route> routes;

    public RouteRegistry(List<Route> routes) {
        this.routes = List.copyOf(routes)
                .stream()
                .sorted((a, b) -> Integer.compare(b.path().length(), a.path().length()))
                .toList();
    }

    public Optional<Route> resolve(String requestPath) {
        return routes.stream()
                .filter(r -> requestPath.equals(r.path()) || requestPath.startsWith(r.path() + "/"))
                .findFirst();
    }

    /**
     * Path suffix forwarded to the upstream service (route prefix stripped),
     * preserving the request query string.
     */
    public static String forwardPath(Route route, String requestUri) {
        String suffix = requestUri.substring(route.path().length());
        return suffix.isEmpty() ? "/" : suffix;
    }
}