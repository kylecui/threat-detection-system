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
public class AlienVaultOtxPoller extends FeedPoller {

    public AlienVaultOtxPoller(HttpClient httpClient, ObjectMapper objectMapper) {
        super(httpClient, objectMapper);
    }

    @Override
    public String getFeedName() {
        return "alienvault_otx";
    }

    @Override
    protected HttpRequest buildRequest(ThreatIntelFeed feed, String apiKey) {
        String url = feed.getFeedUrl() != null ? feed.getFeedUrl()
                : "https://otx.alienvault.com/api/v1/pulses/subscribed";

        return HttpRequest.newBuilder()
                .uri(URI.create(url + "?limit=50&page=1"))
                .header("X-OTX-API-KEY", apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
    }

    @Override
    protected List<CreateIndicatorRequest> parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root.path("results");

        if (!results.isArray()) {
            log.warn("OTX response missing 'results' array");
            return List.of();
        }

        List<CreateIndicatorRequest> indicators = new ArrayList<>();

        for (JsonNode pulse : results) {
            String pulseName = pulse.path("name").asText("unknown-pulse");
            JsonNode pulseIndicators = pulse.path("indicators");

            if (!pulseIndicators.isArray()) {
                continue;
            }

            for (JsonNode indicator : pulseIndicators) {
                String type = indicator.path("type").asText("");
                String iocValue = indicator.path("indicator").asText(null);

                if (iocValue == null || iocValue.isBlank()) {
                    continue;
                }

                IocType iocType = mapOtxType(type);
                if (iocType == null) {
                    continue;
                }

                CreateIndicatorRequest req = new CreateIndicatorRequest();
                req.setIocValue(iocValue);
                req.setIocType(iocType);
                req.setConfidence(70);
                req.setSeverity(Severity.MEDIUM);
                req.setSourceName("alienvault_otx");
                req.setDescription("OTX pulse: " + pulseName);
                req.setTags(List.of("otx", "pulse", sanitizeTag(pulseName)));
                req.setValidFrom(Instant.now());
                req.setValidUntil(Instant.now().plus(30, ChronoUnit.DAYS));

                indicators.add(req);
            }
        }

        return indicators;
    }

    private IocType mapOtxType(String otxType) {
        return switch (otxType) {
            case "IPv4" -> IocType.IP_V4;
            case "IPv6" -> IocType.IP_V6;
            case "CIDR" -> IocType.CIDR;
            case "domain", "hostname" -> IocType.DOMAIN;
            case "FileHash-MD5", "FileHash-SHA1", "FileHash-SHA256" -> IocType.FILE_HASH;
            default -> null;
        };
    }

    private String sanitizeTag(String tag) {
        String sanitized = tag.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        return sanitized.length() > 50 ? sanitized.substring(0, 50) : sanitized;
    }
}
