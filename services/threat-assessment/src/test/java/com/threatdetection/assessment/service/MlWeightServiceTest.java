package com.threatdetection.assessment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MlWeightServiceTest {

    private MlWeightService mlWeightService;

    @BeforeEach
    void setUp() {
        mlWeightService = new MlWeightService(new ObjectMapper(), new SimpleMeterRegistry());
        ReflectionTestUtils.setField(mlWeightService, "cacheTtlSeconds", 300L);
    }

    @Test
    @DisplayName("缓存为空时返回1.0")
    void testGetMlWeightReturnsNeutralOnCacheMiss() {
        double weight = mlWeightService.getMlWeight("customer-001", "00:11:22:33:44:55", 2);
        assertEquals(1.0, weight, 1e-9);
    }

    @Test
    @DisplayName("消费Kafka消息后返回缓存权重")
    void testGetMlWeightReturnsCachedValueAfterKafkaMessage() {
        String msg = """
                {
                  "customerId": "customer-001",
                  "attackMac": "00:11:22:33:44:55",
                  "tier": 2,
                  "mlScore": 0.75,
                  "mlWeight": 2.1,
                  "mlConfidence": 0.85,
                  "anomalyType": "anomalous",
                  "modelVersion": "autoencoder-tier2-v1",
                  "timestamp": "2026-03-27T10:30:00Z"
                }
                """;

        mlWeightService.consumeMlDetection(msg);
        double weight = mlWeightService.getMlWeight("customer-001", "00:11:22:33:44:55", 2);
        assertEquals(2.1, weight, 1e-9);
    }

    @Test
    @DisplayName("TTL过期后返回1.0")
    void testCacheExpiryReturnsNeutral() throws InterruptedException {
        ReflectionTestUtils.setField(mlWeightService, "cacheTtlSeconds", 1L);
        String msg = """
                {
                  "customerId": "customer-001",
                  "attackMac": "00:11:22:33:44:55",
                  "tier": 2,
                  "mlScore": 0.75,
                  "mlWeight": 2.5,
                  "mlConfidence": 0.85,
                  "anomalyType": "anomalous",
                  "modelVersion": "autoencoder-tier2-v1",
                  "timestamp": "2026-03-27T10:30:00Z"
                }
                """;

        mlWeightService.consumeMlDetection(msg);
        Thread.sleep(2000);
        double weight = mlWeightService.getMlWeight("customer-001", "00:11:22:33:44:55", 2);
        assertEquals(1.0, weight, 1e-9);
    }

    @Test
    @DisplayName("缓存键包含customerId attackMac tier")
    void testCacheKeyIncludesCustomerAttackMacAndTier() {
        String tier2 = """
                {
                  "customerId": "customer-001",
                  "attackMac": "00:11:22:33:44:55",
                  "tier": 2,
                  "mlWeight": 2.0,
                  "timestamp": "2026-03-27T10:30:00Z"
                }
                """;
        String tier3 = """
                {
                  "customerId": "customer-001",
                  "attackMac": "00:11:22:33:44:55",
                  "tier": 3,
                  "mlWeight": 1.4,
                  "timestamp": "2026-03-27T10:30:00Z"
                }
                """;

        mlWeightService.consumeMlDetection(tier2);
        mlWeightService.consumeMlDetection(tier3);

        assertEquals(2.0, mlWeightService.getMlWeight("customer-001", "00:11:22:33:44:55", 2), 1e-9);
        assertEquals(1.4, mlWeightService.getMlWeight("customer-001", "00:11:22:33:44:55", 3), 1e-9);
    }

    @Test
    @DisplayName("空消息和非法消息不抛异常")
    void testInvalidKafkaMessagesDoNotCrash() {
        assertDoesNotThrow(() -> mlWeightService.consumeMlDetection(null));
        assertDoesNotThrow(() -> mlWeightService.consumeMlDetection(""));
        assertDoesNotThrow(() -> mlWeightService.consumeMlDetection("not-json"));
        assertEquals(1.0, mlWeightService.getMlWeight("customer-001", "00:11:22:33:44:55", 2), 1e-9);
    }
}
