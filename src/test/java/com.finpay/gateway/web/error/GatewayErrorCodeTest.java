package com.finpay.gateway.web.error;

import org.junit.jupiter.api.Test;

import static com.finpay.gateway.web.error.GatewayErrorCode.BAD_GATEWAY;
import static com.finpay.gateway.web.error.GatewayErrorCode.FORBIDDEN;
import static com.finpay.gateway.web.error.GatewayErrorCode.INTERNAL_ERROR;
import static com.finpay.gateway.web.error.GatewayErrorCode.NOT_FOUND;
import static com.finpay.gateway.web.error.GatewayErrorCode.RATE_LIMITED;
import static com.finpay.gateway.web.error.GatewayErrorCode.SERVICE_UNAVAILABLE;
import static com.finpay.gateway.web.error.GatewayErrorCode.UNAUTHORIZED;
import static org.assertj.core.api.Assertions.assertThat;

class GatewayErrorCodeTest {

    @Test
    void maps_http_status_to_stable_code() {
        assertThat(GatewayErrorCode.fromStatus(401)).isEqualTo(UNAUTHORIZED);
        assertThat(GatewayErrorCode.fromStatus(403)).isEqualTo(FORBIDDEN);
        assertThat(GatewayErrorCode.fromStatus(429)).isEqualTo(RATE_LIMITED);
        assertThat(GatewayErrorCode.fromStatus(404)).isEqualTo(NOT_FOUND);
        assertThat(GatewayErrorCode.fromStatus(502)).isEqualTo(BAD_GATEWAY);
        assertThat(GatewayErrorCode.fromStatus(503)).isEqualTo(SERVICE_UNAVAILABLE);
        assertThat(GatewayErrorCode.fromStatus(500)).isEqualTo(INTERNAL_ERROR);
    }

    @Test
    void every_code_has_a_safe_default_message() {
        for (GatewayErrorCode code : GatewayErrorCode.values()) {
            assertThat(code.defaultMessage()).isNotBlank();
        }
    }
}