package com.threatdetection.gateway.auth.service;

import com.threatdetection.gateway.auth.entity.ConfigAssignment;
import com.threatdetection.gateway.auth.entity.LlmProvider;
import com.threatdetection.gateway.auth.entity.SystemConfig;
import com.threatdetection.gateway.auth.entity.UserConfig;
import com.threatdetection.gateway.auth.repository.ConfigAssignmentRepository;
import com.threatdetection.gateway.auth.repository.LlmProviderRepository;
import com.threatdetection.gateway.auth.repository.SystemConfigRepository;
import com.threatdetection.gateway.auth.repository.UserConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/** Resolves the effective platform LLM config for the trusted TIRE service. */
@Service
@RequiredArgsConstructor
public class LlmRuntimeConfigService {

    private static final String DEFAULT_MODEL = "gpt-4o";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    private final UserConfigRepository userConfigRepository;
    private final ConfigAssignmentRepository assignmentRepository;
    private final LlmProviderRepository providerRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final SecretEncryptionService encryptionService;

    public Mono<Map<String, Object>> resolve(Long userId, String customerId, Long tenantId) {
        Mono<UserConfig> userConfig = userId == null
                ? Mono.just(UserConfig.builder().build())
                : userConfigRepository.findByUserId(userId)
                        .defaultIfEmpty(UserConfig.builder().userId(userId).build());
        Mono<ConfigAssignment> assignment = customerId == null || customerId.isBlank()
                ? Mono.just(ConfigAssignment.builder().build())
                : assignmentRepository.findByCustomerId(customerId)
                        .defaultIfEmpty(ConfigAssignment.builder().customerId(customerId).build());

        return Mono.zip(userConfig, assignment)
                .flatMap(tuple -> resolveScoped(
                        tuple.getT1(), tuple.getT2(), userId, customerId, tenantId));
    }

    private Mono<Map<String, Object>> resolveScoped(
            UserConfig userConfig,
            ConfigAssignment assignment,
            Long userId,
            String customerId,
            Long tenantId) {
        boolean locked = Boolean.TRUE.equals(assignment.getLockLlm());
        boolean useOwn = Boolean.TRUE.equals(userConfig.getUseOwnLlm());

        Long providerId;
        String source;
        if (locked) {
            providerId = assignment.getLlmProviderId();
            source = "admin_locked";
        } else if (useOwn && userConfig.getLlmProviderId() != null) {
            providerId = userConfig.getLlmProviderId();
            source = "user_own";
        } else if (assignment.getLlmProviderId() != null) {
            providerId = assignment.getLlmProviderId();
            source = "admin_assigned";
        } else {
            providerId = null;
            source = "system_default";
        }

        if (providerId != null) {
            return providerRepository.findById(providerId)
                    .filter(provider -> Boolean.TRUE.equals(provider.getEnabled()))
                    .map(provider -> fromProvider(provider, source, userId, customerId, tenantId))
                    .defaultIfEmpty(unavailable(source + "_unavailable", userId, customerId, tenantId));
        }
        if (locked) {
            // An explicit lock with no assignment intentionally disables LLM use.
            return Mono.just(unavailable(source, userId, customerId, tenantId));
        }
        return resolveGlobal(userId, customerId, tenantId);
    }

