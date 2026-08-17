package com.finpay.gateway.upstream;

import java.time.Duration;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.finpay.gateway.config.GatewayProperties.CircuitBreakerPolicy;
import com.finpay.gateway.config.GatewayProperties.Upstream;

/**
 * Forwarding client with the Rule 8 resilience envelope: explicit timeouts
 * (set on the request factory), bounded retry with backoff for idempotent
 * methods only, and a circuit breaker that fails fast when an upstream is
 * unhealthy. Retrying non-idempotent calls would risk double-executing a
 * financial operation.
 */
public class UpstreamClient {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final int maxAttempts;
    private final Duration backoff;

    public UpstreamClient(RestClient restClient, Upstream upstream) {
        this.restClient = restClient;
        CircuitBreakerPolicy cb = upstream.circuitBreaker();
        this.circuitBreaker = new CircuitBreaker(cb.failureThreshold(), cb.openDuration());
        this.maxAttempts = upstream.maxAttempts();
        this.backoff = upstream.backoff();
    }

    public ResponseEntity<byte[]> exchange(org.springframework.http.RequestEntity<byte[]> request) {
        boolean idempotent = isIdempotent(request.getMethod());
        return circuitBreaker.execute(() -> attempt(request, idempotent));
    }

    private ResponseEntity<byte[]> attempt(org.springframework.http.RequestEntity<byte[]> request, boolean idempotent) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                org.springframework.web.client.RestClient.RequestBodyUriSpec spec =
                        restClient.method(request.getMethod()).uri(request.getUrl());
                spec.headers(headers -> headers.putAll(request.getHeaders()));
                if (request.getBody() != null) {
                    spec.body(request.getBody());
                }
                return spec.retrieve().toEntity(byte[].class);
            } catch (RestClientException e) {
                if (idempotent && attempts < maxAttempts) {
                    sleep(backoff);
                    continue;
                }
                throw new UpstreamUnavailableException(
                        "Upstream call failed after " + attempts + " attempt(s): " + request.getUrl(),
                        e);
            }
        }
    }

    private static boolean isIdempotent(HttpMethod method) {
        return method == HttpMethod.GET || method == HttpMethod.HEAD
                || method == HttpMethod.PUT || method == HttpMethod.DELETE
                || method == HttpMethod.OPTIONS;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamUnavailableException("Interrupted during upstream backoff", e);
        }
    }
}