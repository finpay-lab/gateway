package com.finpay.gateway.domain;

import java.util.Map;

/**
 * Edge guardrail that scores an inbound request for prompt-injection /
 * anomalous patterns (AI-7, FP-64).
 *
 * The domain contract is infrastructure-agnostic: it receives the request
 * payload/headers and returns a {@link GuardDecision}. Implementations live in
 * {@code infrastructure/}. The default heuristic implementation needs no
 * external LLM, but a BYOK-backed implementation can be plugged in (ADR-0011).
 */
public interface RequestGuard {

    /**
     * Evaluate a request.
     *
     * @param method   HTTP method
     * @param path     request path
     * @param headers  request headers (lower-cased keys)
     * @param body     request body (may be empty for GET)
     * @return decision; MUST be idempotent for the same inputs
     */
    GuardDecision evaluate(String method, String path, Map<String, String> headers, String body);
}
