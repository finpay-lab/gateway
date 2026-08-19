package com.finpay.gateway.domain.guard;

/**
 * Edge AI guardrail (AI-7): scores a request payload for prompt-injection and
 * anomalous patterns. Domain interface so implementations (heuristic, LLM)
 * live in {@code infrastructure} and remain swappable.
 *
 * <p>Implementations are expected to be idempotent — evaluating the same
 * payload twice yields the same risk — and to never throw for malformed input:
 * failures must degrade to {@link GuardDecision#low()} so the guardrail is
 * best-effort, never a request blocker by default.
 */
public interface RequestGuard {

    GuardDecision evaluate(String payload);
}
