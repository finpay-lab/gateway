package com.finpay.gateway.domain;

/**
 * Result of evaluating an inbound request against the AI guardrail.
 *
 * The guardrail is intentionally NON-BLOCKING by default: it attaches a score
 * and optional headers but lets the request proceed, unless {@link #block()} is
 * true (block mode enabled and risk above threshold).
 */
public record GuardDecision(boolean block, double riskScore, String reason) {

    public static final GuardDecision BENIGN = new GuardDecision(false, 0.0, "ok");

    public static GuardDecision flag(double score, String reason) {
        return new GuardDecision(false, score, reason);
    }

    public static GuardDecision reject(double score, String reason) {
        return new GuardDecision(true, score, reason);
    }
}
