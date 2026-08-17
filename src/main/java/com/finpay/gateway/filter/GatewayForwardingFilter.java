package com.finpay.gateway.filter;

import java.io.IOException;
import java.net.URI;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpay.common.web.filter.CorrelationIdFilter;
import com.finpay.gateway.config.GatewayProperties;
import com.finpay.gateway.config.GatewayProperties.Route;
import com.finpay.gateway.routing.NoRouteFoundException;
import com.finpay.gateway.routing.RouteRegistry;
import com.finpay.gateway.security.GatewayHeaders;
import com.finpay.gateway.security.GatewayPrincipal;
import com.finpay.gateway.security.JwtAuthenticationFilter;
import com.finpay.gateway.upstream.CircuitBreaker;
import com.finpay.gateway.upstream.UpstreamClient;
import com.finpay.gateway.upstream.UpstreamUnavailableException;
import com.finpay.gateway.web.ProblemDetailWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Routes the request to the matched downstream service and writes the upstream
 * response back. No business logic lives here — only transport concerns:
 * route resolution, coarse RBAC (SECURITY.md: enforced at the gateway), header
 * sanitisation and propagation of the verified principal.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GatewayForwardingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayForwardingFilter.class);

    /** Headers that must never be forwarded in either direction (RFC 9110). */
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade");

    private final GatewayProperties properties;
    private final RouteRegistry routeRegistry;
    private final UpstreamClient upstreamClient;
    private final ObjectMapper objectMapper;

    public GatewayForwardingFilter(
            GatewayProperties properties,
            RouteRegistry routeRegistry,
            UpstreamClient upstreamClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.routeRegistry = routeRegistry;
        this.upstreamClient = upstreamClient;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (isOpenPath(path)) {
            writeHealth(response);
            return;
        }
        try {
            Route route = routeRegistry.resolve(path)
                    .orElseThrow(() -> new NoRouteFoundException(path));
            authorize(route, request);
            ResponseEntity<byte[]> upstream = upstreamClient.exchange(buildForwardRequest(request, route));
            writeUpstreamResponse(response, upstream);
        } catch (NoRouteFoundException e) {
            ProblemDetailWriter.write(response, objectMapper, 404, "NOT_FOUND", e.getMessage());
        } catch (ForbiddenException e) {
            ProblemDetailWriter.write(response, objectMapper, 403, "FORBIDDEN",
                    "The authenticated principal lacks the required role");
        } catch (UpstreamUnavailableException | CircuitBreaker.CircuitOpenException e) {
            log.warn("Upstream unavailable for {}: {}", path, e.getMessage());
            ProblemDetailWriter.write(response, objectMapper, 503, "UPSTREAM_UNAVAILABLE",
                    "The requested service is temporarily unavailable");
        } catch (Exception e) {
            log.error("Unhandled gateway error for {}: {}", path, e.getMessage(), e);
            ProblemDetailWriter.write(response, objectMapper, 500, "INTERNAL_ERROR",
                    "An unexpected gateway error occurred");
        }
    }

    /** Coarse RBAC: the principal needs one of the route's required roles. */
    private void authorize(Route route, HttpServletRequest request) {
        List<String> required = route.requiredRoles();
        if (required == null || required.isEmpty()) {
            return;
        }
        GatewayPrincipal principal = (GatewayPrincipal) request.getAttribute(JwtAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        boolean allowed = principal != null
                && principal.roles().stream().anyMatch(required::contains);
        if (!allowed) {
            throw new ForbiddenException(route.id());
        }
    }

    /** Role check failed (SECURITY.md coarse gateway RBAC); mapped to 403. */
    private static final class ForbiddenException extends RuntimeException {
        private ForbiddenException(String routeId) {
            super("Principal lacks a required role for route: " + routeId);
        }
    }

    private RequestEntity<byte[]> buildForwardRequest(HttpServletRequest request, Route route) {
        String target = route.uri() + RouteRegistry.forwardPath(route, request.getRequestURI());
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            target += "?" + request.getQueryString();
        }
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            if (isSafeToForward(name)) {
                headers.add(name, request.getHeader(name));
            }
        }
        // Verified principal + correlation are the identity the downstream trusts.
        GatewayPrincipal principal = (GatewayPrincipal) request.getAttribute(JwtAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (principal != null) {
            headers.set(GatewayHeaders.SUBJECT, principal.subject());
            headers.set(GatewayHeaders.ROLES, String.join(",", principal.roles()));
        }
        headers.set(CorrelationIdFilter.HEADER, correlationId(request));

        byte[] body = hasRequestBody(method) ? readBody(request) : null;
        return RequestEntity.method(method, URI.create(target)).headers(headers).body(body);
    }

    private void writeUpstreamResponse(HttpServletResponse response, ResponseEntity<byte[]> upstream) throws IOException {
        response.setStatus(upstream.getStatusCode().value());
        upstream.getHeaders().forEach((name, values) -> {
            if (!HOP_BY_HOP.contains(name.toLowerCase()) && !name.equalsIgnoreCase("content-length")) {
                for (String value : values) {
                    response.addHeader(name, value);
                }
            }
        });
        byte[] body = upstream.getBody();
        if (body != null && body.length > 0) {
            response.getOutputStream().write(body);
        }
    }

    private void writeHealth(HttpServletResponse response) throws IOException {
        response.setStatus(200);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":\"UP\"}");
    }

    private boolean isOpenPath(String uri) {
        return properties.auth().openPaths() != null
                && properties.auth().openPaths().stream()
                        .anyMatch(p -> uri.equals(p) || uri.startsWith(p + "/"));
    }

    private static boolean isSafeToForward(String name) {
        String lower = name.toLowerCase();
        if (HOP_BY_HOP.contains(lower)) {
            return false;
        }
        // Never forward the bearer token; the verified principal replaces it.
        return !lower.equals("host")
                && !lower.equals("content-length")
                && !lower.equals("authorization");
    }

    private static boolean hasRequestBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }

    private static byte[] readBody(HttpServletRequest request) {
        try {
            return request.getInputStream().readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read request body", e);
        }
    }

    private static String correlationId(HttpServletRequest request) {
        String header = request.getHeader(CorrelationIdFilter.HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        String mdc = MDC.get(CorrelationIdFilter.MDC_KEY);
        return mdc != null ? mdc : UUID.randomUUID().toString();
    }
}