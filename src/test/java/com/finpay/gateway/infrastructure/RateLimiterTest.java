package com.finpay.gateway.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void allowsUpToCapacityThenThrottles() {
        RateLimiter limiter = new RateLimiter(3, 0.0); // no refill
        String key = "client-1";
        assertThat(limiter.tryAcquire(key)).isTrue();
        assertThat(limiter.tryAcquire(key)).isTrue();
        assertThat(limiter.tryAcquire(key)).isTrue();
        assertThat(limiter.tryAcquire(key)).isFalse();
        assertThat(limiter.tokensRemaining(key)).isZero();
    }

    @Test
    void separateKeysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, 0.0);
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("b")).isTrue(); // different bucket
        assertThat(limiter.tryAcquire("a")).isFalse();
    }
}
