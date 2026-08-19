package com.finpay.gateway.domain.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class GuardDecisionTest {

    @Test
    void low_is_not_blocked() {
        assertThat(GuardDecision.low().isBlocked()).isFalse();
    }

    @Test
    void high_is_blocked() {
        assertThat(new GuardDecision(RiskLevel.HIGH, List.of("jailbreak")).isBlocked()).isTrue();
    }

    @Test
    void combine_keeps_highest_risk_and_merges_reasons() {
        GuardDecision low = new GuardDecision(RiskLevel.LOW, List.of("heuristic"));
        GuardDecision high = new GuardDecision(RiskLevel.HIGH, List.of("jailbreak", "heuristic"));

        GuardDecision merged = low.combine(high);

        assertThat(merged.risk()).isEqualTo(RiskLevel.HIGH);
        assertThat(merged.reasons()).containsExactlyInAnyOrder("heuristic", "jailbreak");
    }

    @Test
    void combine_is_associative_for_risk() {
        GuardDecision a = new GuardDecision(RiskLevel.MEDIUM, List.of("a"));
        GuardDecision b = new GuardDecision(RiskLevel.HIGH, List.of("b"));
        GuardDecision c = new GuardDecision(RiskLevel.LOW, List.of("c"));

        assertThat(a.combine(b).combine(c).risk()).isEqualTo(RiskLevel.HIGH);
        assertThat(RiskLevel.max(RiskLevel.MEDIUM, RiskLevel.LOW)).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    void reasons_are_defensively_copied() {
        List<String> reasons = new java.util.ArrayList<>(List.of("x"));
        GuardDecision decision = new GuardDecision(RiskLevel.MEDIUM, reasons);
        reasons.add("y");
        assertThat(decision.reasons()).containsExactly("x");
    }
}
