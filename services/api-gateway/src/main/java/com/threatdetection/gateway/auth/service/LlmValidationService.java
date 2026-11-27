package com.threatdetection.gateway.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Performs an end-to-end OpenAI-compatible chat-completions probe. */
@Service
public class LlmValidationService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() { };

    private final WebClient.Builder webClientBuilder;

    public LlmValidationService(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> validate(String apiKey, String baseUrl, String model) {
        String effectiveBase = normalizeBaseUrl(baseUrl);
        WebClient client = webClientBuilder.baseUrl(effectiveBase).build();
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", "Reply with OK.")),
                "temperature", 0,
                "max_tokens", 8);

        Mono<Map<String, Object>> chatProbe = client.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .timeout(Duration.ofSeconds(30))
                .flatMap(data -> {
                    Object choices = data.get("choices");
                    if (!(choices instanceof List) || ((List<?>) choices).isEmpty()) {
                        return Mono.error(new IllegalStateException(
                                "Provider returned no chat completion choices"));
                    }
                    return Mono.just(data);
                });

        Mono<List<String>> models = client.get()
                .uri("/models")
                .header("Authorization", "Bearer " + apiKey)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .timeout(Duration.ofSeconds(15))
                .map(data -> {
                    Object rawData = data.get("data");
                    if (!(rawData instanceof List)) return List.<String>of(model);
                    List<String> result = ((List<Map<String, Object>>) rawData).stream()
                            .map(item -> (String) item.get("id"))
                            .filter(id -> id != null && !id.isBlank())
                            .sorted()
                            .collect(Collectors.toList());
                    return result.isEmpty() ? List.of(model) : result;
                })
                // Listing models is informational. A successful generation probe is authoritative.
                .onErrorReturn(List.of(model));

        return chatProbe.then(models).map(availableModels -> {
            Map<String, Object> result = new HashMap<>();
            result.put("ok", true);
            result.put("models", availableModels);
            result.put("tested_model", model);
            result.put("error", null);
            return result;
        });
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) {
            throw new IllegalArgumentException("base_url must use http or https");
        }
        return normalized;
    }
}
