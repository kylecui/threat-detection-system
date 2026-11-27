package com.threatdetection.gateway.auth.controller;

import com.threatdetection.gateway.auth.entity.SystemConfig;
import com.threatdetection.gateway.auth.repository.SystemConfigRepository;
import com.threatdetection.gateway.auth.service.JwtTokenProvider;
import com.threatdetection.gateway.auth.service.LlmValidationService;
import com.threatdetection.gateway.auth.service.SecretEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemConfigControllerTest {

    private final SystemConfigRepository repository = mock(SystemConfigRepository.class);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final SecretEncryptionService encryptionService = mock(SecretEncryptionService.class);
    private final LlmValidationService validationService = mock(LlmValidationService.class);
    private final SystemConfigController controller = new SystemConfigController(
            repository, jwtTokenProvider, encryptionService, validationService);

    @BeforeEach
    void authorizeSuperAdmin() {
        when(jwtTokenProvider.validateToken("token")).thenReturn(true);
        when(jwtTokenProvider.getRolesFromToken("token")).thenReturn(List.of("SUPER_ADMIN"));
    }

    @Test
    void validatesWithSavedSecretWhenRequestLeavesApiKeyBlank() {
        when(repository.findByConfigKey(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return switch (key) {
                case "LLM_API_KEY" -> Mono.just(config(key, "encrypted-key", true));
                case "LLM_BASE_URL" -> Mono.just(config(key, "https://llm.example/v1", false));
                case "LLM_MODEL" -> Mono.just(config(key, "model-a", false));
                default -> Mono.empty();
            };
        });
        when(encryptionService.decrypt("encrypted-key")).thenReturn("saved-key");
        Map<String, Object> result = Map.of("ok", true, "models", List.of("model-a"));
        when(validationService.validate("saved-key", "https://llm.example/v1", "model-a"))
                .thenReturn(Mono.just(result));

        StepVerifier.create(controller.validateLlmConnection(
                        Map.of("api_key", ""), authorizedExchange()))
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    assertEquals(result, response.getBody());
                })
                .verifyComplete();

        verify(validationService).validate("saved-key", "https://llm.example/v1", "model-a");
    }

    @Test
    void categoryReadsUseTheStableRepositoryOrder() {
        when(repository.findByCategoryOrdered("llm")).thenReturn(Flux.just(
                SystemConfig.builder().id(1L).configKey("LLM_API_KEY").configValue("secret").isSecret(true).build(),
                SystemConfig.builder().id(2L).configKey("LLM_MODEL").configValue("model-a").isSecret(false).build()));

        StepVerifier.create(controller.getByCategory("llm", authorizedExchange()))
                .assertNext(response -> {
                    assertEquals(200, response.getStatusCode().value());
                    assertEquals("LLM_API_KEY", response.getBody().get(0).get("key"));
                    assertEquals("LLM_MODEL", response.getBody().get(1).get("key"));
                })
                .verifyComplete();

        verify(repository).findByCategoryOrdered("llm");
        verify(repository, never()).findByCategory("llm");
    }

    private MockServerWebExchange authorizedExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .header("Authorization", "Bearer token"));
    }

    private SystemConfig config(String key, String value, boolean secret) {
        return SystemConfig.builder()
                .configKey(key)
                .configValue(value)
                .isSecret(secret)
                .build();
    }
}
