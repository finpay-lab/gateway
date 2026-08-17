package com.finpay.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import com.finpay.gateway.config.GatewayProperties.Auth;

class JwtValidatorTest {

    private JwtValidator validator;

    @BeforeEach
    void setUp() {
        validator = new JwtValidator(new Auth(TestJwts.ISSUER, TestJwts.AUDIENCE, null, TestJwts.SECRET, List.of()));
    }

    @Test
    void validates_signature_and_extracts_principal() throws Exception {
        String token = TestJwts.mint("alice", List.of("CUSTOMER", "ADMIN"));

        GatewayPrincipal principal = validator.validate(token);

        assertThat(principal.subject()).isEqualTo("alice");
        assertThat(principal.roles()).containsExactlyInAnyOrder("CUSTOMER", "ADMIN");
    }

    @Test
    void accepts_token_without_roles_claim() throws Exception {
        String token = TestJwts.mint("bob", null);

        GatewayPrincipal principal = validator.validate(token);

        assertThat(principal.subject()).isEqualTo("bob");
        assertThat(principal.roles()).isEmpty();
    }

    @Test
    void rejects_token_signed_with_different_key() throws Exception {
        String token = TestJwts.mint("alice", List.of("CUSTOMER"));
        JwtValidator other = new JwtValidator(new Auth(
                TestJwts.ISSUER, TestJwts.AUDIENCE, null,
                java.util.Base64.getEncoder().encodeToString("another-secret-that-is-different-32-bytes".getBytes()),
                List.of()));

        assertThatThrownBy(() -> other.validate(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejects_expired_token() throws Exception {
        String token = TestJwts.mint("alice", List.of("CUSTOMER"), new Date(System.currentTimeMillis() - 60_000));

        assertThatThrownBy(() -> validator.validate(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejects_wrong_issuer() throws Exception {
        String token = TestJwts.mint("alice", List.of("CUSTOMER"));
        JwtValidator other = new JwtValidator(new Auth("http://evil.test", TestJwts.AUDIENCE, null, TestJwts.SECRET, List.of()));

        assertThatThrownBy(() -> other.validate(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejects_wrong_audience() throws Exception {
        String token = TestJwts.mint("alice", List.of("CUSTOMER"));
        JwtValidator other = new JwtValidator(new Auth(TestJwts.ISSUER, "other-api", null, TestJwts.SECRET, List.of()));

        assertThatThrownBy(() -> other.validate(token)).isInstanceOf(JwtException.class);
    }
}