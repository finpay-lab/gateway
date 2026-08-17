package com.finpay.gateway.security;

/**
 * Headers used to propagate the verified gateway principal to downstream
 * services (SECURITY.md: gateway attaches subject + roles to the request
 * context). Downstream services must still re-verify tokens on sensitive
 * endpoints (defense in depth) and must never trust these headers from
 * unauthenticated traffic.
 */
public final class GatewayHeaders {

    /** Downstream subject (JWT {@code sub}). */
    public static final String SUBJECT = "X-FinPay-Subject";
    /** Downstream roles as a comma-separated list. */
    public static final String ROLES = "X-FinPay-Roles";

    private GatewayHeaders() {
    }
}