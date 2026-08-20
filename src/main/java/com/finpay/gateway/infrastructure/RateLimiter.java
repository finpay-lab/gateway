package com.finpay.gateway.infrastructure;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-key token-bucket rate limiter (FP-3, Rule 8).
 *
 * Bucket capacity + refill rate are shared; keys are typically the resolved
 * subject or a fallback (IP). Thread-safe; state is in-memory only (sufficient
 * for a single-gateway-node lab; a production deployment would use Redis).
 */
public final class RateLimiter {

    private final long capacity;
    private final double refillPerSecond;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(long capacity, double refillPerSecond) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
    }

    /** @return true if the request is allowed (a token was consumed). */
    public boolean tryAcquire(String key) {
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(capacity, System.nanoTime()));
        return b.tryAcquire(refillPerSecond);
    }

    public long tokensRemaining(String key) {
        Bucket b = buckets.get(key);
        return b == null ? capacity : b.tokens(refillPerSecond);
    }

    private static final class Bucket {
        private final AtomicLong tokens;
        private volatile long lastNanos;

        Bucket(long capacity, long nowNanos) {
            this.tokens = new AtomicLong(capacity);
            this.lastNanos = nowNanos;
        }

        synchronized boolean tryAcquire(double refillPerSecond) {
            refill(refillPerSecond);
            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        synchronized long tokens(double refillPerSecond) {
            refill(refillPerSecond);
            return tokens.get();
        }

        private void refill(double refillPerSecond) {
            long now = System.nanoTime();
            double elapsed = (now - lastNanos) / 1_000_000_000.0;
            if (elapsed <= 0) return;
            long added = (long) (elapsed * refillPerSecond);
            if (added > 0) {
                long cur = tokens.get();
                // cap at a large bound to avoid overflow; capacity handled by caller
                if (cur < Long.MAX_VALUE - added) {
                    tokens.set(cur + added);
                }
                lastNanos = now;
            }
        }
    }
}
