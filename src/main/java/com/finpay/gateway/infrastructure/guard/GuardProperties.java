package com.finpay.gateway.infrastructure.guard;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Gateway AI guardrail configuration (AI-7). Bound from {@code gateway.guard.*}.
 *
 * @param enabled   master switch; when false the filter passes every request through.
 * @param block     when true, {@link com.finpay.gateway.domain.guard.RiskLevel#HIGH}
 *                  requests are rejected with 403 instead of only being flagged.
 * @param ai        BYOK LLM scorer settings (ignored until {@code api-key} is set).
 */
@ConfigurationProperties(prefix = "gateway.guard")
public record GuardProperties(boolean enabled, boolean block, Ai ai) {

    public GuardProperties {
        if (ai == null) {
            ai = new Ai(null, null, null, null);
        }
    }

    /**
     * @param apiKey   BYOK key resolved from the secret store (env var in production).
     * @param endpoint OpenAI-compatible chat completions endpoint.
     * @param model    chat model to use for classification.
     * @param timeout  connect + read timeout for the LLM call (fail-open on expiry).
     */
    public record Ai(String apiKey, String endpoint, String model, Duration timeout) {

        private static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
        private static final String DEFAULT_MODEL = "gpt-4o-mini";
        private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

        public Ai {
            if (endpoint == null || endpoint.isBlank()) {
                endpoint = DEFAULT_ENDPOINT;
            }
            if (model == null || model.isBlank()) {
                model = DEFAULT_MODEL;
            }
            if (timeout == null) {
                timeout = DEFAULT_TIMEOUT;
            }
        }

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
