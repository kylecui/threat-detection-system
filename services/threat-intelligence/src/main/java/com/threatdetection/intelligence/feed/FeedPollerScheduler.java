package com.threatdetection.intelligence.feed;

import com.threatdetection.intelligence.dto.BulkUpsertRequest;
import com.threatdetection.intelligence.dto.CreateIndicatorRequest;
import com.threatdetection.intelligence.model.ThreatIntelFeed;
import com.threatdetection.intelligence.repository.ThreatIntelFeedRepository;
import com.threatdetection.intelligence.service.ThreatIndicatorService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FeedPollerScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeedPollerScheduler.class);

    private final ThreatIntelFeedRepository feedRepository;
    private final ThreatIndicatorService indicatorService;
    private final FeedRateLimiter rateLimiter;
    private final Map<String, FeedPoller> pollerMap;

    @Value("${feed.poller.enabled:true}")
    private boolean pollerEnabled;

    public FeedPollerScheduler(
            ThreatIntelFeedRepository feedRepository,
            ThreatIndicatorService indicatorService,
            FeedRateLimiter rateLimiter,
            List<FeedPoller> pollers
    ) {
        this.feedRepository = feedRepository;
        this.indicatorService = indicatorService;
        this.rateLimiter = rateLimiter;
        this.pollerMap = new HashMap<>();
        for (FeedPoller poller : pollers) {
            this.pollerMap.put(poller.getFeedName(), poller);
        }
        log.info("Registered {} feed pollers: {}", pollerMap.size(), pollerMap.keySet());
    }

    @Scheduled(fixedDelayString = "${feed.poller.check-interval-ms:300000}", initialDelayString = "${feed.poller.initial-delay-ms:60000}")
    public void pollFeeds() {
        if (!pollerEnabled) {
            return;
        }

        List<ThreatIntelFeed> enabledFeeds = feedRepository.findByEnabledTrue();
        log.debug("Checking {} enabled feeds for polling", enabledFeeds.size());

        for (ThreatIntelFeed feed : enabledFeeds) {
            if ("INTERNAL".equals(feed.getFeedType())) {
                continue;
            }

            if (!isDue(feed)) {
                continue;
            }

            FeedPoller poller = pollerMap.get(feed.getFeedName());
            if (poller == null) {
                log.warn("No poller registered for feed: {}", feed.getFeedName());
                continue;
            }

            pollSingleFeed(feed, poller);
        }
    }

    @CircuitBreaker(name = "feedPoller", fallbackMethod = "onPollFailure")
    public FeedPollerResult pollSingleFeed(ThreatIntelFeed feed, FeedPoller poller) {
        long start = System.currentTimeMillis();
        String feedName = feed.getFeedName();

        try {
            int maxPerHour = getRateLimit(feedName);
            if (!rateLimiter.tryAcquire(feedName, maxPerHour)) {
                updateFeedStatus(feed, "RATE_LIMITED", "Rate limit exceeded", 0);
                return FeedPollerResult.builder()
                        .feedName(feedName)
                        .success(false)
                        .errorMessage("Rate limited")
                        .durationMs(System.currentTimeMillis() - start)
                        .build();
            }

            String apiKey = resolveApiKey(feed);
            if (apiKey == null || apiKey.isBlank()) {
                updateFeedStatus(feed, "FAILED", "API key not configured: " + feed.getApiKeyEnvVar(), 0);
                return FeedPollerResult.builder()
                        .feedName(feedName)
                        .success(false)
                        .errorMessage("API key missing")
                        .durationMs(System.currentTimeMillis() - start)
                        .build();
            }

            List<CreateIndicatorRequest> indicators = poller.poll(feed, apiKey);

            int upserted = 0;
            if (!indicators.isEmpty()) {
                BulkUpsertRequest bulkRequest = new BulkUpsertRequest();
                bulkRequest.setIndicators(indicators);
                upserted = indicatorService.bulkUpsert(bulkRequest);
            }

            updateFeedStatus(feed, "SUCCESS", null, upserted);
            log.info("Feed {} polled successfully: {} indicators processed", feedName, upserted);

            return FeedPollerResult.builder()
                    .feedName(feedName)
                    .success(true)
                    .indicatorsProcessed(indicators.size())
                    .indicatorsNew(upserted)
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            updateFeedStatus(feed, "FAILED", errorMsg, 0);
            log.error("Feed {} poll failed: {}", feedName, errorMsg, e);

            return FeedPollerResult.builder()
                    .feedName(feedName)
                    .success(false)
                    .errorMessage(errorMsg)
                    .durationMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    public FeedPollerResult onPollFailure(ThreatIntelFeed feed, FeedPoller poller, Exception e) {
        log.warn("Circuit breaker open for feed {}: {}", feed.getFeedName(), e.getMessage());
        updateFeedStatus(feed, "FAILED", "Circuit breaker: " + e.getMessage(), 0);
        return FeedPollerResult.builder()
                .feedName(feed.getFeedName())
                .success(false)
                .errorMessage("Circuit breaker open: " + e.getMessage())
                .build();
    }

    public FeedPollerResult triggerManualPoll(Long feedId) {
        ThreatIntelFeed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new RuntimeException("Feed not found: " + feedId));

        FeedPoller poller = pollerMap.get(feed.getFeedName());
        if (poller == null) {
            return FeedPollerResult.builder()
                    .feedName(feed.getFeedName())
                    .success(false)
                    .errorMessage("No poller registered for feed: " + feed.getFeedName())
                    .build();
        }

        return pollSingleFeed(feed, poller);
    }

    private boolean isDue(ThreatIntelFeed feed) {
        if (feed.getLastPollAt() == null) {
            return true;
        }
        int intervalHours = feed.getPollIntervalHours() != null ? feed.getPollIntervalHours() : 6;
        Instant nextPoll = feed.getLastPollAt().plus(intervalHours, ChronoUnit.HOURS);
        return Instant.now().isAfter(nextPoll);
    }

    private void updateFeedStatus(ThreatIntelFeed feed, String status, String error, int count) {
        feed.setLastPollAt(Instant.now());
        feed.setLastPollStatus(status);
        feed.setLastPollError(error);
        if ("SUCCESS".equals(status)) {
            feed.setIndicatorCount((feed.getIndicatorCount() != null ? feed.getIndicatorCount() : 0) + count);
        }
        feedRepository.save(feed);
    }

    private String resolveApiKey(ThreatIntelFeed feed) {
        String envVar = feed.getApiKeyEnvVar();
        if (envVar == null || envVar.isBlank()) {
            return null;
        }
        return System.getenv(envVar);
    }

    private int getRateLimit(String feedName) {
        return switch (feedName) {
            case "abuseipdb" -> 41;
            case "virustotal" -> 20;
            case "greynoise" -> 200;
            case "alienvault_otx" -> 100;
            default -> 10;
        };
    }
}
