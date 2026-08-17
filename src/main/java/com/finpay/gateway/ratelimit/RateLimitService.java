package com.finpay.gateway.ratelimit;

import com.finpay.gateway.config.GatewayProperties.RateLimitPolicy;

/**
 * Gateway rate-limit port (SECURITY.md: per-client / per-token / per-IP limits,
 * token-bucket in Redis). Implementations decide how the bucket state is stored.
 */
public interface RateLimitService {

    /**
     * @param key          stable identity of the limited actor (ip / subject)
     * @param policy       bucket shape for the matched route
     * @param nowMillis    current time so callers can share a clock
     * @return whether the request is allowed plus seconds until a token frees up
     */
    Result tryAcquire(String key, RateLimitPolicy policy, long nowMillis);

    /** Success or {@code retryAfterSeconds} until the bucket refills. */
    record Result(boolean allowed, long retryAfterSeconds) {}
}