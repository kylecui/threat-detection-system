package com.threatdetection.intelligence.feed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.intelligence.dto.CreateIndicatorRequest;
import com.threatdetection.intelligence.model.IocType;
import com.threatdetection.intelligence.model.Severity;
import com.threatdetection.intelligence.model.ThreatIntelFeed;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class AbuseIpDbPoller extends FeedPoller {

    public AbuseIpDbPoller(HttpClient httpClient, ObjectMapper objectMapper) {
        super(httpClient, objectMapper);
    }

    @Override
    public String getFeedName() {
        return "abuseipdb";
    }

    @Override
    protected HttpRequest buildRequest(ThreatIntelFeed feed, String apiKey) {
        String url = feed.getFeedUrl() != null ? feed.getFeedUrl()
                : "https://api.abuseipdb.com/api/v2/blacklist";

        return HttpRequest.newBuilder()
                .uri(URI.create(url + "?confidenceMinimum=90&limit=500"))
                .header("Key", apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
    }

    @Override
    protected List<CreateIndicatorRequest> parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode data = root.path("data");

        if (!data.isArray()) {
            log.warn("AbuseIPDB response missing 'data' array");
            return List.of();
        }

        List<CreateIndicatorRequest> indicators = new ArrayList<>();
        for (JsonNode entry : data) {
            String ip = entry.path("ipAddress").asText(null);
            int abuseConfidence = entry.path("abuseConfidenceScore").asInt(50);

            if (ip == null || ip.isBlank()) {
                continue;
            }

            CreateIndicatorRequest req = new CreateIndicatorRequest();
            req.setIocValue(ip);
            req.setIocType(ip.contains(":") ? IocType.IP_V6 : IocType.IP_V4);
            req.setConfidence(abuseConfidence);
            req.setSeverity(mapSeverity(abuseConfidence));
            req.setSourceName("abuseipdb");
            req.setDescription("AbuseIPDB blacklisted IP (confidence: " + abuseConfidence + "%)");
            req.setTags(List.of("abuseipdb", "blacklist"));
            req.setValidFrom(Instant.now());
            req.setValidUntil(Instant.now().plus(7, ChronoUnit.DAYS));

            indicators.add(req);
        }

        return indicators;
    }

    private Severity mapSeverity(int abuseConfidence) {
        if (abuseConfidence >= 95) {
            return Severity.CRITICAL;
        }
        if (abuseConfidence >= 80) {
            return Severity.HIGH;
        }
        if (abuseConfidence >= 50) {
            return Severity.MEDIUM;
        }
        if (abuseConfidence >= 25) {
            return Severity.LOW;
        }
        return Severity.INFO;
    }
}
