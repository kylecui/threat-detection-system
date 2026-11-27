package com.threatdetection.gateway.control;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/** Wire signatures cover the exact base64 payload, independent of JSON key order. */
public final class ControlEnvelope {
    private static final ObjectMapper JSON = new ObjectMapper();
    private ControlEnvelope() { }

    private static byte[] signature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
    }

    public static byte[] encode(Map<String, Object> value, String secret) throws Exception {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(JSON.writeValueAsBytes(value));
        return JSON.writeValueAsBytes(Map.of("payload", payload, "signature", HexFormat.of().formatHex(signature(payload, secret))));
    }

    public static Map<String, Object> decode(byte[] wire, String secret) throws Exception {
        if (wire.length > 3 * 1024 * 1024) throw new SecurityException("Control result too large");
        Map<String, String> envelope = JSON.readValue(wire, new TypeReference<>() { });
        if (!envelope.keySet().equals(java.util.Set.of("payload", "signature"))) throw new SecurityException("Invalid envelope");
        String payload = envelope.get("payload");
        if (!MessageDigest.isEqual(signature(payload, secret), HexFormat.of().parseHex(envelope.get("signature")))) {
            throw new SecurityException("Invalid control signature");
        }
        return JSON.readValue(Base64.getUrlDecoder().decode(payload), new TypeReference<>() { });
    }
}
