package com.threatdetection.intelligence.service;

import com.threatdetection.intelligence.client.TireClient;
import com.threatdetection.intelligence.dto.StatisticsResponse;
import com.threatdetection.intelligence.dto.BulkUpsertRequest;
import com.threatdetection.intelligence.dto.CreateIndicatorRequest;
import com.threatdetection.intelligence.model.IocType;
import com.threatdetection.intelligence.model.Severity;
import com.threatdetection.intelligence.model.ThreatIndicator;
import com.threatdetection.intelligence.model.ThreatIntelFeed;
import com.threatdetection.intelligence.repository.ThreatIndicatorRepository;
import com.threatdetection.intelligence.repository.ThreatIntelFeedRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThreatIndicatorServiceTest {

    @Test
    void statisticsIncludesFeedCounts() {
        ThreatIndicatorRepository indicatorRepository = mock(ThreatIndicatorRepository.class);
        ThreatIntelFeedRepository feedRepository = mock(ThreatIntelFeedRepository.class);
        TireClient tireClient = mock(TireClient.class);
        ThreatIndicatorService service = new ThreatIndicatorService(
                indicatorRepository, feedRepository, tireClient);

        when(indicatorRepository.findAll()).thenReturn(List.of());
        when(indicatorRepository.count()).thenReturn(7L);
        when(indicatorRepository.countActive(any(Instant.class))).thenReturn(5L);
        when(feedRepository.count()).thenReturn(5L);
        when(feedRepository.countEnabledExternalFeeds()).thenReturn(1L);

        StatisticsResponse result = service.getStatistics();

        assertEquals(7L, result.getTotalIndicators());
        assertEquals(5L, result.getActiveIndicators());
        assertEquals(5L, result.getTotalFeeds());
        assertEquals(1L, result.getActiveFeeds());
    }

    @Test
    void listFeedsUsesStableDisplayOrder() {
        ThreatIndicatorRepository indicatorRepository = mock(ThreatIndicatorRepository.class);
        ThreatIntelFeedRepository feedRepository = mock(ThreatIntelFeedRepository.class);
        TireClient tireClient = mock(TireClient.class);
        ThreatIndicatorService service = new ThreatIndicatorService(
                indicatorRepository, feedRepository, tireClient);
        List<ThreatIntelFeed> expected = List.of(
                ThreatIntelFeed.builder().id(1L).feedName("internal").feedType("INTERNAL").build(),
                ThreatIntelFeed.builder().id(2L).feedName("abuseipdb").feedType("REST_API").build());
        when(feedRepository.findAllInDisplayOrder()).thenReturn(expected);

        assertEquals(expected, service.listFeeds());
        verify(feedRepository).findAllInDisplayOrder();
    }

    @Test
    void remoteRefreshDoesNotIncrementManualSightingCount() {
        ThreatIndicatorRepository indicatorRepository = mock(ThreatIndicatorRepository.class);
        ThreatIntelFeedRepository feedRepository = mock(ThreatIntelFeedRepository.class);
        TireClient tireClient = mock(TireClient.class);
        ThreatIndicatorService service = new ThreatIndicatorService(
                indicatorRepository, feedRepository, tireClient);

        ThreatIndicator existing = ThreatIndicator.builder()
                .id(7L)
                .iocValue("203.0.113.8")
                .iocType(IocType.IP_V4)
                .sourceName("abuseipdb")
                .severity(Severity.HIGH)
                .confidence(90)
                .sightingCount(4)
                .build();
        CreateIndicatorRequest incoming = new CreateIndicatorRequest();
        incoming.setIocValue("203.0.113.8");
        incoming.setIocType(IocType.IP_V4);
        incoming.setSourceName("abuseipdb");
        incoming.setSeverity(Severity.CRITICAL);
        incoming.setConfidence(99);
        BulkUpsertRequest request = new BulkUpsertRequest();
        request.setIndicators(List.of(incoming));

        when(indicatorRepository.findByIocValueAndSourceName(
                "203.0.113.8", "abuseipdb")).thenReturn(java.util.Optional.of(existing));
        when(indicatorRepository.save(existing)).thenReturn(existing);

        assertEquals(1, service.bulkUpsert(request));
        assertEquals(4, existing.getSightingCount());
        assertEquals(99, existing.getConfidence());
        assertEquals(Severity.CRITICAL, existing.getSeverity());
    }

    @Test
    void filteredIndicatorListUsesCombinedRepositoryQuery() {
        ThreatIndicatorRepository indicatorRepository = mock(ThreatIndicatorRepository.class);
        ThreatIntelFeedRepository feedRepository = mock(ThreatIntelFeedRepository.class);
        TireClient tireClient = mock(TireClient.class);
        ThreatIndicatorService service = new ThreatIndicatorService(
                indicatorRepository, feedRepository, tireClient);
        PageRequest page = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));
        when(indicatorRepository.findByIocTypeAndSeverity(IocType.IP_V4, Severity.HIGH, page))
                .thenReturn(new PageImpl<>(List.of(), page, 0));

        assertEquals(0, service.listIndicators(IocType.IP_V4, Severity.HIGH, page).getTotalElements());
        verify(indicatorRepository).findByIocTypeAndSeverity(IocType.IP_V4, Severity.HIGH, page);
    }

    @Test
    void manualSightingReturnsTheUpdatedIndicator() {
        ThreatIndicatorRepository indicatorRepository = mock(ThreatIndicatorRepository.class);
        ThreatIntelFeedRepository feedRepository = mock(ThreatIntelFeedRepository.class);
        TireClient tireClient = mock(TireClient.class);
        ThreatIndicatorService service = new ThreatIndicatorService(
                indicatorRepository, feedRepository, tireClient);
        ThreatIndicator indicator = ThreatIndicator.builder()
                .id(9L)
                .iocValue("198.51.100.9")
                .iocType(IocType.IP_V4)
                .sourceName("internal")
                .severity(Severity.MEDIUM)
                .confidence(60)
                .sightingCount(2)
                .build();
        when(indicatorRepository.findById(9L)).thenReturn(java.util.Optional.of(indicator));
        when(indicatorRepository.save(indicator)).thenReturn(indicator);

        var response = service.incrementSighting(9L);

        assertEquals(3, response.getSightingCount());
        assertNotNull(response.getLastSeenAt());
    }
}
