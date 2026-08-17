package com.finpay.gateway.security;

import java.util.Set;

/**
 * Verified principal attached to the request context after the gateway has
 * validated the JWT (SECURITY.md). The gateway uses these for coarse RBAC;
 * fine-grained authorization stays in the services.
 */
public record GatewayPrincipal(String subject, Set<String> roles) {}
