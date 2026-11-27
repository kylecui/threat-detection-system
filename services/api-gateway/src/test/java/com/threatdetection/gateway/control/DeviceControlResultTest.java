package com.threatdetection.gateway.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.r2dbc.postgresql.codec.Json;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DeviceControlResultTest {
    private final DeviceControlService service = new DeviceControlService(null, new ObjectMapper(), null, null);
    private static final String BODY = "{\"phase\":\"completed\",\"result\":{\"etag\":\"5\",\"draft\":{\"id\":5,\"yaml_text\":\"中文: true\\n\"}}}";

    private Map<String, Object> row(Object raw, String phase) {
        var row = new LinkedHashMap<String, Object>();
        row.put("request_id", "same-original-request"); row.put("action", "config.read");
        row.put("phase", phase); row.put("result_json_text", raw); row.put("fingerprint", "private");
        return row;
    }

    @Test void projectsTextAndNativeDriverInputsWithoutUsingDebugStrings() {
        for (Object raw : List.of(BODY, Json.of(BODY),
                Json.of(Unpooled.copiedBuffer(BODY, StandardCharsets.UTF_8)))) {
            var view = service.view(row(raw, "completed"));
            assertFalse(view.containsKey("result_decode_error"));
            assertFalse(view.containsKey("result_json_text"));
            assertFalse(view.containsKey("fingerprint"));
            var envelope = (Map<?, ?>) view.get("result");
            var content = (Map<?, ?>) envelope.get("result");
            var draft = (Map<?, ?>) content.get("draft");
            assertEquals("中文: true\n", draft.get("yaml_text"));
        }
    }

    @Test void malformedAndWrongShapeAreExplicitAndDoNotChangeDevicePhase() {
        for (Object raw : List.of("not json", "[]", "42", "{}", new Object())) {
            var view = service.view(row(raw, "device_applied"));
            assertEquals("result_decode_error", view.get("result_decode_error"));
            assertEquals("device_applied", view.get("phase"));
            assertEquals("same-original-request", view.get("request_id"));
            assertNull(view.get("result"));
        }
    }

    @Test void pendingNullAndEmptyAreNotDecodeFailuresAndLaterSameIdRecovers() {
        assertNull(service.view(row(null, "queued")).get("result"));
        assertFalse(service.view(row("{}", "accepted")).containsKey("result_decode_error"));
        assertEquals("result_decode_error", service.view(row(null, "completed")).get("result_decode_error"));
        assertNotNull(service.view(row(BODY, "completed")).get("result"));
    }
    @Test void storedVersionFencePreventsOlderSnapshotsFromReplacingNewerResults() {
        assertTrue(DeviceControlService.PERSIST_RESULT_SQL.contains("AND (result_version<:version OR (result_version=0 AND :version=0"));
        assertTrue(DeviceControlService.PERSIST_RESULT_SQL.contains("result_version=:version"));
        assertTrue(DeviceControlService.PERSIST_RESULT_SQL.contains("OR phase=:phase"));
        assertEquals(0L, DeviceControlService.resultVersion(Map.of()));
        assertEquals(3L, DeviceControlService.resultVersion(Map.of("result_version", 3)));
        assertEquals(4L, DeviceControlService.resultVersion(Map.of("result_version", 4L)));
        assertThrows(IllegalArgumentException.class, () -> DeviceControlService.resultVersion(Map.of("result_version", -1)));
        assertThrows(IllegalArgumentException.class, () -> DeviceControlService.resultVersion(Map.of("result_version", 1.5)));
        assertThrows(IllegalArgumentException.class, () -> DeviceControlService.resultVersion(Map.of("result_version", "3")));
    }

    @Test void rollbackAndManualReconciliationPreserveBusinessStateAndAdditiveFields() {
        var result = service.view(row("""
                {"phase":"device_pending_confirm","current_effect":"rolled_back","device_pending_confirm":true,
                 "control_reconciliation_required":true,"result_version":6,"result":{}}
                """, "device_pending_confirm"));
        assertFalse(result.containsKey("result_decode_error"));
        assertEquals("device_pending_confirm", result.get("phase"));
        assertEquals("rolled_back", ((Map<?, ?>) result.get("result")).get("current_effect"));
        assertTrue(DeviceControlService.CLAIM_RECONCILE_SQL.contains("'device_pending_confirm'"));
        assertFalse(DeviceControlService.CLAIM_RECONCILE_SQL.contains("'control_reconciliation_required'"));
    }

}
