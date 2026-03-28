package com.threatdetection.assessment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.assessment.dto.MlWeightCacheEntry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MlWeightService {

    private static final Logger logger = LoggerFactory.getLogger(MlWeightService.class);
    private static final int MAX_CACHE_SIZE = 5000;

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, MlWeightCacheEntry> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> accessTimes = new ConcurrentHashMap<>();

    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter cacheUpdateCounter;

    @Value("${ml.weight.cache-ttl:300}")
    private long cacheTtlSeconds;

    @Autowired
    public MlWeightService(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.cacheHitCounter = Counter.builder("ml.weight.cache.hits")
                .description("ML weight cache hits")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("ml.weight.cache.misses")
                .description("ML weight cache misses")
                .register(meterRegistry);
        this.cacheUpdateCounter = Counter.builder("ml.weight.cache.updates")
                .description("ML weight cache updates")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = "${kafka.topics.ml-threat-detections:ml-threat-detections}",
            groupId = "threat-assessment-ml-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeMlDetection(@Payload String message) {
        if (message == null || message.isBlank()) {
            logger.debug("Skipping empty ML detection message");
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(message);
            String customerId = getText(root, "customerId");
            String attackMac = getText(root, "attackMac");
            Integer tier = getInteger(root, "tier");

            if (customerId == null || attackMac == null || tier == null) {
                logger.debug("Skipping ML detection due to missing key fields: {}", message);
                return;
            }

            MlWeightCacheEntry entry = MlWeightCacheEntry.builder()
                    .mlWeight(getDouble(root, "mlWeight", 1.0))
                    .mlScore(getDouble(root, "mlScore", 0.0))
                    .mlConfidence(getDouble(root, "mlConfidence", 0.0))
                    .anomalyType(getText(root, "anomalyType"))
                    .modelVersion(getText(root, "modelVersion"))
                    .timestamp(parseInstant(getText(root, "timestamp")))
                    .cachedAt(Instant.now())
                    .build();

            String key = buildKey(customerId, attackMac, tier);
            cache.put(key, entry);
            accessTimes.put(key, System.nanoTime());
            evictIfNeeded();
            cacheUpdateCounter.increment();

            logger.debug("ML weight cache updated: key={}, mlWeight={}, cacheSize={}", key, entry.getMlWeight(), cache.size());
        } catch (Exception e) {
            logger.warn("Failed to consume ML detection message: {}", e.getMessage());
            logger.debug("Failed ML detection payload: {}", message);
        }
    }

    public double getMlWeight(String customerId, String attackMac, Integer tier) {
        if (customerId == null || attackMac == null || tier == null) {
            cacheMissCounter.increment();
            logger.debug("ML weight cache miss due to null key parts: customerId={}, attackMac={}, tier={}", customerId, attackMac, tier);
            return 1.0;
        }

        String key = buildKey(customerId, attackMac, tier);
        MlWeightCacheEntry entry = cache.get(key);
        if (entry == null) {
            cacheMissCounter.increment();
            logger.debug("ML weight cache miss: key={}", key);
            return 1.0;
        }

        if (entry.isExpired(cacheTtlSeconds)) {
            cache.remove(key);
            accessTimes.remove(key);
            cacheMissCounter.increment();
            logger.debug("ML weight cache expired: key={}", key);
            return 1.0;
        }

        accessTimes.put(key, System.nanoTime());
        cacheHitCounter.increment();
        return entry.getMlWeight();
    }

    private String buildKey(String customerId, String attackMac, Integer tier) {
        return customerId + ":" + attackMac + ":" + tier;
    }

    private void evictIfNeeded() {
        while (cache.size() > MAX_CACHE_SIZE) {
            Map.Entry<String, Long> lruEntry = accessTimes.entrySet().stream()
                    .min(Comparator.comparingLong(Map.Entry::getValue))
                    .orElse(null);
            if (lruEntry == null) {
                return;
            }
            String key = lruEntry.getKey();
            cache.remove(key);
            accessTimes.remove(key);
        }
    }

    private String getText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private Integer getInteger(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isInt() ? node.asInt() : Integer.valueOf(node.asText());
    }

    private double getDouble(JsonNode root, String field, double defaultValue) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        return node.asDouble(defaultValue);
    }

    private Instant parseInstant(String value) {
        if (value == null) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }
}
