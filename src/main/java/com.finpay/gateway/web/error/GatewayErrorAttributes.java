package com.finpay.gateway.web.error;

import com.finpay.gateway.filter.CorrelationIdWebFilter;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps any error that reaches the reactive error chain into the platform
 * problem-details shape (status/code/message/traceId/details). Internal
 * exception text is never exposed to clients (SECURITY.md). Implemented by
 * customizing {@link ErrorAttributes} so the default error web handler keeps
 * doing status/content-type negotiation.
 */
@Component
public class GatewayErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(
            ServerRequest request, ErrorAttributeOptions options) {
        int status = resolveStatus(getError(request));
        GatewayErrorCode code = GatewayErrorCode.fromStatus(status);
        String traceId = request.headers().firstHeader(CorrelationIdWebFilter.HEADER);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("path", request.path());

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("status", status);
        problem.put("code", code.name());
        problem.put("message", code.defaultMessage());
        problem.put("traceId", traceId);
        problem.put("details", details);
        return problem;
    }

    private int resolveStatus(Throwable error) {
        if (error instanceof ResponseStatusException rse) {
            return rse.getStatusCode().value();
        }
        if (error instanceof WebClientResponseException wce) {
            return wce.getStatusCode().value();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
}