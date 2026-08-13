package com.finpay.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full reactive context (no external IdP/Redis needed: JWKS is only
 * fetched on first decode, Redis only connects on first rate-limit request) and
 * proves the gateway routes are registered and security is enforced.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayApplicationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void context_loads_and_service_routes_are_registered() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotEmpty();
        assertThat(routes).extracting(Route::getId).contains(
                "identity", "customer", "account", "ledger", "payment", "transfer", "audit");
    }

    @Test
    void health_endpoint_is_public() {
        webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void protected_route_returns_401_without_token() {
        webTestClient.get().uri("/accounts/1")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }
}