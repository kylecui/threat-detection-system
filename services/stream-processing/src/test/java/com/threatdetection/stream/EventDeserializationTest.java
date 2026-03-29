package com.threatdetection.stream;

import com.threatdetection.stream.model.AttackEvent;
import org.apache.flink.util.Collector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EventDeserializationTest {

    private static class ListCollector<T> implements Collector<T> {
        private final List<T> values = new ArrayList<>();

        @Override
        public void collect(T record) {
            values.add(record);
        }

        @Override
        public void close() {
        }
    }

    @Test
    void attackEventDeserializer_emitsAttackEvent_forValidJson() {
        StreamProcessingJob.AttackEventDeserializer deserializer = new StreamProcessingJob.AttackEventDeserializer();
        ListCollector<AttackEvent> collector = new ListCollector<>();

        String json = """
            {
              "id":"evt-1",
              "devSerial":"DEV-001",
              "logType":1,
              "subType":2,
              "attackMac":"00:11:22:33:44:55",
              "attackIp":"192.168.1.10",
              "responseIp":"10.0.0.5",
              "responsePort":445,
              "lineId":10,
              "ifaceType":1,
              "vlanId":2,
              "logTime":1710000000,
              "ethType":2048,
              "ipType":4,
              "customerId":"cust-a"
            }
            """;

        deserializer.flatMap(json, collector);

        assertEquals(1, collector.values.size());
        AttackEvent event = collector.values.get(0);
        assertEquals("evt-1", event.getId());
        assertEquals("DEV-001", event.getDevSerial());
        assertEquals(445, event.getResponsePort());
        assertEquals("cust-a", event.getCustomerId());
    }

    @Test
    void attackEventDeserializer_emitsNothing_forMalformedJson() {
        StreamProcessingJob.AttackEventDeserializer deserializer = new StreamProcessingJob.AttackEventDeserializer();
        ListCollector<AttackEvent> collector = new ListCollector<>();

        assertDoesNotThrow(() -> deserializer.flatMap("{not-valid-json", collector));
        assertEquals(0, collector.values.size());
    }

    @Test
    void attackEventDeserializer_emitsEventWithDefaults_forMissingFields() {
        StreamProcessingJob.AttackEventDeserializer deserializer = new StreamProcessingJob.AttackEventDeserializer();
        ListCollector<AttackEvent> collector = new ListCollector<>();

        String jsonWithMissingFields = """
            {
              "attackMac":"AA:BB:CC:DD:EE:FF",
              "responsePort":0,
              "logTime":1710000001
            }
            """;

        deserializer.flatMap(jsonWithMissingFields, collector);

        assertEquals(1, collector.values.size());
        AttackEvent event = collector.values.get(0);
        assertNull(event.getDevSerial());
        assertEquals("AA:BB:CC:DD:EE:FF", event.getAttackMac());
        assertEquals(0, event.getResponsePort());
    }
}
