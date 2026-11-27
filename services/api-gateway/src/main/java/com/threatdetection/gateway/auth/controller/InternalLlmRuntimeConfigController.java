package com.threatdetection.gateway.auth.controller;

import com.threatdetection.gateway.auth.service.LlmRuntimeConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/** Service-to-service endpoint used by TIRE to resolve effective LLM access. */
@RestController
@RequestMapping("/internal/v1/runtime-config/llm")
public class InternalLlmRuntimeConfigController {

    private final LlmRuntimeConfigService runtimeConfigService;
    private final byte[] expectedServiceToken;

    public InternalLlmRuntimeConfigController(
            LlmRuntimeConfigService runtimeConfigService,
            @Value("${internal.tire-service-token}") String serviceToken) {
        this.runtimeConfigService = runtimeConfigService;
        this.expectedServiceToken = serviceToken.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/effective")
    public Mono<ResponseEntity<Map<String, Object>>> effective(
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String token,
            @RequestHeader(value = "X-Context-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Context-Customer-Id", required = false) String customerId,
            @RequestHeader(value = "X-Context-Tenant-Id", required = false) String tenantId) {
        if (!validServiceToken(token)) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized service identity")));
        }
        Long parsedUserId = parsePositiveLong(userId);
        Long parsedTenantId = parsePositiveLong(tenantId);
        String normalizedCustomerId = customerId == null || customerId.isBlank()
                ? null : customerId.trim();
        return runtimeConfigService
                .resolve(parsedUserId, normalizedCustomerId, parsedTenantId)
                .map(config -> ResponseEntity.ok()
                        .header("Cache-Control", "no-store")
                        .header("Pragma", "no-cache")
                        .body(config));
    }

    private boolean validServiceToken(String token) {
        if (token == null || token.isBlank()) return false;
        return MessageDigest.isEqual(
                expectedServiceToken, token.getBytes(StandardCharsets.UTF_8));
    }

    private Long parsePositiveLong(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            long value = Long.parseLong(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
