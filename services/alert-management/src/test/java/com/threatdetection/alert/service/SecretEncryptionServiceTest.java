package com.threatdetection.alert.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretEncryptionServiceTest {

    private static final String KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    @Test
    void encryptsWithRandomIvAndDecryptsPortableFormat() {
        SecretEncryptionService service = new SecretEncryptionService(KEY);

        String first = service.encrypt("smtp-password");
        String second = service.encrypt("smtp-password");

        assertTrue(first.startsWith(SecretEncryptionService.PREFIX));
        assertNotEquals(first, second);
        assertEquals("smtp-password", service.decrypt(first));
        assertEquals("smtp-password", service.decrypt(second));
    }

    @Test
    void keepsLegacyPlaintextReadableAndRejectsInvalidKeys() {
        SecretEncryptionService service = new SecretEncryptionService(KEY);

        assertEquals("legacy", service.decrypt("legacy"));
        assertThrows(IllegalStateException.class,
                () -> new SecretEncryptionService("not-a-32-byte-key"));
    }
}
