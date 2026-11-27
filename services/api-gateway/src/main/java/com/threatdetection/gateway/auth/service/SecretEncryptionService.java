package com.threatdetection.gateway.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts configuration secrets at rest using AES-256-GCM.
 *
 * <p>The portable storage format is
 * {@code enc:v1:<base64url-iv>:<base64url-ciphertext-and-tag>}. A fresh
 * 96-bit IV is generated for every value and the fixed format identifier is
 * authenticated as AAD. Values without the prefix are treated as legacy
 * plaintext so rolling upgrades can migrate them without downtime.</p>
 */
@Service
public class SecretEncryptionService {

    public static final String PREFIX = "enc:v1:";
    private static final byte[] AAD = "threat-detection-config:v1"
            .getBytes(StandardCharsets.UTF_8);
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretEncryptionService(
            @Value("${security.config-encryption-key}") String encodedKey) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getUrlDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "CONFIG_ENCRYPTION_KEY must be URL-safe base64", e);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "CONFIG_ENCRYPTION_KEY must decode to exactly 32 bytes");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty() || isEncrypted(plaintext)) {
            return plaintext;
        }
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return PREFIX + encoder.encodeToString(iv) + ":"
                    + encoder.encodeToString(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to encrypt configuration secret", e);
        }
    }

    /** Decrypts encrypted values and returns legacy plaintext unchanged. */
    public String decrypt(String storedValue) {
        if (storedValue == null || storedValue.isEmpty() || !isEncrypted(storedValue)) {
            return storedValue;
        }
        String[] parts = storedValue.substring(PREFIX.length()).split(":", 2);
        if (parts.length != 2) {
            throw new IllegalStateException("Malformed encrypted configuration secret");
        }
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] iv = decoder.decode(parts[0]);
            if (iv.length != IV_BYTES) {
                throw new IllegalStateException("Malformed encrypted configuration secret IV");
            }
            byte[] ciphertext = decoder.decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Unable to decrypt configuration secret", e);
        }
    }
}
