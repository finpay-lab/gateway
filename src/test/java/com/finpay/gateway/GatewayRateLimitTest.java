package com.finpay.gateway;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import com.finpay.gateway.ratelimit.RateLimitService;
import com.finpay.gateway.security.TestJwts;

/**
 * Rate limiting at the gateway edge (SECURITY.md): a limited request is rejected
 * with 429 + Retry-After before it ever reaches the downstream service.
 */
@SpringBootTest
class GatewayRateLimitTest {

    @MockitoBean
    private RateLimitService rateLimitService;

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    @BeforeEach
    void denyEverything() {
        when(rateLimitService.tryAcquire(anyString(), any(), anyLong()))
                .thenReturn(new RateLimitService.Result(false, 2));
    }

    @Test
    void limited_request_returns_429_problem_with_retry_after() throws Exception {
        String token = TestJwts.mint("alice", List.of("CUSTOMER"));

        mockMvc.perform(get("/customer/ping").header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(MockMvcResultMatchers.content().contentType("application/problem+json"));
    }
}