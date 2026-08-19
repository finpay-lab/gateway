package com.finpay.gateway.application.guard;

import com.finpay.gateway.domain.guard.GuardDecision;
import com.finpay.gateway.domain.guard.RequestGuard;

import java.util.List;

/**
 * Use case that scores an inbound request payload against every registered
 * {@link RequestGuard} and combines the results (AI-7). Keeps the servlet
 * filter free of guard-selection logic.
 */
public final class RequestGuardService {

    private final List<RequestGuard> guards;

    public RequestGuardService(List<RequestGuard> guards) {
        this.guards = guards == null ? List.of() : List.copyOf(guards);
    }

    /**
     * Evaluates the payload with all guards and returns the most severe risk.
     * With no guards registered this is a pass-through ({@link GuardDecision#low()}).
     */
    public GuardDecision guard(String payload) {
        GuardDecision result = GuardDecision.low();
        for (RequestGuard guard : guards) {
            result = result.combine(guard.evaluate(payload));
        }
        return result;
    }
}
