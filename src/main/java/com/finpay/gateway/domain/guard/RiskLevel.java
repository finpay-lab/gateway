package com.finpay.gateway.domain.guard;

/**
 * Risk classification produced by the AI guardrail (AI-7).
 *
 * <p>Ordered from least to most severe; the guardrail's final decision is the
 * highest risk reported by any guard implementation.
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH;

    /** The most severe of the two levels. */
    public static RiskLevel max(RiskLevel a, RiskLevel b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
