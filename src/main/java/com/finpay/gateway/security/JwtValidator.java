package com.finpay.gateway.security;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.finpay.gateway.config.GatewayProperties.Auth;

/**
 * Validates the access-token JWT presented to the gateway (SECURITY.md: check
 * signature, {@code exp}, {@code iss}, {@code aud}) and extracts the verified
 * principal. Signature verification uses the IdP JWKS when {@code jwkSetUri} is
 * configured; the lab falls back to a symmetric HMAC secret because
 * identity-service is not yet built (documented lab shortcut).
 */
public final class JwtValidator {

    private final NimbusJwtDecoder decoder;
    private final String issuer;
    private final String audience;

    public JwtValidator(Auth auth) {
        this.issuer = auth.issuer();
        this.audience = auth.audience();
        if (auth.jwkSetUri() != null && !auth.jwkSetUri().isBlank()) {
            // Prod shape (SECURITY.md): Nimbus fetches and caches the JWKS with a
            // short TTL, so a stampede on the IdP is avoided.
            this.decoder = NimbusJwtDecoder.withJwkSetUri(auth.jwkSetUri()).build();
        } else {
            // Lab shortcut: HMAC secret shared with the (absent) IdP.
            byte[] keyBytes = Base64.getDecoder().decode(auth.secret());
            SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
            this.decoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
        }
    }

    /**
     * @return the verified principal, or throws {@link JwtException} on an
     * invalid signature, expired token, or unexpected issuer/audience.
     */
    public GatewayPrincipal validate(String token) {
        Jwt jwt = decoder.decode(token);
        if (!issuer.equals(jwt.getClaimAsString("iss"))) {
            throw new JwtException("Unexpected issuer: " + jwt.getClaimAsString("iss"));
        }
        if (jwt.getClaimAsStringList("aud") == null || !jwt.getClaimAsStringList("aud").contains(audience)) {
            throw new JwtException("Unexpected audience: " + jwt.getClaimAsStringList("aud"));
        }
        String subject = jwt.getSubject();
        Set<String> roles = new HashSet<>(jwt.getClaimAsStringList("roles") != null
                ? jwt.getClaimAsStringList("roles")
                : Set.of());
        return new GatewayPrincipal(subject, roles);
    }
}