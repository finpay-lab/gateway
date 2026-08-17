package com.finpay.gateway.ratelimit;

import com.finpay.gateway.config.GatewayProperties.RateLimitPolicy;

/** Fail-open limiter used when rate limiting is disabled. */
public class NoOpRateLimiter implements RateLimitService {

    @Override
    public Result tryAcquire(String key, RateLimitPolicy policy, long nowMillis) {
        return new Result(true, 0);
    }
}