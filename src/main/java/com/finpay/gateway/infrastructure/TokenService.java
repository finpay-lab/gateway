package com.finpay.gateway.infrastructure;

import com.finpay.common.security.Role;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free JWT verifier for the gateway edge (FP-3).
 *
 * Validates the compact JWS structure, decodes the payload, checks the issuer
 * and expiry, and extracts the subject + roles. Signature verification is
 * performed only when an HMAC secret is configured (otherwise the gateway runs
 * in "opaque token" mode and trusts the issuer claim — acceptable for the lab
 * topology where the gateway and IdP share a secret in production).
 */
public final class TokenService {

    private final String expectedIssuer;
    private final byte[] hmacSecret; // nullable

    public TokenService(String expectedIssuer, String hmacSecret) {
        this.expectedIssuer = expectedIssuer;
        this.hmacSecret = (hmacSecret == null || hmacSecret.isBlank())
                ? null
                : hmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    /** Parsed, validated token claims. */
    public record Claims(String subject, List<Role> roles, boolean expired) {}

    /**
     * @return parsed claims, or throws {@link InvalidTokenException} on any
     *         structural / validation failure. Idempotent for the same token.
     */
    public Claims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("missing token");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException("malformed token");
        }
        Map<String, Object> payload = decodeJson(parts[1]);

        Object iss = payload.get("iss");
        if (expectedIssuer != null && !expectedIssuer.equals(iss)) {
            throw new InvalidTokenException("unexpected issuer");
        }
        Object exp = payload.get("exp");
        boolean expired = false;
        if (exp instanceof Number n) {
            expired = System.currentTimeMillis() / 1000L > n.longValue();
        }
        String sub = String.valueOf(payload.getOrDefault("sub", ""));

        List<Role> roles = extractRoles(payload);

        // Signature check (only if a secret is configured).
        if (hmacSecret != null) {
            String expected = sign(parts[0], parts[1], hmacSecret);
            if (!constantTimeEquals(expected, parts[2])) {
                throw new InvalidTokenException("bad signature");
            }
        }
        return new Claims(sub, roles, expired);
    }

    @SuppressWarnings("unchecked")
    private List<Role> extractRoles(Map<String, Object> payload) {
        Object r = payload.get("roles");
        if (r instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(Role::valueOf)
                    .toList();
        }
        // Keycloak-style realm_access.roles
        Object realm = payload.get("realm_access");
        if (realm instanceof Map<?, ?> m) {
            Object rolesObj = m.get("roles");
            if (rolesObj instanceof List<?> list) {
                return list.stream().map(String::valueOf).map(Role::valueOf).toList();
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeJson(String b64) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(b64);
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, Map.class);
        } catch (Exception e) {
            throw new InvalidTokenException("undecodable payload");
        }
    }

    private static String sign(String headerB64, String payloadB64, byte[] secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"));
            byte[] sig = mac.doFinal((headerB64 + "." + payloadB64).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new InvalidTokenException("sign failed");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    public static final class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) { super(message); }
    }
}
