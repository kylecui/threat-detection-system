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
public class GreyNoisePoller extends FeedPoller {

    public GreyNoisePoller(HttpClient httpClient, ObjectMapper objectMapper) {
        super(httpClient, objectMapper);
    }

    @Override
    public String getFeedName() {
        return "greynoise";
    }

    @Override
    protected HttpRequest buildRequest(ThreatIntelFeed feed, String apiKey) {
        String url = feed.getFeedUrl() != null ? feed.getFeedUrl()
                : "https://api.greynoise.io/v3/community";

        return HttpRequest.newBuilder()
                .uri(URI.create(url + "?query=classification:malicious&size=100"))
                .header("key", apiKey)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
    }

    @Override
    protected List<CreateIndicatorRequest> parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        List<CreateIndicatorRequest> indicators = new ArrayList<>();

        JsonNode data = root.path("data");
        if (data.isArray()) {
            for (JsonNode entry : data) {
                String ip = entry.path("ip").asText(null);
                if (ip == null || ip.isBlank()) {
                    continue;
                }

                String classification = entry.path("classification").asText("unknown");
                String name = entry.path("name").asText("unknown");
                boolean isBot = entry.path("bot").asBoolean(false);

                int confidence = "malicious".equals(classification) ? 80 : 40;

                CreateIndicatorRequest req = new CreateIndicatorRequest();
                req.setIocValue(ip);
                req.setIocType(ip.contains(":") ? IocType.IP_V6 : IocType.IP_V4);
                req.setConfidence(confidence);
                req.setSeverity("malicious".equals(classification) ? Severity.HIGH : Severity.LOW);
                req.setSourceName("greynoise");
                req.setDescription("GreyNoise: " + classification + " (name: " + name + ", bot: " + isBot + ")");
                req.setTags(List.of("greynoise", classification));
                req.setValidFrom(Instant.now());
                req.setValidUntil(Instant.now().plus(3, ChronoUnit.DAYS));

                indicators.add(req);
            }
        }

        if (indicators.isEmpty() && root.has("ip")) {
            String ip = root.path("ip").asText(null);
            String classification = root.path("classification").asText("unknown");
            boolean noise = root.path("noise").asBoolean(false);

            if (ip != null && noise) {
                CreateIndicatorRequest req = new CreateIndicatorRequest();
                req.setIocValue(ip);
                req.setIocType(ip.contains(":") ? IocType.IP_V6 : IocType.IP_V4);
                req.setConfidence("malicious".equals(classification) ? 75 : 30);
                req.setSeverity("malicious".equals(classification) ? Severity.HIGH : Severity.INFO);
                req.setSourceName("greynoise");
                req.setDescription("GreyNoise community: " + classification);
                req.setTags(List.of("greynoise", "community"));
                req.setValidFrom(Instant.now());
                req.setValidUntil(Instant.now().plus(3, ChronoUnit.DAYS));
                indicators.add(req);
            }
        }

        return indicators;
    }
}
