package com.threatdetection.gateway.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ControlEnvelopeTest {
    private static final String KEY = "test-only-platform-signing-secret-32-bytes";

    @Test void preservesUtf8AndRejectsTampering() throws Exception {
        Map<String, Object> value = Map.of("schema_version", 1, "device_id", "序列号/大小写");
        byte[] wire = ControlEnvelope.encode(value, KEY);
        assertEquals(value, ControlEnvelope.decode(wire, KEY));
        assertThrows(SecurityException.class, () -> ControlEnvelope.decode(wire, "different-key"));
        Map<String, String> envelope = new ObjectMapper().readValue(wire, Map.class);
        envelope.put("signature", "0".repeat(64));
        assertThrows(SecurityException.class, () -> ControlEnvelope.decode(new ObjectMapper().writeValueAsBytes(envelope), KEY));
    }

    @Test void rejectsOversizedResultBeforeParsing() {
        assertThrows(SecurityException.class, () -> ControlEnvelope.decode(new byte[3 * 1024 * 1024 + 1], KEY));
    }

    @Test void readsExactPythonUtf8ProtocolVector() throws Exception {
        String wire = "{\"payload\":\"eyJzY2hlbWFfdmVyc2lvbiI6MSwicmVxdWVzdF9pZCI6IjAwMDAwMDAwLTAwMDAtMDAwMC0wMDAwLTAwMDAwMDAwMDAwOSIsImRldmljZV9pZCI6IuW6j-WIl-WPty_lpKflsI_lhpkifQ\",\"signature\":\"834539c5da1d843133bc65d163a65920c352f7375c1e61d9261addd9ce30576c\"}";
        Map<String, Object> value = ControlEnvelope.decode(wire.getBytes(java.nio.charset.StandardCharsets.UTF_8), KEY);
        assertEquals("序列号/大小写", value.get("device_id"));
        assertEquals("00000000-0000-0000-0000-000000000009", value.get("request_id"));
    }
}
