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
public class VirusTotalPoller extends FeedPoller {

    public VirusTotalPoller(HttpClient httpClient, ObjectMapper objectMapper) {
        super(httpClient, objectMapper);
    }

    @Override
    public String getFeedName() {
        return "virustotal";
    }

    @Override
    protected HttpRequest buildRequest(ThreatIntelFeed feed, String apiKey) {
        String url = feed.getFeedUrl() != null ? feed.getFeedUrl()
                : "https://www.virustotal.com/api/v3/popular_threat_categories";

        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("x-apikey", apiKey)
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
            log.warn("VirusTotal response missing 'data' array");
            return List.of();
        }

        List<CreateIndicatorRequest> indicators = new ArrayList<>();

        for (JsonNode entry : data) {
            String id = entry.path("id").asText(null);
            JsonNode attributes = entry.path("attributes");

            if (id == null || id.isBlank()) {
                continue;
            }

            int malicious = attributes.path("last_analysis_stats").path("malicious").asInt(0);
            int total = attributes.path("last_analysis_stats").path("malicious").asInt(0)
                    + attributes.path("last_analysis_stats").path("undetected").asInt(0)
                    + attributes.path("last_analysis_stats").path("harmless").asInt(0);

            int confidence = total > 0 ? Math.min(100, (malicious * 100) / total) : 30;

            CreateIndicatorRequest req = new CreateIndicatorRequest();
            req.setIocValue(id);
            req.setIocType(guessIocType(id));
            req.setConfidence(confidence);
            req.setSeverity(mapSeverity(confidence));
            req.setSourceName("virustotal");
            req.setDescription("VirusTotal detection: " + malicious + "/" + total + " engines");
            req.setTags(List.of("virustotal", "multi-engine"));
            req.setValidFrom(Instant.now());
            req.setValidUntil(Instant.now().plus(14, ChronoUnit.DAYS));

            indicators.add(req);
        }

        return indicators;
    }

    private IocType guessIocType(String value) {
        if (value.contains("/")) {
            return IocType.CIDR;
        }
        if (value.contains(":")) {
            return IocType.IP_V6;
        }
        if (value.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            return IocType.IP_V4;
        }
        if (value.matches("[a-fA-F0-9]{32,64}")) {
            return IocType.FILE_HASH;
        }
        return IocType.DOMAIN;
    }

    private Severity mapSeverity(int confidence) {
        if (confidence >= 90) {
            return Severity.CRITICAL;
        }
        if (confidence >= 70) {
            return Severity.HIGH;
        }
        if (confidence >= 40) {
            return Severity.MEDIUM;
        }
        if (confidence >= 20) {
            return Severity.LOW;
        }
        return Severity.INFO;
    }
}
