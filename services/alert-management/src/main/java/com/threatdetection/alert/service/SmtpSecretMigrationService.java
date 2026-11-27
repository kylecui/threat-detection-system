package com.threatdetection.alert.service;

import com.threatdetection.alert.repository.SmtpConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Migrates legacy plaintext SMTP passwords after an in-place upgrade. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpSecretMigrationService {

    private final SmtpConfigRepository smtpConfigRepository;
    private final SecretEncryptionService encryptionService;

    @EventListener(ApplicationReadyEvent.class)
    public void migrateAfterStartup() {
        int migrated = 0;
        try {
            for (var config : smtpConfigRepository.findAll()) {
                String password = config.getPassword();
                if (password != null && !password.isBlank()
                        && !encryptionService.isEncrypted(password)) {
                    config.setPassword(encryptionService.encrypt(password));
                    smtpConfigRepository.save(config);
                    migrated++;
                }
            }
            log.info("SMTP secret migration completed: {} value(s) encrypted", migrated);
        } catch (Exception exception) {
            log.error("SMTP secret migration failed; legacy values remain readable", exception);
        }
    }
}
