package com.finpay.gateway.web.error;

/**
 * Stable machine-readable error codes emitted by the gateway. Names overlap
 * with {@code com.finpay:common-web} ErrorCode on purpose (single platform
 * error vocabulary); the gateway owns its copy because the shared enum cannot
 * be imported into the reactive gateway without dragging the servlet stack.
 */
public enum GatewayErrorCode {
    UNAUTHORIZED("Missing or invalid access token"),
    FORBIDDEN("Insufficient permissions"),
    RATE_LIMITED("Too many requests"),
    NOT_FOUND("Resource not found"),
    BAD_GATEWAY("Upstream service failed"),
    SERVICE_UNAVAILABLE("Upstream service unavailable"),
    INTERNAL_ERROR("An unexpected error occurred");

    private final String defaultMessage;

    GatewayErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public static GatewayErrorCode fromStatus(int status) {
        return switch (status) {
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 429 -> RATE_LIMITED;
            case 404 -> NOT_FOUND;
            case 502 -> BAD_GATEWAY;
            case 503 -> SERVICE_UNAVAILABLE;
            default -> INTERNAL_ERROR;
        };
    }
}