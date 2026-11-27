package com.threatdetection.gateway.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ControlEventTest {
    private static final String SECRET = "test-only-hmac-material-test-only";
    private final Map<String, Object> fixture = new HashMap<>(Map.of(
            "schema_version", 1, "event_id", "event-44", "event_type", "config.reported",
            "controller_id", "controller-a", "platform_id", "platform-a", "device_id", "device-internal-id",
            "dev_serial", "serial-a", "occurred_at", "2026-11-26T10:00:00Z", "data", Map.of("revision", 12)));

    @Test void signedEventsMustMatchBothIdentitiesAndHaveValidShape() throws Exception {
        var store = mock(ControlEventStore.class);
        var service = new DeviceControlService(null, new ObjectMapper(), null, store);
        ReflectionTestUtils.setField(service, "secret", SECRET);
        ReflectionTestUtils.setField(service, "controllerId", "controller-a");
        ReflectionTestUtils.setField(service, "platformId", "platform-a");
        when(store.append(anyMap())).thenReturn(Mono.empty());
        service.receiveEvent(ControlEnvelope.encode(fixture, "wrong-secret")).block();
        for (var invalid : Map.of("controller_id", "foreign", "platform_id", "other", "schema_version", 2,
                "event_type", "unsupported", "event_id", "invalid\n", "occurred_at", "no-timezone").entrySet()) {
            var changed = new HashMap<>(fixture);
            changed.put(invalid.getKey(), invalid.getValue());
            service.receiveEvent(ControlEnvelope.encode(changed, SECRET)).block();
        }
        verifyNoInteractions(store);
        service.receiveEvent(ControlEnvelope.encode(fixture, SECRET)).block();
        verify(store).append(fixture);
    }

    @Test void retainedAndWrongQosAreDiscardedBeforeDecode() {
        var message = mock(Mqtt3Publish.class);
        when(message.getQos()).thenReturn(MqttQos.AT_LEAST_ONCE);
        when(message.isRetain()).thenReturn(true);
        HiveMqControlTransport.deliver(message, bytes -> Mono.error(new AssertionError("retained")));
        verify(message).acknowledge();
        verify(message, never()).getPayloadAsBytes();
        when(message.getQos()).thenReturn(MqttQos.AT_MOST_ONCE);
        HiveMqControlTransport.deliver(message, bytes -> Mono.error(new AssertionError("QoS 0")));
        verify(message, times(1)).acknowledge();
        when(message.getQos()).thenReturn(MqttQos.AT_LEAST_ONCE);
        when(message.isRetain()).thenReturn(false);
        when(message.getPayloadAsBytes()).thenReturn(new byte[]{1});
        assertThrows(IllegalStateException.class, () -> HiveMqControlTransport.deliver(message,
                bytes -> Mono.error(new IllegalStateException("DB unavailable"))));
        verify(message, times(1)).acknowledge();
        HiveMqControlTransport.deliver(message, bytes -> Mono.empty());
        verify(message, times(2)).acknowledge();
    }

    @Test
    @SuppressWarnings("unchecked")
    void duplicateEventProducesNoNotificationAndOtherCustomersSeeNothing() {
        var db = mock(DatabaseClient.class);
        var spec = mock(DatabaseClient.GenericExecuteSpec.class);
        var stored = new AtomicBoolean();
        var row = mock(Row.class);
        when(row.get("customer_id", String.class)).thenReturn("customer-a");
        when(row.get("dev_serial", String.class)).thenReturn("SERIAL-A");
        when(row.get("sequence_id", Long.class)).thenReturn(9L);
        when(db.sql(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            assertTrue(sql.contains("ON CONFLICT (event_id) DO NOTHING"));
            assertTrue(sql.contains("UPPER(dev_serial)=UPPER(:serial)"));
            assertTrue(sql.contains("bind_time<=:occurred"));
            return spec;
        });
        when(spec.bind(anyString(), any())).thenReturn(spec);
        when(spec.map(any(BiFunction.class))).thenAnswer(call -> {
            BiFunction<Row, RowMetadata, ?> mapper = call.getArgument(0);
            RowsFetchSpec<Object> rows = mock(RowsFetchSpec.class);
            when(rows.one()).thenReturn(Mono.defer(() ->
                    stored.getAndSet(true) ? Mono.empty() : Mono.just(mapper.apply(row, null))));
            return rows;
        });
        var store = new ControlEventStore(db, new ObjectMapper());
        var other = store.notifications("customer-b", "serial-a").subscribe(cursor -> fail("cross-customer signal"));
        StepVerifier.create(store.notifications("customer-a", "serial-a"))
                .then(() -> {
                    store.append(fixture).block();
                    store.append(fixture).block();
                }).expectNext("9").expectNoEvent(java.time.Duration.ofMillis(20)).thenCancel().verify();
        other.dispose();
        verify(spec, times(2)).bind("id", "event-44");
    }
}
