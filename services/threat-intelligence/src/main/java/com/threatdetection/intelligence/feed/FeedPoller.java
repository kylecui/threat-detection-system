package com.threatdetection.intelligence.feed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.intelligence.dto.CreateIndicatorRequest;
import com.threatdetection.intelligence.model.ThreatIntelFeed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;

public abstract class FeedPoller {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final HttpClient httpClient;
    protected final ObjectMapper objectMapper;

    protected FeedPoller(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public abstract String getFeedName();

    protected abstract HttpRequest buildRequest(ThreatIntelFeed feed, String apiKey);

    protected abstract List<CreateIndicatorRequest> parseResponse(String responseBody) throws Exception;

    public List<CreateIndicatorRequest> poll(ThreatIntelFeed feed, String apiKey) {
        try {
            HttpRequest request = buildRequest(feed, apiKey);
            log.info("Polling feed={} url={}", getFeedName(), request.uri());

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Feed {} returned HTTP {}: {}", getFeedName(), response.statusCode(),
                        truncate(response.body(), 500));
                throw new RuntimeException("HTTP " + response.statusCode() + " from " + getFeedName());
            }

            List<CreateIndicatorRequest> indicators = parseResponse(response.body());
            log.info("Feed {} returned {} indicators", getFeedName(), indicators.size());
            return indicators;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Feed {} polling interrupted", getFeedName());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Feed {} poll failed: {}", getFeedName(), e.getMessage(), e);
            throw new RuntimeException("Poll failed for " + getFeedName() + ": " + e.getMessage(), e);
        }
    }

    protected String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

}
