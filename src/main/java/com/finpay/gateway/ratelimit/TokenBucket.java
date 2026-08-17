package com.finpay.gateway.ratelimit;

/**
 * Pure token-bucket math (SECURITY.md: token-bucket in Redis). Kept framework
 * free so the accounting rules can be unit tested; the Redis implementation
 * persists the bucket state and only needs the resulting numbers.
 *
 * @param capacity        maximum number of tokens the bucket can hold
 * @param refillPerSecond tokens added per second
 */
public record TokenBucket(int capacity, int refillPerSecond) {

    /**
     * @param currentTokens   tokens currently in the bucket
     * @param lastRefillMillis epoch ms of the last refill computation
     * @param nowMillis       current epoch ms
     */
    public Result acquire(double currentTokens, long lastRefillMillis, long nowMillis) {
        double tokens = refill(currentTokens, lastRefillMillis, nowMillis);
        boolean allowed = tokens >= 1.0;
        double after = allowed ? tokens - 1.0 : tokens;
        long retryAfterSeconds = allowed
                ? 0
                : (long) Math.ceil((1.0 - after) / Math.max(1, refillPerSecond));
        return new Result(allowed, after, retryAfterSeconds);
    }

    private double refill(double currentTokens, long lastRefillMillis, long nowMillis) {
        long elapsedMillis = Math.max(0, nowMillis - lastRefillMillis);
        double added = elapsedMillis * (double) refillPerSecond / 1000.0;
        return Math.min(capacity, currentTokens + added);
    }

    /** Outcome of an {@link #acquire} attempt. */
    public record Result(boolean allowed, double tokensAfter, long retryAfterSeconds) {}
}