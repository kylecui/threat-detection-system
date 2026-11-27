package com.threatdetection.gateway.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretEncryptionServiceTest {

    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void encryptsWithRandomNonceAndDecrypts() {
        SecretEncryptionService service = new SecretEncryptionService(KEY);

        String first = service.encrypt("sk-sensitive");
        String second = service.encrypt("sk-sensitive");

        assertTrue(first.startsWith(SecretEncryptionService.PREFIX));
        assertNotEquals(first, second);
        assertEquals("sk-sensitive", service.decrypt(first));
        assertEquals("sk-sensitive", service.decrypt(second));
    }

    @Test
    void acceptsLegacyPlaintextDuringRollingMigration() {
        SecretEncryptionService service = new SecretEncryptionService(KEY);
        assertEquals("legacy-secret", service.decrypt("legacy-secret"));
    }
}
