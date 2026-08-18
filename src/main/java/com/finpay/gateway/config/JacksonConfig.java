package com.finpay.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit Jackson 2.x {@link ObjectMapper} bean.
 *
 * <p>Spring Boot 4 defaults its JSON stack to Jackson 3.x ({@code tools.jackson}),
 * so the auto-configured {@code ObjectMapper} is no longer the Jackson 2.x type
 * the gateway's filters inject. This bean keeps the gateway on Jackson 2.x
 * (the only variant its {@code com.fasterxml.jackson.databind} imports compile
 * against) until the filter layer is migrated to Jackson 3.x.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
