package com.threatdetection.gateway.auth.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmValidationServiceTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void validatesWithRealChatCompletionProbe() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}"));
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"data\":[{\"id\":\"test-model\"}]}"));

        LlmValidationService service = new LlmValidationService(WebClient.builder());
        Map<String, Object> result = service.validate(
                "sk-test", server.url("/").toString(), "test-model").block();

        assertTrue(Boolean.TRUE.equals(result.get("ok")));
        assertEquals("test-model", result.get("tested_model"));
        assertEquals("/chat/completions", server.takeRequest().getPath());
        assertEquals("Bearer sk-test", server.takeRequest().getHeader("Authorization"));
    }
}
