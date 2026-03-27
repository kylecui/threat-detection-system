package com.threatdetection.stream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ThreatIntelServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(ThreatIntelServiceClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final long CACHE_TTL_MILLIS = Duration.ofMinutes(5).toMillis();

    private final String serviceUrl;
    private final HttpClient httpClient;
    private final Map<String, CachedResult> resultCache;

    public ThreatIntelServiceClient(String serviceUrl) {
        this.serviceUrl = serviceUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.resultCache = Collections.synchronizedMap(new LinkedHashMap<String, CachedResult>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedResult> eldest) {
                return size() > 1000;
            }
        });

        logger.info("ThreatIntelServiceClient initialized: serviceUrl={}", serviceUrl);
    }

    public double getIntelWeight(String attackIp) {
        if (attackIp == null || attackIp.isEmpty()) {
            return 1.0;
        }

        CachedResult cached = resultCache.get(attackIp);
        if (cached != null && !cached.isExpired()) {
            return cached.intelWeight;
        }

        double weight = fetchIntelWeight(attackIp);
        resultCache.put(attackIp, new CachedResult(weight, System.currentTimeMillis()));
        return weight;
    }

    public int getIntelScore(String attackIp) {
        if (attackIp == null || attackIp.isEmpty()) {
            return 0;
        }

        CachedResult cached = resultCache.get(attackIp);
        if (cached != null && !cached.isExpired()) {
            return cached.intelScore;
        }

        fetchAndCache(attackIp);
        cached = resultCache.get(attackIp);
        return cached != null ? cached.intelScore : 0;
    }

    private double fetchIntelWeight(String attackIp) {
        try {
            JsonNode response = fetchLookup(attackIp);
            if (response == null) {
                return 1.0;
            }

            double intelWeight = response.path("intelWeight").asDouble(1.0);
            int intelScore = response.path("confidence").asInt(0);
            resultCache.put(attackIp, new CachedResult(intelWeight, intelScore, System.currentTimeMillis()));
            return intelWeight;
        } catch (Exception e) {
            logger.warn("Failed to fetch intel weight for IP {}: {}", attackIp, e.getMessage());
            return 1.0;
        }
    }

    private void fetchAndCache(String attackIp) {
        try {
            JsonNode response = fetchLookup(attackIp);
            if (response == null) {
                resultCache.put(attackIp, new CachedResult(1.0, 0, System.currentTimeMillis()));
                return;
            }

            double intelWeight = response.path("intelWeight").asDouble(1.0);
            int intelScore = response.path("confidence").asInt(0);
            resultCache.put(attackIp, new CachedResult(intelWeight, intelScore, System.currentTimeMillis()));
        } catch (Exception e) {
            logger.warn("Failed to fetch intel data for IP {}: {}", attackIp, e.getMessage());
            resultCache.put(attackIp, new CachedResult(1.0, 0, System.currentTimeMillis()));
        }
    }

    private JsonNode fetchLookup(String attackIp) throws IOException, InterruptedException {
        String endpoint = String.format("%s/api/v1/threat-intel/lookup?ip=%s", serviceUrl, attackIp);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(3))
            .GET()
            .build();

        logger.debug("Querying threat intel for IP {}", attackIp);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.warn("Threat intel lookup failed: ip={}, statusCode={}", attackIp, response.statusCode());
            return null;
        }

        return objectMapper.readTree(response.body());
    }

    public void clearCache() {
        resultCache.clear();
        logger.info("Threat intel cache cleared");
    }

    public int getCacheSize() {
        return resultCache.size();
    }

    private static class CachedResult {
        final double intelWeight;
        final int intelScore;
        final long cachedAt;

        CachedResult(double intelWeight, long cachedAt) {
            this(intelWeight, 0, cachedAt);
        }

        CachedResult(double intelWeight, int intelScore, long cachedAt) {
            this.intelWeight = intelWeight;
            this.intelScore = intelScore;
            this.cachedAt = cachedAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > CACHE_TTL_MILLIS;
        }
    }
}
