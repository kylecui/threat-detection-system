package com.threatdetection.gateway.auth.service;

import com.threatdetection.gateway.auth.repository.LlmProviderRepository;
import com.threatdetection.gateway.auth.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Opportunistically migrates legacy plaintext LLM keys after an upgrade. */
@Slf4j
@Service
@ConditionalOnProperty(
        name = "security.secret-migration-enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
public class SecretMigrationService {

    private final SystemConfigRepository systemConfigRepository;
    private final LlmProviderRepository llmProviderRepository;
    private final SecretEncryptionService encryptionService;

    @EventListener(ApplicationReadyEvent.class)
    public void migrateAfterStartup() {
        migrateLegacyLlmSecrets()
                .subscribe(
                        count -> log.info("Configuration secret migration completed: {} value(s) encrypted", count),
                        error -> log.error("Configuration secret migration failed; legacy values remain readable", error));
    }

    public Mono<Long> migrateLegacyLlmSecrets() {
        Flux<?> globalKeyMigration = systemConfigRepository
                .findByConfigKey("LLM_API_KEY")
                .filter(config -> hasLegacyPlaintext(config.getConfigValue()))
                .flatMap(config -> {
                    config.setConfigValue(encryptionService.encrypt(config.getConfigValue()));
                    return systemConfigRepository.save(config);
                })
                .flux();

        Flux<?> providerMigration = llmProviderRepository.findAll()
                .filter(provider -> hasLegacyPlaintext(provider.getApiKey()))
                .flatMap(provider -> {
                    provider.setApiKey(encryptionService.encrypt(provider.getApiKey()));
                    return llmProviderRepository.save(provider);
                });

        return Flux.concat(globalKeyMigration, providerMigration).count();
    }

    private boolean hasLegacyPlaintext(String value) {
        return value != null && !value.isBlank() && !encryptionService.isEncrypted(value);
    }
}
