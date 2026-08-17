package com.finpay.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenBucketTest {

    private static final long NOW = 1_000_000L;

    @Test
    void full_bucket_allows_capacity_requests_then_denies() {
        TokenBucket bucket = new TokenBucket(3, 10);
        long last = NOW;

        assertThat(bucket.acquire(3.0, last, NOW).allowed()).isTrue();
        assertThat(bucket.acquire(2.0, last, NOW).allowed()).isTrue();
        assertThat(bucket.acquire(1.0, last, NOW).allowed()).isTrue();

        TokenBucket.Result result = bucket.acquire(0.0, last, NOW);

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void refills_over_time() {
        TokenBucket bucket = new TokenBucket(10, 10);

        TokenBucket.Result afterOneSecond = bucket.acquire(0.0, NOW, NOW + 1_000);

        assertThat(afterOneSecond.allowed()).isTrue();
        assertThat(afterOneSecond.tokensAfter()).isEqualTo(9.0);
    }

    @Test
    void refill_is_capped_at_capacity() {
        TokenBucket bucket = new TokenBucket(5, 100);

        TokenBucket.Result result = bucket.acquire(4.0, NOW, NOW + 500);

        assertThat(result.allowed()).isTrue();
        assertThat(result.tokensAfter()).isEqualTo(4.0);
    }

    @Test
    void retry_after_is_zero_when_allowed() {
        TokenBucket bucket = new TokenBucket(10, 5);

        assertThat(bucket.acquire(5.0, NOW, NOW).retryAfterSeconds()).isZero();
    }

    @Test
    void retry_after_reflects_refill_rate() {
        TokenBucket bucket = new TokenBucket(10, 2);

        TokenBucket.Result result = bucket.acquire(0.0, NOW, NOW);

        assertThat(result.allowed()).isFalse();
        // Need 1 token at 2 tokens/sec -> 1 second.
        assertThat(result.retryAfterSeconds()).isEqualTo(1);
    }
}