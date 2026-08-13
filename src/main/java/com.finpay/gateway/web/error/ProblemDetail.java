package com.finpay.gateway.web.error;

import java.util.Map;

/**
 * Gateway-local RFC-9457 shaped problem response, mirroring the shape of
 * {@code com.finpay:common-web} ProblemDetail (status/code/message/traceId/
 * details) so clients see one error model across the platform. The gateway
 * cannot import common-web directly because it is a reactive application while
 * common-web is servlet-stack (see CorrelationIdWebFilter). Never leaks
 * internal exception text.
 */
public record ProblemDetail(
        int status,
        String code,
        String message,
        String traceId,
        Map<String, Object> details) {
}