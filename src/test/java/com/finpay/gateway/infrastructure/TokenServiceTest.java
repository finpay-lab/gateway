package com.finpay.gateway.infrastructure;

import com.finpay.common.security.Role;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static String makeToken(String payloadJson, String secret) throws Exception {
        String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = b64(payloadJson);
        String signingInput = header + "." + payload;
        String sig = sign(signingInput, secret);
        return signingInput + "." + sig;
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String sign(String input, String secret) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void validTokenParsesClaims() throws Exception {
        long exp = System.currentTimeMillis() / 1000L + 3600;
        String payload = "{\"iss\":\"finpay\",\"sub\":\"user-42\",\"roles\":[\"CUSTOMER\"],\"exp\":" + exp + "}";
        String token = makeToken(payload, "s3cr3t");
        TokenService svc = new TokenService("finpay", "s3cr3t");
        TokenService.Claims c = svc.parse(token);
        assertThat(c.subject()).isEqualTo("user-42");
        assertThat(c.roles()).contains(Role.CUSTOMER);
        assertThat(c.expired()).isFalse();
    }

    @Test
    void wrongSignatureIsRejected() throws Exception {
        String payload = "{\"iss\":\"finpay\",\"sub\":\"u\",\"exp\":" + (System.currentTimeMillis()/1000+3600) + "}";
        String token = makeToken(payload, "right-secret") + "x"; // tamper sig
        TokenService svc = new TokenService("finpay", "right-secret");
        assertThatThrownBy(() -> svc.parse(token))
                .isInstanceOf(TokenService.InvalidTokenException.class);
    }

    @Test
    void expiredTokenDetected() throws Exception {
        String payload = "{\"iss\":\"finpay\",\"sub\":\"u\",\"exp\":1}";
        String token = makeToken(payload, "s3cr3t");
        TokenService svc = new TokenService("finpay", "s3cr3t");
        TokenService.Claims c = svc.parse(token);
        assertThat(c.expired()).isTrue();
    }

    @Test
    void malformedTokenRejected() {
        TokenService svc = new TokenService("finpay", "s3cr3t");
        assertThatThrownBy(() -> svc.parse("not-a-jwt"))
                .isInstanceOf(TokenService.InvalidTokenException.class);
    }
}
