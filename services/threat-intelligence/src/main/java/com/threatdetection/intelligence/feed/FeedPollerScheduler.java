package com.threatdetection.intelligence.feed;

import com.threatdetection.intelligence.dto.BulkUpsertRequest;
import com.threatdetection.intelligence.dto.CreateIndicatorRequest;
import com.threatdetection.intelligence.exception.FeedNotPollableException;
import com.threatdetection.intelligence.model.ThreatIntelFeed;
import com.threatdetection.intelligence.repository.ThreatIntelFeedRepository;
import com.threatdetection.intelligence.service.ThreatIndicatorService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    @Value("${feed.poller.enabled:true}")
    private boolean pollerEnabled;

    public FeedPollerScheduler(
            ThreatIntelFeedRepository feedRepository,
            ThreatIndicatorService indicatorService,
            FeedRateLimiter rateLimiter,
            JdbcTemplate jdbcTemplate,
            List<FeedPoller> pollers
    ) {
        this.feedRepository = feedRepository;
        this.indicatorService = indicatorService;
        this.rateLimiter = rateLimiter;
        this.jdbcTemplate = jdbcTemplate;
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

            int previousCount = indicatorService.countActiveBySourceName(feedName);
            List<CreateIndicatorRequest> indicators = poller.poll(feed, apiKey);

            int upserted = 0;
            if (!indicators.isEmpty()) {
                BulkUpsertRequest bulkRequest = new BulkUpsertRequest();
                bulkRequest.setIndicators(indicators);
                upserted = indicatorService.bulkUpsert(bulkRequest);
            }

            int currentCount = indicatorService.countActiveBySourceName(feedName);
            int newCount = Math.max(0, currentCount - previousCount);
            int updatedCount = Math.max(0, upserted - newCount);
            updateFeedStatus(feed, "SUCCESS", null, currentCount);
            log.info("Feed {} polled successfully: {} indicators processed", feedName, upserted);

            return FeedPollerResult.builder()
                    .feedName(feedName)
                    .success(true)
                    .indicatorsProcessed(indicators.size())
                    .indicatorsNew(newCount)
                    .indicatorsUpdated(updatedCount)
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
        if (isInternal(feed)) {
            throw new FeedNotPollableException(feed.getFeedName(),
                    "Internal feed accepts locally written indicators and does not require polling");
        }
        if (poller == null) {
            throw new FeedNotPollableException(feed.getFeedName(),
                    "No poller is registered for this feed type");
        }

        return pollSingleFeed(feed, poller);
    }

    public List<ThreatIntelFeed> describeCapabilities(List<ThreatIntelFeed> feeds) {
        feeds.forEach(this::describeCapability);
        return feeds;
    }

    public ThreatIntelFeed describeCapability(ThreatIntelFeed feed) {
        FeedPoller poller = pollerMap.get(feed.getFeedName());
        if (isInternal(feed)) {
            feed.setPollable(false);
            feed.setCapability("LOCAL_WRITE");
            feed.setNonPollableReason("INTERNAL_SOURCE");
            feed.setPollBatchLimit(null);
            return feed;
        }

        boolean pollable = poller != null;
        feed.setPollable(pollable);
        feed.setCapability(pollable ? "SCHEDULED_AND_MANUAL_POLL" : "UNSUPPORTED");
        feed.setNonPollableReason(pollable ? null : "POLLER_NOT_REGISTERED");
        feed.setPollBatchLimit(pollable ? poller.getPollBatchLimit() : null);
        return feed;
    }

    private boolean isInternal(ThreatIntelFeed feed) {
        return "INTERNAL".equalsIgnoreCase(feed.getFeedType());
    }

    private boolean isDue(ThreatIntelFeed feed) {
        if (feed.getLastPollAt() == null) {
            return true;
        }
        int intervalHours = feed.getPollIntervalHours() != null ? feed.getPollIntervalHours() : 6;
        Instant nextPoll = feed.getLastPollAt().plus(intervalHours, ChronoUnit.HOURS);
        return Instant.now().isAfter(nextPoll);
    }

    private void updateFeedStatus(ThreatIntelFeed feed, String status, String error, int currentCount) {
        feed.setLastPollAt(Instant.now());
        feed.setLastPollStatus(status);
        feed.setLastPollError(error);
        if ("SUCCESS".equals(status)) {
            feed.setIndicatorCount(currentCount);
        }
        feedRepository.save(feed);
    }

    private String resolveApiKey(ThreatIntelFeed feed) {
        String envVar = feed.getApiKeyEnvVar();
        if (envVar == null || envVar.isBlank()) {
            return null;
        }
        try {
            List<String> values = jdbcTemplate.query(
                    "SELECT config_value FROM system_config WHERE config_key = ? AND config_value <> '' LIMIT 1",
                    (rs, rowNum) -> rs.getString(1),
                    envVar
            );
            if (!values.isEmpty() && values.get(0) != null && !values.get(0).isBlank()) {
                return values.get(0);
            }
        } catch (Exception e) {
            log.warn("Unable to read runtime API key {} from system_config: {}", envVar, e.getMessage());
        }
        String environmentValue = System.getenv(envVar);
        if ((environmentValue == null || environmentValue.isBlank()) && "VT_API_KEY".equals(envVar)) {
            // Preserve compatibility with installations that used the legacy
            // pre-3.1.32 variable name before the UI/TIRE contract was aligned.
            return System.getenv("VIRUSTOTAL_API_KEY");
        }
        return environmentValue;
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
