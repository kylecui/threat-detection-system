package com.threatdetection.intelligence.feed;

import com.threatdetection.intelligence.dto.CreateIndicatorRequest;
import com.threatdetection.intelligence.exception.FeedNotPollableException;
import com.threatdetection.intelligence.model.ThreatIntelFeed;
import com.threatdetection.intelligence.repository.ThreatIntelFeedRepository;
import com.threatdetection.intelligence.service.ThreatIndicatorService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedPollerSchedulerTest {

    @Test
    void internalFeedIsExplicitlyNonPollable() {
        ThreatIntelFeedRepository repository = mock(ThreatIntelFeedRepository.class);
        ThreatIndicatorService service = mock(ThreatIndicatorService.class);
        FeedPollerScheduler scheduler = new FeedPollerScheduler(
                repository, service, mock(FeedRateLimiter.class), mock(JdbcTemplate.class), List.of());
        ThreatIntelFeed internal = ThreatIntelFeed.builder()
                .id(1L).feedName("internal").feedType("INTERNAL").enabled(true).build();
        when(repository.findById(1L)).thenReturn(Optional.of(internal));

        FeedNotPollableException error = assertThrows(
                FeedNotPollableException.class, () -> scheduler.triggerManualPoll(1L));
        assertEquals("internal", error.getFeedName());

        scheduler.describeCapability(internal);
        assertFalse(internal.getPollable());
        assertEquals("LOCAL_WRITE", internal.getCapability());
    }

    @Test
    void successfulRefreshStoresCurrentUniqueCountInsteadOfAddingToOldCount() {
        ThreatIntelFeedRepository repository = mock(ThreatIntelFeedRepository.class);
        ThreatIndicatorService service = mock(ThreatIndicatorService.class);
        FeedRateLimiter rateLimiter = mock(FeedRateLimiter.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FeedPoller poller = mock(FeedPoller.class);
        when(poller.getFeedName()).thenReturn("abuseipdb");
        when(poller.getPollBatchLimit()).thenReturn(500);
        when(rateLimiter.tryAcquire("abuseipdb", 41)).thenReturn(true);
        when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any(String.class)))
                .thenReturn(List.of("api-key"));
        when(service.countActiveBySourceName("abuseipdb")).thenReturn(500, 500);
        when(poller.poll(any(), any())).thenReturn(List.of(new CreateIndicatorRequest()));
        when(service.bulkUpsert(any())).thenReturn(1);

        FeedPollerScheduler scheduler = new FeedPollerScheduler(
                repository, service, rateLimiter, jdbcTemplate, List.of(poller));
        ThreatIntelFeed feed = ThreatIntelFeed.builder()
                .id(2L)
                .feedName("abuseipdb")
                .feedType("REST_API")
                .apiKeyEnvVar("ABUSEIPDB_API_KEY")
                .indicatorCount(500)
                .build();

        FeedPollerResult result = scheduler.pollSingleFeed(feed, poller);

        assertTrue(result.isSuccess());
        assertEquals(500, feed.getIndicatorCount());
        assertEquals(0, result.getIndicatorsNew());
        assertEquals(1, result.getIndicatorsUpdated());
        verify(repository).save(feed);
    }
}
