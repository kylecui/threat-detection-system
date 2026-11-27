package com.threatdetection.gateway.auth.controller;

import com.threatdetection.gateway.auth.service.LlmRuntimeConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalLlmRuntimeConfigControllerTest {

    private final LlmRuntimeConfigService service = mock(LlmRuntimeConfigService.class);
    private final InternalLlmRuntimeConfigController controller =
            new InternalLlmRuntimeConfigController(service, "service-token");

    @Test
    void rejectsMissingOrIncorrectServiceToken() {
        StepVerifier.create(controller.effective("wrong-token", "11", "customer-a", "7"))
                .assertNext(response -> assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode()))
                .verifyComplete();

        verify(service, never()).resolve(11L, "customer-a", 7L);
    }

    @Test
    void passesValidatedContextToResolverAndDisablesResponseCaching() {
        Map<String, Object> value = Map.of(
                "configured", true,
                "api_key", "secret",
                "model", "model-a",
                "base_url", "https://llm.example/v1");
        when(service.resolve(11L, "customer-a", 7L)).thenReturn(Mono.just(value));

        StepVerifier.create(controller.effective(
                        "service-token", "11", " customer-a ", "7"))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertEquals("no-store", response.getHeaders().getCacheControl());
                    assertEquals(value, response.getBody());
                })
                .verifyComplete();

        verify(service).resolve(11L, "customer-a", 7L);
    }
}
