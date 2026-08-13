package com.finpay.gateway.filter;

import com.finpay.gateway.config.TokenClaimsProperties;
import com.finpay.gateway.web.error.GatewayErrorCode;
import com.finpay.gateway.web.error.ProblemDetailWriter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * Post-authentication claim enforcement (ADR-0006): signature/JWKS and exp/nbf
 * are already validated by the resource-server decoder; this filter additionally
 * checks {@code iss}, {@code aud} and {@code azp} against the configured
 * expectations. Rejected tokens get a 401 problem detail before any request is
 * routed. Anonymous traffic (permit-all paths) passes through untouched.
 *
 * <p>Runs as a GlobalFilter so it executes only for matched routes, strictly
 * after Spring Security has authenticated the request (security is a WebFilter
 * that precedes gateway routing).
 */
@Component
public class TokenClaimsValidationFilter implements GlobalFilter, Ordered {

    private final TokenClaimsProperties properties;
    private final ProblemDetailWriter writer;

    public TokenClaimsValidationFilter(TokenClaimsProperties properties, ProblemDetailWriter writer) {
        this.properties = properties;
        this.writer = writer;
    }

    @Override
    public int getOrder() {
        return 15_000;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(authentication -> authentication != null
                        && authentication.getPrincipal() instanceof Jwt)
                .flatMap(authentication -> {
                    Jwt jwt = (Jwt) authentication.getPrincipal();
                    Optional<GatewayErrorCode> violation = validate(jwt, properties);
                    return violation
                            .map(code -> writer.write(exchange, 401, code))
                            .orElseGet(() -> chain.filter(exchange));
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    static Optional<GatewayErrorCode> validate(Jwt jwt, TokenClaimsProperties properties) {
        if (properties == null || !properties.isConfigured()) {
            return Optional.empty();
        }
        if (properties.expectedIssuer() != null && !properties.expectedIssuer().equals(jwt.getIssuer())) {
            return Optional.of(GatewayErrorCode.UNAUTHORIZED);
        }
        if (properties.expectedAudience() != null) {
            List<String> audience = jwt.getAudience();
            if (audience == null || !audience.contains(properties.expectedAudience())) {
                return Optional.of(GatewayErrorCode.UNAUTHORIZED);
            }
        }
        if (properties.expectedAzp() != null) {
            String azp = jwt.getClaimAsString("azp");
            if (azp == null || !properties.expectedAzp().equals(azp)) {
                return Optional.of(GatewayErrorCode.UNAUTHORIZED);
            }
        }
        return Optional.empty();
    }
}