package com.finpay.gateway.infrastructure.guard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finpay.gateway.application.guard.RequestGuardService;
import com.finpay.gateway.domain.guard.RequestGuard;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Wires the AI guardrail (AI-7). The heuristic guard is always present; the LLM
 * scorer joins only when a BYOK key is configured, keeping the offline baseline
 * intact. The filter itself is a {@code @Component}.
 */
@Configuration
@EnableConfigurationProperties(GuardProperties.class)
public class GuardConfig {

    @Bean
    HeuristicPromptInjectionGuard heuristicPromptInjectionGuard() {
        return new HeuristicPromptInjectionGuard();
    }

    @Bean
    @ConditionalOnProperty(prefix = "gateway.guard.ai", name = "api-key")
    LlmRequestGuard llmRequestGuard(GuardProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) properties.ai().timeout().toMillis();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        return new LlmRequestGuard(
                restClient,
                properties.ai().endpoint(),
                properties.ai().apiKey(),
                properties.ai().model(),
                objectMapper);
    }

    @Bean
    RequestGuardService requestGuardService(List<RequestGuard> guards) {
        return new RequestGuardService(guards);
    }
}
