package com.finpay.gateway.web;

import com.finpay.common.web.error.ErrorCode;
import com.finpay.gateway.infrastructure.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-client rate limiting at the edge (FP-3, Rule 8). Every remote dependency
 * — including the gateway's own clients — defines a limit; this enforces it
 * with a token bucket. The bucket key is the authenticated user id when present,
 * otherwise the client address. On exhaustion returns 429 with the stable
 * {@link ErrorCode#RATE_LIMITED} semantics.
 */
@Component
@Order(30)
public class RateLimitFilter extends OncePerRequestFilter {

    static final String HEADER_REMAINING = "X-RateLimit-Remaining";

    private final RateLimiter limiter;

    public RateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String key = resolveKey(request);
        if (!limiter.tryAcquire(key)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "1");
            response.getWriter().write("{\"error\":\"" + ErrorCode.RATE_LIMITED.name() + "\"}");
            return;
        }
        response.setHeader(HEADER_REMAINING, String.valueOf(limiter.tokensRemaining(key)));
        chain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest request) {
        Object uid = request.getAttribute(JwtAuthFilter.HEADER_USER_ID);
        if (uid != null) return "u:" + uid;
        return "ip:" + request.getRemoteAddr();
    }
}
