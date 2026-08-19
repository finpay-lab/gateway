package com.finpay.gateway.domain.guard;

import java.util.ArrayList;
import java.util.List;

/**
 * Outcome of evaluating a payload against the AI guardrail.
 *
 * <p>Pure domain value: carries the combined {@link RiskLevel} and the
 * machine-readable reasons that contributed to it. A decision is blocked when
 * the risk is {@link RiskLevel#HIGH}.
 */
public record GuardDecision(RiskLevel risk, List<String> reasons) {

    public GuardDecision {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static GuardDecision low() {
        return new GuardDecision(RiskLevel.LOW, List.of());
    }

    public boolean isBlocked() {
        return risk == RiskLevel.HIGH;
    }

    /** Merges two decisions, keeping the highest risk and deduplicating reasons. */
    public GuardDecision combine(GuardDecision other) {
        if (other == null) {
            return this;
        }
        List<String> merged = new ArrayList<>(reasons);
        for (String reason : other.reasons) {
            if (!merged.contains(reason)) {
                merged.add(reason);
            }
        }
        return new GuardDecision(RiskLevel.max(risk, other.risk), merged);
    }
}
