package com.finpay.gateway.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.finpay.gateway.config.GatewayProperties.RateLimitPolicy;
import com.finpay.gateway.ratelimit.NoOpRateLimiter;
import com.finpay.gateway.ratelimit.RateLimitService;
import com.finpay.gateway.ratelimit.RedisTokenBucketRateLimiter;
import com.finpay.gateway.routing.RouteRegistry;
import com.finpay.gateway.security.JwtValidator;
import com.finpay.gateway.upstream.UpstreamClient;

/**
 * Gateway bean wiring. The gateway is a pure edge: beans here are transport and
 * resilience concerns only, never business logic.
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayBeans {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    /** Policy for a route id, falling back to the global default. */
    public static RateLimitPolicy policyFor(GatewayProperties props, String routeId) {
        if (props.rateLimit() == null || props.rateLimit().defaultPolicy() == null) {
            return null;
        }
        RateLimitPolicy policy = props.rateLimit().routes() != null
                ? props.rateLimit().routes().get(routeId)
                : null;
        return policy != null ? policy : props.rateLimit().defaultPolicy();
    }

    @Bean
    RouteRegistry routeRegistry(GatewayProperties props) {
        return new RouteRegistry(props.routes());
    }

    /**
     * Rule 8: the upstream HTTP client enforces explicit connect/read timeouts
     * before any request is issued.
     */
    @Bean
    RestClient gatewayRestClient(GatewayProperties props) {
        GatewayProperties.Upstream upstream = props.upstream();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(toMillis(upstream != null ? upstream.connectTimeout() : DEFAULT_TIMEOUT));
        factory.setReadTimeout(toMillis(upstream != null ? upstream.readTimeout() : DEFAULT_TIMEOUT));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean
    @ConditionalOnProperty(name = "gateway.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
    RateLimitService redisRateLimiter(GatewayProperties props, org.springframework.data.redis.core.StringRedisTemplate template) {
        return new RedisTokenBucketRateLimiter(template);
    }

    /** Fail-open fallback when rate limiting is disabled. */
    @Bean
    @ConditionalOnMissingBean(RateLimitService.class)
    RateLimitService noOpRateLimiter() {
        return new NoOpRateLimiter();
    }

    @Bean
    JwtValidator jwtValidator(GatewayProperties props) {
        return new JwtValidator(props.auth());
    }

    @Bean
    UpstreamClient upstreamClient(RestClient restClient, GatewayProperties props) {
        return new UpstreamClient(restClient, props.upstream());
    }

    private static int toMillis(Duration duration) {
        return (int) duration.toMillis();
    }
}