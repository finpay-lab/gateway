package com.finpay.gateway.security;

import java.util.Base64;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Mints HS256 access tokens with the same shared lab secret the gateway
 * validates, so tests can exercise real token validation (SECURITY.md).
 */
public final class TestJwts {

    static final String ISSUER = "http://idp.test/realms/finpay";
    static final String AUDIENCE = "finpay-api";
    static final String SECRET = "Y2hhbmdlbWUtdGhpcy1sYWItc2VjcmV0LXRva2VuLWZvci1maW5wYXktZ2F0ZXdheQ==";

    private TestJwts() {
    }

    public static String mint(String subject, List<String> roles) throws Exception {
        return mint(subject, roles, new Date(System.currentTimeMillis() + 60_000));
    }

    /** Mint with an arbitrary secret (used to build tokens the gateway must reject). */
    public static String mintWithSecret(String subject, List<String> roles, String secret) throws Exception {
        return mint(subject, roles, new Date(System.currentTimeMillis() + 60_000), secret);
    }

    public static String mint(String subject, List<String> roles, Date expiration) throws Exception {
        return mint(subject, roles, expiration, SECRET);
    }

    private static String mint(String subject, List<String> roles, Date expiration, String secret) throws Exception {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .expirationTime(expiration);
        if (roles != null) {
            claims.claim("roles", roles);
        }
        SignedJWT jwt = new SignedJWT(header, claims.build());
        jwt.sign(new MACSigner(Base64.getDecoder().decode(secret)));
        return jwt.serialize();
    }
}