package com.finpay.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.finpay.gateway.config.GatewayProperties;
import com.finpay.gateway.config.GatewayProperties.Cors;
import com.finpay.gateway.config.GatewayProperties.RateLimit;
import com.finpay.gateway.config.GatewayProperties.RateLimitPolicy;
import com.finpay.gateway.config.GatewayProperties.Route;
import com.finpay.gateway.config.GatewayProperties.Upstream;
import com.finpay.gateway.config.GatewayProperties.Auth;
import com.finpay.gateway.config.GatewayProperties.Security;
import com.finpay.gateway.config.GatewayProperties.CircuitBreakerPolicy;
import com.finpay.gateway.ratelimit.RateLimitFilter;
import com.finpay.gateway.ratelimit.RateLimitService;
import com.finpay.gateway.routing.RouteRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;

/**
 * Rate limiting at the gateway edge (SECURITY.md): a limited request is rejected
 * with 429 + Retry-After before it ever reaches the downstream service.
 *
 * <p>Exercised directly against {@link RateLimitFilter} (no Spring context) so
 * the denying limiter is unambiguous — the SB4 test container replaced the
 * {@code @MockBean} mechanism, and wiring a denying {@link RateLimitService}
 * through the full context proved flaky against the {@code noOpRateLimiter}
 * {@code @ConditionalOnMissingBean}.
 */
class GatewayRateLimitTest {

    private static final RateLimitService DENY_ALL =
            (key, policy, nowMillis) -> new RateLimitService.Result(false, 1);

    @Test
    void limited_request_returns_429_problem_with_retry_after() throws Exception {
        Route customer = new Route("customer", "/customer", "http://127.0.0.1:1", List.of("CUSTOMER"));
        GatewayProperties props = new GatewayProperties(
                new Auth(null, null, null, null, List.of("/healthz")),
                new Cors(List.of("*")),
                new Security(true),
                new RateLimit(true, new RateLimitPolicy(10, 5), Map.of()),
                new Upstream(null, null, 1, null, new CircuitBreakerPolicy(2, java.time.Duration.ofSeconds(30))),
                List.of(customer));
        RateLimitFilter filter = new RateLimitFilter(
                props, DENY_ALL, new RouteRegistry(List.of(customer)), new ObjectMapper());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/customer/ping");
        request.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new AssertionError("rate-limited request must not reach the chain");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getContentType()).contains("application/problem+json");
    }
}
