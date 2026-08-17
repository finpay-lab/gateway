package com.finpay.gateway.upstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

    private final CircuitBreaker breaker = new CircuitBreaker(2, Duration.ofSeconds(1));

    @Test
    void allows_calls_when_closed_and_reports_open() {
        assertThat(breaker.isOpen()).isFalse();
        breaker.execute(() -> "ok");
        assertThat(breaker.isOpen()).isFalse();
    }

    @Test
    void opens_after_failure_threshold() {
        breaker.execute(() -> { throw new RuntimeException("boom"); });
        assertThat(breaker.isOpen()).isFalse();

        breaker.execute(() -> { throw new RuntimeException("boom"); });
        assertThat(breaker.isOpen()).isTrue();
    }

    @Test
    void rejects_fast_while_open() {
        breaker.execute(() -> { throw new RuntimeException("boom"); });
        breaker.execute(() -> { throw new RuntimeException("boom"); });
        assertThat(breaker.isOpen()).isTrue();

        assertThatThrownBy(() -> breaker.execute(() -> "rejected"))
                .isInstanceOf(CircuitBreaker.CircuitOpenException.class);
    }

    @Test
    void probes_half_open_and_closes_on_success() throws Exception {
        breaker.execute(() -> { throw new RuntimeException("boom"); });
        breaker.execute(() -> { throw new RuntimeException("boom"); });
        assertThat(breaker.isOpen()).isTrue();

        Thread.sleep(1100);
        assertThat(breaker.execute(() -> "recovered")).isEqualTo("recovered");
        assertThat(breaker.isOpen()).isFalse();
    }

    @Test
    void reopens_when_probe_fails() throws Exception {
        breaker.execute(() -> { throw new RuntimeException("boom"); });
        breaker.execute(() -> { throw new RuntimeException("boom"); });
        assertThat(breaker.isOpen()).isTrue();

        Thread.sleep(1100);
        assertThatThrownBy(() -> breaker.execute(() -> { throw new RuntimeException("still down"); }))
                .isInstanceOf(RuntimeException.class);
        assertThat(breaker.isOpen()).isTrue();
    }

    @Test
    void single_failure_does_not_open() {
        AtomicInteger calls = new AtomicInteger();
        breaker.execute(() -> { throw new RuntimeException("boom"); });
        assertThat(breaker.isOpen()).isFalse();
        breaker.execute(calls::incrementAndGet);
        assertThat(calls.get()).isEqualTo(1);
    }
}