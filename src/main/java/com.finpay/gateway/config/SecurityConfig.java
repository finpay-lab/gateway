package com.finpay.gateway.config;

import com.finpay.gateway.web.error.ProblemDetailsServerAccessDeniedHandler;
import com.finpay.gateway.web.error.ProblemDetailsServerAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Gateway security: JWT enforcement only (no business logic). Every exchange
 * except health checks and CORS preflight requires a valid JWT validated
 * against the IdP JWKS (ADR-0006). Auth failures are rendered as problem
 * details. Security headers (HSTS, X-Content-Type-Options, frame options, ...)
 * are added automatically by Spring Security's reactive header writer.
 */
@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(TokenClaimsProperties.class)
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ProblemDetailsServerAuthenticationEntryPoint authenticationEntryPoint,
            ProblemDetailsServerAccessDeniedHandler accessDeniedHandler) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/actuator/health/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/actuator/info").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(Customizer.withDefaults()))
                .build();
    }
}