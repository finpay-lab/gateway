package com.finpay.gateway.application.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.finpay.gateway.domain.guard.GuardDecision;
import com.finpay.gateway.domain.guard.RequestGuard;
import com.finpay.gateway.domain.guard.RiskLevel;

import org.junit.jupiter.api.Test;

import java.util.List;

class RequestGuardServiceTest {

    @Test
    void aggregates_guards_into_highest_risk() {
        RequestGuardService service = new RequestGuardService(List.of(
                payload -> new GuardDecision(RiskLevel.MEDIUM, List.of("heuristic")),
                payload -> new GuardDecision(RiskLevel.LOW, List.of("llm")),
                payload -> new GuardDecision(RiskLevel.HIGH, List.of("jailbreak"))
        ));

        GuardDecision decision = service.guard("anything");

        assertThat(decision.risk()).isEqualTo(RiskLevel.HIGH);
        assertThat(decision.reasons()).containsExactlyInAnyOrder("heuristic", "llm", "jailbreak");
        assertThat(decision.isBlocked()).isTrue();
    }

    @Test
    void no_guards_is_pass_through_low() {
        RequestGuardService service = new RequestGuardService(List.of());

        assertThat(service.guard("anything").risk()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void null_payload_is_handled() {
        RequestGuardService service = new RequestGuardService(List.of(payload -> GuardDecision.low()));

        assertThat(service.guard(null).risk()).isEqualTo(RiskLevel.LOW);
    }
}