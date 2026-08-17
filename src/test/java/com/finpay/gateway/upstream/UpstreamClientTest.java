package com.finpay.gateway.upstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.finpay.gateway.config.GatewayProperties.CircuitBreakerPolicy;
import com.finpay.gateway.config.GatewayProperties.Upstream;
import com.sun.net.httpserver.HttpServer;

/**
 * Exercises the Rule 8 resilience envelope (timeout / retry / circuit breaker)
 * against a real local HTTP server.
 */
class UpstreamClientTest {

    private static final CircuitBreakerPolicy CB = new CircuitBreakerPolicy(2, Duration.ofSeconds(30));

    private HttpServer server;
    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private String baseUrl;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int calls = counts.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
            switch (path) {
                case "/flaky" -> {
                    if (calls <= 2) {
                        respond(exchange, 503, "down");
                    } else {
                        respond(exchange, 200, "pong");
                    }
                }
                case "/slow" -> {
                    Thread.sleep(1500);
                    respond(exchange, 200, "late");
                }
                case "/count" -> respond(exchange, 200, Integer.toString(calls));
                default -> respond(exchange, 200, "ok");
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void retries_idempotent_get_until_success() {
        UpstreamClient client = client(new Upstream(Duration.ofMillis(500), Duration.ofMillis(500), 5, Duration.ofMillis(5), CB));

        var response = client.exchange(RequestEntity.get(uri("/flaky")).build());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(response.getBody())).isEqualTo("pong");
        assertThat(counts.get("/flaky").get()).isEqualTo(3);
    }

    @Test
    void does_not_retry_non_idempotent_post() {
        UpstreamClient client = client(new Upstream(Duration.ofMillis(500), Duration.ofMillis(500), 5, Duration.ofMillis(5), CB));

        assertThatThrownBy(() -> client.exchange(
                RequestEntity.post(uri("/count")).body(new byte[]{1})))
                .isInstanceOf(UpstreamUnavailableException.class);

        assertThat(counts.get("/count").get()).isEqualTo(1);
    }

    @Test
    void opens_circuit_and_rejects_fast() {
        UpstreamClient client = client(new Upstream(Duration.ofMillis(500), Duration.ofMillis(500), 1, Duration.ofMillis(5), CB));

        assertThatThrownBy(() -> client.exchange(RequestEntity.get(uri("/always-down")).build()))
                .isInstanceOf(UpstreamUnavailableException.class);
        assertThatThrownBy(() -> client.exchange(RequestEntity.get(uri("/always-down")).build()))
                .isInstanceOf(UpstreamUnavailableException.class);
        assertThatThrownBy(() -> client.exchange(RequestEntity.get(uri("/always-down")).build()))
                .isInstanceOf(CircuitBreaker.CircuitOpenException.class);

        assertThat(counts.get("/always-down").get()).isEqualTo(2);
    }

    @Test
    void read_timeout_throws() {
        UpstreamClient client = client(new Upstream(Duration.ofMillis(500), Duration.ofMillis(200), 1, Duration.ofMillis(5), CB));

        assertThatThrownBy(() -> client.exchange(RequestEntity.get(uri("/slow")).build()))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes();
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private UpstreamClient client(Upstream upstream) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) upstream.connectTimeout().toMillis());
        factory.setReadTimeout((int) upstream.readTimeout().toMillis());
        RestClient restClient = RestClient.builder().requestFactory(factory).build();
        return new UpstreamClient(restClient, upstream);
    }

    private java.net.URI uri(String path) {
        return java.net.URI.create(baseUrl + path);
    }
}