    private Mono<Map<String, Object>> resolveGlobal(
            Long userId, String customerId, Long tenantId) {
        Mono<SystemConfig> key = config("LLM_API_KEY");
        Mono<SystemConfig> model = config("LLM_MODEL");
        Mono<SystemConfig> baseUrl = config("LLM_BASE_URL");

        return Mono.zip(key, model, baseUrl)
                .flatMap(tuple -> {
                    SystemConfig keyConfig = tuple.getT1();
                    String apiKey = decrypt(keyConfig.getConfigValue());
                    if (apiKey != null && !apiKey.isBlank()) {
                        return Mono.just(build(
                                apiKey,
                                valueOrDefault(tuple.getT2().getConfigValue(), DEFAULT_MODEL),
                                valueOrDefault(tuple.getT3().getConfigValue(), DEFAULT_BASE_URL),
                                "system_config",
                                null,
                                newest(keyConfig.getUpdatedAt(), tuple.getT2().getUpdatedAt(),
                                        tuple.getT3().getUpdatedAt()),
                                userId,
                                customerId,
                                tenantId));
                    }
                    return providerRepository
                            .findByIsDefaultTrueAndOwnerTypeAndOwnerIdIsNull("SYSTEM")
                            .filter(provider -> Boolean.TRUE.equals(provider.getEnabled()))
                            .map(provider -> fromProvider(
                                    provider, "system_provider", userId, customerId, tenantId))
                            .defaultIfEmpty(unavailable(
                                    "template", userId, customerId, tenantId));
                });
    }

    private Mono<SystemConfig> config(String key) {
        return systemConfigRepository.findByConfigKey(key)
                .defaultIfEmpty(SystemConfig.builder().configKey(key).configValue("").build());
    }

    private Map<String, Object> fromProvider(
            LlmProvider provider,
            String source,
            Long userId,
            String customerId,
            Long tenantId) {
        String apiKey = decrypt(provider.getApiKey());
        if (apiKey == null || apiKey.isBlank()) {
            return unavailable(source + "_unavailable", userId, customerId, tenantId);
        }
        return build(
                apiKey,
                valueOrDefault(provider.getModel(), DEFAULT_MODEL),
                valueOrDefault(provider.getBaseUrl(), DEFAULT_BASE_URL),
                source,
                provider.getId(),
                provider.getUpdatedAt(),
                userId,
                customerId,
                tenantId);
    }

    private Map<String, Object> build(
            String apiKey,
            String model,
            String baseUrl,
            String source,
            Long providerId,
            LocalDateTime updatedAt,
            Long userId,
            String customerId,
            Long tenantId) {
        Map<String, Object> result = new HashMap<>();
        result.put("configured", true);
        result.put("api_key", apiKey);
        result.put("model", model);
        result.put("base_url", stripTrailingSlash(baseUrl));
        result.put("source", source);
        result.put("fingerprint", fingerprint(apiKey, model, baseUrl, source, providerId));
        result.put("version", updatedAt == null ? 0L : updatedAt.toInstant(ZoneOffset.UTC).toEpochMilli());
        if (providerId != null) result.put("provider_id", providerId);
        addContext(result, userId, customerId, tenantId);
        return result;
    }

    private Map<String, Object> unavailable(
            String source, Long userId, String customerId, Long tenantId) {
        Map<String, Object> result = new HashMap<>();
        result.put("configured", false);
        result.put("api_key", "");
        result.put("model", "");
        result.put("base_url", "");
        result.put("source", source);
        result.put("fingerprint", fingerprint("", "", "", source, null));
        result.put("version", 0L);
        addContext(result, userId, customerId, tenantId);
        return result;
    }

    private void addContext(
            Map<String, Object> result, Long userId, String customerId, Long tenantId) {
        if (userId != null) result.put("user_id", userId);
        if (customerId != null && !customerId.isBlank()) result.put("customer_id", customerId);
        if (tenantId != null) result.put("tenant_id", tenantId);
    }

    private String decrypt(String value) {
        return encryptionService.decrypt(value);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String stripTrailingSlash(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private LocalDateTime newest(LocalDateTime... values) {
        LocalDateTime newest = null;
        for (LocalDateTime value : values) {
            if (value != null && (newest == null || value.isAfter(newest))) newest = value;
        }
        return newest;
    }

    private String fingerprint(
            String apiKey, String model, String baseUrl, String source, Long providerId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = source + "\n" + (providerId == null ? "" : providerId) + "\n"
                    + apiKey + "\n" + model + "\n" + baseUrl;
            return HexFormat.of().formatHex(
                    digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
