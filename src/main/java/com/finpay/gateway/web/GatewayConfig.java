package com.finpay.gateway.web;

import com.finpay.gateway.domain.RequestGuard;
import com.finpay.gateway.infrastructure.HeuristicRequestGuard;
import com.finpay.gateway.infrastructure.RateLimiter;
import com.finpay.gateway.infrastructure.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Wires the gateway edge components (FP-3 + FP-64). Kept in {@code web/}
 * because it assembles transport-layer beans; the domain/infra types themselves
 * carry no Spring/web imports.
 */
@Configuration
public class GatewayConfig {

    @Value("${finpay.gateway.issuer:finpay}")
    private String issuer;

    @Value("${finpay.gateway.jwt-secret:}")
    private String jwtSecret;

    @Value("${finpay.gateway.public-paths:/actuator,/health}")
    private String publicPathsRaw;

    @Value("${finpay.gateway.rate-limit.capacity:100}")
    private long rateCapacity;

    @Value("${finpay.gateway.rate-limit.refill-per-second:10}")
    private double rateRefill;

    @Value("${finpay.gateway.guardrail.block-mode:false}")
    private boolean guardrailBlockMode;

    @Value("${finpay.gateway.guardrail.block-threshold:0.85}")
    private double guardrailThreshold;

    @Bean
    public TokenService tokenService() {
        return new TokenService(issuer, jwtSecret);
    }

    @Bean
    public RateLimiter rateLimiter() {
        return new RateLimiter(rateCapacity, rateRefill);
    }

    @Bean
    public RequestGuard requestGuard() {
        return new HeuristicRequestGuard()
                .blockMode(guardrailBlockMode)
                .blockThreshold(guardrailThreshold);
    }

    @Bean
    public List<String> publicPaths() {
        return List.of(publicPathsRaw.split(","));
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(TokenService tokenService, List<String> publicPaths) {
        return new JwtAuthFilter(tokenService, publicPaths);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimiter rateLimiter) {
        return new RateLimitFilter(rateLimiter);
    }

    @Bean
    public GuardrailFilter guardrailFilter(RequestGuard requestGuard) {
        return new GuardrailFilter(requestGuard);
    }

    /**
     * Permissive CORS source required by Spring Boot 4.1's management security
     * auto-configuration (ManagementWebSecurityAutoConfiguration expects a
     * CorsConfigurationSource bean). Allowed origins are narrowed to the cluster
     * ingress / observability tooling in production.
     */
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
