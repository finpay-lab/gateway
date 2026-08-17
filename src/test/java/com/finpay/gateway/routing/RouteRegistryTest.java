package com.finpay.gateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.finpay.gateway.config.GatewayProperties.Route;

class RouteRegistryTest {

    private final RouteRegistry registry = new RouteRegistry(List.of(
            new Route("customer", "/customer", "http://customer", List.of("CUSTOMER")),
            new Route("payment", "/payment", "http://payment", List.of("CUSTOMER")),
            new Route("account", "/account", "http://account", List.of("OPERATOR"))));

    @Test
    void resolves_exact_path() {
        assertThat(registry.resolve("/customer").orElseThrow().id()).isEqualTo("customer");
        assertThat(registry.resolve("/customer/").orElseThrow().id()).isEqualTo("customer");
    }

    @Test
    void resolves_sub_path() {
        assertThat(registry.resolve("/payment/transfers/123").orElseThrow().id()).isEqualTo("payment");
    }

    @Test
    void prefix_does_not_leak_into_similar_route() {
        assertThat(registry.resolve("/payments").isEmpty()).isTrue();
    }

    @Test
    void returns_empty_when_no_match() {
        assertThat(registry.resolve("/unknown").isEmpty()).isTrue();
    }

    @Test
    void strips_prefix_preserving_suffix_and_slash() {
        Route route = registry.resolve("/customer/123/profile").orElseThrow();
        assertThat(RouteRegistry.forwardPath(route, "/customer/123/profile")).isEqualTo("/123/profile");
        assertThat(RouteRegistry.forwardPath(route, "/customer")).isEqualTo("/");
    }
}