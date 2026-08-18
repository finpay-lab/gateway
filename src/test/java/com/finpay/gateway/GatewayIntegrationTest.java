package com.finpay.gateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.finpay.gateway.security.TestJwts;
import com.sun.net.httpserver.HttpServer;

/**
 * End-to-end gateway behaviour: JWT enforcement, coarse role gating, routing to
 * a real local upstream and propagation of the verified principal + correlation
 * id (SECURITY.md, runtime-architecture.md).
 */
@SpringBootTest
class GatewayIntegrationTest {

    private static HttpServer upstream;
    private static String upstreamUrl;
    private static volatile ReceivedRequest lastRequest;

    @DynamicPropertySource
    static void pointCustomerRouteAtTestUpstream(DynamicPropertyRegistry registry) {
        // SB4 does not merge indexed-list dynamic properties (gateway.routes[0].uri
        // nulls out the rest of the record). Point the in-test upstream at the
        // route via a non-indexed placeholder instead.
        registry.add("gateway.test.upstream-url", () -> upstreamUrl);
    }

    @BeforeAll
    static void startUpstream() throws Exception {
        upstream = HttpServer.create(new InetSocketAddress(0), 0);
        upstreamUrl = "http://127.0.0.1:" + upstream.getAddress().getPort();
        upstream.createContext("/", exchange -> {
            lastRequest = new ReceivedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("X-FinPay-Subject"),
                    exchange.getRequestHeaders().getFirst("X-FinPay-Roles"),
                    exchange.getRequestHeaders().getFirst("X-Correlation-Id"),
                    exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"from\":\"upstream\"}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();
    }

    @AfterAll
    static void stopUpstream() {
        upstream.stop(0);
    }

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private List<org.springframework.web.filter.OncePerRequestFilter> filters;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setupMockMvc() {
        // Replicates what @AutoConfigureMockMvc did in SB3: register the gateway's
        // @Component OncePerRequestFilter chain (auth, RBAC, CORS, rate-limit,
        // forwarding) into MockMvc, honouring their @Order so requests are routed.
        List<org.springframework.web.filter.OncePerRequestFilter> ordered = new java.util.ArrayList<>(filters);
        org.springframework.core.annotation.AnnotationAwareOrderComparator.sort(ordered);
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(ordered.toArray(new org.springframework.web.filter.OncePerRequestFilter[0]))
                .build();
    }

    @Test
    void forwards_request_and_propagates_verified_principal_and_correlation() throws Exception {
        String token = TestJwts.mint("alice", List.of("CUSTOMER"));

        mockMvc.perform(get("/customer/ping").queryParam("x", "1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"from\":\"upstream\"}"));

        assertThatLastRequest()
                .hasMethod("GET")
                .hasPath("/ping")
                .hasSubject("alice")
                .hasRoles("CUSTOMER")
                .hasCorrelationId()
                .hasNoAuthorizationHeader();
    }

    @Test
    void rejects_request_without_token() throws Exception {
        mockMvc.perform(get("/customer/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejects_token_signed_with_unknown_key() throws Exception {
        String badSecret = java.util.Base64.getEncoder()
                .encodeToString("a-different-unsigned-secret-for-tests-at-least-32b".getBytes());
        String token = TestJwts.mintWithSecret("alice", List.of("CUSTOMER"), badSecret);

        mockMvc.perform(get("/customer/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknown_path_returns_404_problem() throws Exception {
        String token = TestJwts.mint("alice", List.of("CUSTOMER"));

        mockMvc.perform(get("/nope").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().contentType("application/problem+json"));
    }

    @Test
    void insufficient_role_returns_403() throws Exception {
        String token = TestJwts.mint("alice", List.of("CUSTOMER"));

        mockMvc.perform(get("/admin/x").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthz_is_open_without_token() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }

    private static ReceivedRequestAssert assertThatLastRequest() {
        return new ReceivedRequestAssert(lastRequest);
    }

    private record ReceivedRequest(
            String method, String path, String subject, String roles, String correlationId, String authorization) {
    }

    private static final class ReceivedRequestAssert {
        private final ReceivedRequest request;

        private ReceivedRequestAssert(ReceivedRequest request) {
            this.request = request;
        }

        private ReceivedRequestAssert hasMethod(String method) {
            org.assertj.core.api.Assertions.assertThat(request.method()).isEqualTo(method);
            return this;
        }

        private ReceivedRequestAssert hasPath(String path) {
            org.assertj.core.api.Assertions.assertThat(request.path()).isEqualTo(path);
            return this;
        }

        private ReceivedRequestAssert hasSubject(String subject) {
            org.assertj.core.api.Assertions.assertThat(request.subject()).isEqualTo(subject);
            return this;
        }

        private ReceivedRequestAssert hasRoles(String roles) {
            org.assertj.core.api.Assertions.assertThat(request.roles()).isEqualTo(roles);
            return this;
        }

        private ReceivedRequestAssert hasCorrelationId() {
            org.assertj.core.api.Assertions.assertThat(request.correlationId()).isNotBlank();
            return this;
        }

        private ReceivedRequestAssert hasNoAuthorizationHeader() {
            org.assertj.core.api.Assertions.assertThat(request.authorization()).isNull();
            return this;
        }
    }
}