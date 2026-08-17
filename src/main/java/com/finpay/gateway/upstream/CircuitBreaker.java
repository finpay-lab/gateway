package com.finpay.gateway.upstream;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Minimal circuit breaker for upstream calls (Rule 8). State machine with only
 * legal transitions (Rule 9): CLOSED -> OPEN (consecutive failures reach the
 * threshold) -> HALF_OPEN (a probe after {@code openDuration}) -> CLOSED on
 * probe success or OPEN again on probe failure. When OPEN the call is rejected
 * fast without touching the failing service.
 */
public final class CircuitBreaker {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final Duration openDuration;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long openedAtMillis;

    public CircuitBreaker(int failureThreshold, Duration openDuration) {
        if (failureThreshold <= 0 || openDuration == null || openDuration.isZero() || openDuration.isNegative()) {
            throw new IllegalArgumentException("Invalid circuit-breaker config");
        }
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
    }

    public boolean isOpen() {
        return state.get() == State.OPEN;
    }

    /**
     * Executes {@code call} through the breaker. Throws
     * {@link CircuitOpenException} when the circuit is open.
     */
    public <T> T execute(Supplier<T> call) {
        if (!allowRequest()) {
            throw new CircuitOpenException();
        }
        try {
            T result = call.get();
            onSuccess();
            return result;
        } catch (RuntimeException e) {
            onFailure();
            throw e;
        }
    }

    private boolean allowRequest() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        }
        if (current == State.OPEN && now() - openedAtMillis >= openDuration.toMillis()) {
            // Legal: OPEN -> HALF_OPEN (single probe).
            state.compareAndSet(State.OPEN, State.HALF_OPEN);
            return true;
        }
        return current == State.HALF_OPEN;
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        // Legal: HALF_OPEN -> CLOSED (and no-op when already CLOSED).
        state.compareAndSet(State.HALF_OPEN, State.CLOSED);
    }

    private void onFailure() {
        if (state.get() == State.HALF_OPEN) {
            // Legal: HALF_OPEN -> OPEN.
            state.set(State.OPEN);
            openedAtMillis = now();
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            // Legal: CLOSED -> OPEN.
            state.set(State.OPEN);
            openedAtMillis = now();
        }
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /** Thrown when the circuit is open; mapped to 503 by the gateway. */
    public static final class CircuitOpenException extends RuntimeException {
        public CircuitOpenException() {
            super("Circuit breaker open");
        }
    }
}