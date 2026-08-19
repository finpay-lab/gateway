package com.finpay.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.finpay.gateway.application.guard.RequestGuardService;
import com.finpay.gateway.infrastructure.guard.HeuristicPromptInjectionGuard;
import com.finpay.gateway.infrastructure.guard.LlmRequestGuard;
import com.finpay.gateway.interfaces.web.filter.RequestGuardFilter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Proves the AI guardrail (AI-7) wires up in a real Spring context: the filter,
 * the service and the heuristic guard are present, and the LLM scorer stays out
 * until a BYOK key is configured.
 */
@SpringBootTest(properties = "gateway.guard.block=false")
class GuardContextTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void guard_beams_are_wired() {
        assertThat(context.getBean(RequestGuardService.class)).isNotNull();
        assertThat(context.getBean(RequestGuardFilter.class)).isNotNull();
        assertThat(context.getBean(HeuristicPromptInjectionGuard.class)).isNotNull();
    }

    @Test
    void llm_guard_is_absent_without_api_key() {
        assertThat(context.getBeansOfType(LlmRequestGuard.class)).isEmpty();
    }
}
