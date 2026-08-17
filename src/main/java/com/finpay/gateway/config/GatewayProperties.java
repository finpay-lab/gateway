package com.finpay.gateway.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway configuration: auth, rate limiting, CORS, upstream resilience and the
 * routing table. Binds {@code gateway.*} from application.yml. The gateway owns
 * no data (SERVICE_CATALOG.md) so this is the only "domain" it has.
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        Auth auth,
        Cors cors,
        Security security,
        RateLimit rateLimit,
        Upstream upstream,
        List<Route> routes) {

    /** JWT validation (Rule 6 / SECURITY.md). */
    public record Auth(String issuer, String audience, String jwkSetUri, String secret, List<String> openPaths) {}

    public record Cors(List<String> allowedOrigins) {}

    public record Security(boolean enabled) {}

    public record RateLimit(boolean enabled, RateLimitPolicy defaultPolicy, Map<String, RateLimitPolicy> routes) {}

    /** Token-bucket shape (capacity in tokens, refill in tokens/second). */
    public record RateLimitPolicy(int capacity, int refillPerSecond) {}

    /** Rule 8: timeouts, retries and circuit breaker for upstream calls. */
    public record Upstream(
            Duration connectTimeout,
            Duration readTimeout,
            int maxAttempts,
            Duration backoff,
            CircuitBreakerPolicy circuitBreaker) {}

    public record CircuitBreakerPolicy(int failureThreshold, Duration openDuration) {}

    /** One routing entry: external path prefix -> downstream service base URI. */
    public record Route(String id, String path, String uri, List<String> requiredRoles) {}
}