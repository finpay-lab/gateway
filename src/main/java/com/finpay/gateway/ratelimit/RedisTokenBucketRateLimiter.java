package com.finpay.gateway.ratelimit;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.finpay.gateway.config.GatewayProperties.RateLimitPolicy;

/**
 * Redis-backed token bucket (SECURITY.md). Bucket state lives in two keys
 * ({@code tokens}, {@code last-refill-ts}); a Lua script performs the update as
 * an atomic compare-and-set so concurrent requests never over-consume. If Redis
 * is unreachable the gateway fails open (documented in runtime-architecture.md),
 * because an availability loss must not block all traffic.
 */
public class RedisTokenBucketRateLimiter implements RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBucketRateLimiter.class);

    private static final String KEY_PREFIX = "gateway:ratelimit:";
    private static final long KEY_TTL_SECONDS = 300;
    private static final int MAX_CAS_ATTEMPTS = 10;

    /** Initializes the key if absent, otherwise CAS-updates the stored tokens. */
    private static final String CAS_LUA = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                redis.call('SET', KEYS[1], ARGV[2])
                redis.call('SET', KEYS[2], ARGV[3])
                redis.call('EXPIRE', KEYS[1], ARGV[4])
                redis.call('EXPIRE', KEYS[2], ARGV[4])
                return 1
            end
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2])
                redis.call('SET', KEYS[2], ARGV[3])
                redis.call('EXPIRE', KEYS[1], ARGV[4])
                redis.call('EXPIRE', KEYS[2], ARGV[4])
                return 1
            end
            return 0
            """;

    private final StringRedisTemplate template;
    private final DefaultRedisScript<Long> casScript;

    public RedisTokenBucketRateLimiter(StringRedisTemplate template) {
        this.template = template;
        this.casScript = new DefaultRedisScript<>(CAS_LUA, Long.class);
    }

    @Override
    public Result tryAcquire(String key, RateLimitPolicy policy, long nowMillis) {
        TokenBucket bucket = new TokenBucket(policy.capacity(), policy.refillPerSecond());
        String tokensKey = KEY_PREFIX + key + ":tokens";
        String tsKey = KEY_PREFIX + key + ":ts";

        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            try {
                Double current = currentTokens(tokensKey, policy.capacity());
                long lastRefill = lastRefill(tsKey, nowMillis);
                TokenBucket.Result result = bucket.acquire(current, lastRefill, nowMillis);
                if (!result.allowed()) {
                    return new Result(false, result.retryAfterSeconds());
                }
                Long applied = template.execute(
                        casScript,
                        List.of(tokensKey, tsKey),
                        format(current),
                        format(result.tokensAfter()),
                        Long.toString(nowMillis),
                        Long.toString(KEY_TTL_SECONDS));
                if (Long.valueOf(1L).equals(applied)) {
                    return new Result(true, 0);
                }
            } catch (DataAccessException e) {
                log.warn("Rate-limit Redis unavailable, failing open: {}", e.getMessage());
                return new Result(true, 0);
            }
        }
        log.warn("Rate-limit CAS contention exceeded {} attempts for {}", MAX_CAS_ATTEMPTS, key);
        return new Result(true, 0);
    }

    private Double currentTokens(String key, int capacity) {
        String raw = template.opsForValue().get(key);
        return raw == null ? capacity : Double.parseDouble(raw);
    }

    private long lastRefill(String key, long nowMillis) {
        String raw = template.opsForValue().get(key);
        return raw == null ? nowMillis : Long.parseLong(raw);
    }

    private static String format(double value) {
        return Double.toString(value);
    }
}