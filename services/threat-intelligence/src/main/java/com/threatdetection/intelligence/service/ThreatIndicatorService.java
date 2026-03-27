package com.threatdetection.intelligence.service;

import com.threatdetection.intelligence.dto.BulkUpsertRequest;
import com.threatdetection.intelligence.dto.CreateIndicatorRequest;
import com.threatdetection.intelligence.dto.IndicatorResponse;
import com.threatdetection.intelligence.dto.LookupResponse;
import com.threatdetection.intelligence.dto.StatisticsResponse;
import com.threatdetection.intelligence.dto.UpdateIndicatorRequest;
import com.threatdetection.intelligence.exception.IndicatorNotFoundException;
import com.threatdetection.intelligence.model.IocType;
import com.threatdetection.intelligence.model.Severity;
import com.threatdetection.intelligence.model.ThreatIndicator;
import com.threatdetection.intelligence.model.ThreatIntelFeed;
import com.threatdetection.intelligence.repository.ThreatIndicatorRepository;
import com.threatdetection.intelligence.repository.ThreatIntelFeedRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ThreatIndicatorService {

    private static final Logger log = LoggerFactory.getLogger(ThreatIndicatorService.class);

    private final ThreatIndicatorRepository threatIndicatorRepository;
    private final ThreatIntelFeedRepository threatIntelFeedRepository;

    public ThreatIndicatorService(
            ThreatIndicatorRepository threatIndicatorRepository,
            ThreatIntelFeedRepository threatIntelFeedRepository
    ) {
        this.threatIndicatorRepository = threatIndicatorRepository;
        this.threatIntelFeedRepository = threatIntelFeedRepository;
    }

    @Transactional(readOnly = true)
    public LookupResponse lookupIp(String ip) {
        Instant now = Instant.now();
        List<ThreatIndicator> indicators = threatIndicatorRepository.findActiveByIocValue(ip, now);

        if (indicators.isEmpty()) {
            return LookupResponse.builder()
                    .ip(ip)
                    .found(false)
                    .confidence(0)
                    .severity(Severity.INFO.name())
                    .intelWeight(1.0d)
                    .sources(Collections.emptyList())
                    .indicatorCount(0)
                    .lastSeenAt(null)
                    .build();
        }

        int maxConfidence = indicators.stream()
                .map(ThreatIndicator::getConfidence)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);

        List<String> sources = indicators.stream()
                .map(ThreatIndicator::getSourceName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        int boostedConfidence = Math.min(100, maxConfidence + Math.max(0, sources.size() - 1) * 5);

        Severity highestSeverity = indicators.stream()
                .map(ThreatIndicator::getSeverity)
                .filter(Objects::nonNull)
                .min(this::compareSeverity)
                .orElse(Severity.MEDIUM);

        Instant lastSeenAt = indicators.stream()
                .map(ThreatIndicator::getLastSeenAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        double intelWeight = calculateIntelWeight(boostedConfidence, highestSeverity);

        return LookupResponse.builder()
                .ip(ip)
                .found(true)
                .confidence(boostedConfidence)
                .severity(highestSeverity.name())
                .intelWeight(intelWeight)
                .sources(sources)
                .indicatorCount(indicators.size())
                .lastSeenAt(lastSeenAt)
                .build();
    }

    @Transactional
    public IndicatorResponse createIndicator(CreateIndicatorRequest request) {
        ThreatIndicator indicator = ThreatIndicator.builder()
                .iocValue(request.getIocValue())
                .iocType(request.getIocType())
                .iocInet(resolveIocInet(request.getIocType(), request.getIocValue()))
                .indicatorType(defaultIfBlank(request.getIndicatorType(), "malicious-activity"))
                .confidence(defaultConfidence(request.getConfidence()))
                .validFrom(request.getValidFrom() != null ? request.getValidFrom() : Instant.now())
                .validUntil(request.getValidUntil())
                .severity(request.getSeverity() != null ? request.getSeverity() : Severity.MEDIUM)
                .sourceName(request.getSourceName())
                .description(request.getDescription())
                .tags(joinTags(request.getTags()))
                .firstSeenAt(Instant.now())
                .lastSeenAt(Instant.now())
                .sightingCount(1)
                .build();

        ThreatIndicator saved = threatIndicatorRepository.save(indicator);
        log.info("Created threat indicator id={} iocValue={} source={}", saved.getId(), saved.getIocValue(), saved.getSourceName());
        return toResponse(saved);
    }

    @Transactional
    public IndicatorResponse updateIndicator(Long id, UpdateIndicatorRequest request) {
        ThreatIndicator indicator = threatIndicatorRepository.findById(id)
                .orElseThrow(() -> new IndicatorNotFoundException("Threat indicator not found: " + id));

        if (request.getIocValue() != null) {
            indicator.setIocValue(request.getIocValue());
        }
        if (request.getIocType() != null) {
            indicator.setIocType(request.getIocType());
        }
        if (request.getIndicatorType() != null) {
            indicator.setIndicatorType(request.getIndicatorType());
        }
        if (request.getPattern() != null) {
            indicator.setPattern(request.getPattern());
        }
        if (request.getPatternType() != null) {
            indicator.setPatternType(request.getPatternType());
        }
        if (request.getConfidence() != null) {
            indicator.setConfidence(defaultConfidence(request.getConfidence()));
        }
        if (request.getValidFrom() != null) {
            indicator.setValidFrom(request.getValidFrom());
        }
        if (request.getValidUntil() != null) {
            indicator.setValidUntil(request.getValidUntil());
        }
        if (request.getSeverity() != null) {
            indicator.setSeverity(request.getSeverity());
        }
        if (request.getSourceName() != null) {
            indicator.setSourceName(request.getSourceName());
        }
        if (request.getDescription() != null) {
            indicator.setDescription(request.getDescription());
        }
        if (request.getTags() != null) {
            indicator.setTags(joinTags(request.getTags()));
        }

        indicator.setIocInet(resolveIocInet(indicator.getIocType(), indicator.getIocValue()));

        ThreatIndicator saved = threatIndicatorRepository.save(indicator);
        log.info("Updated threat indicator id={}", saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public void deleteIndicator(Long id) {
        if (!threatIndicatorRepository.existsById(id)) {
            throw new IndicatorNotFoundException("Threat indicator not found: " + id);
        }
        threatIndicatorRepository.deleteById(id);
        log.info("Deleted threat indicator id={}", id);
    }

    @Transactional(readOnly = true)
    public IndicatorResponse getIndicator(Long id) {
        ThreatIndicator indicator = threatIndicatorRepository.findById(id)
                .orElseThrow(() -> new IndicatorNotFoundException("Threat indicator not found: " + id));
        return toResponse(indicator);
    }

    @Transactional(readOnly = true)
    public Page<IndicatorResponse> listIndicators(Pageable pageable) {
        return threatIndicatorRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public int bulkUpsert(BulkUpsertRequest request) {
        int upserted = 0;
        for (CreateIndicatorRequest item : request.getIndicators()) {
            Optional<ThreatIndicator> existingOptional = threatIndicatorRepository
                    .findByIocValueAndSourceName(item.getIocValue(), item.getSourceName());

            if (existingOptional.isPresent()) {
                ThreatIndicator existing = existingOptional.get();
                applyUpsertUpdate(existing, item);
                threatIndicatorRepository.save(existing);
                upserted++;
                continue;
            }

            ThreatIndicator created = ThreatIndicator.builder()
                    .iocValue(item.getIocValue())
                    .iocType(item.getIocType())
                    .iocInet(resolveIocInet(item.getIocType(), item.getIocValue()))
                    .indicatorType(defaultIfBlank(item.getIndicatorType(), "malicious-activity"))
                    .confidence(defaultConfidence(item.getConfidence()))
                    .validFrom(item.getValidFrom() != null ? item.getValidFrom() : Instant.now())
                    .validUntil(item.getValidUntil())
                    .severity(item.getSeverity() != null ? item.getSeverity() : Severity.MEDIUM)
                    .sourceName(item.getSourceName())
                    .description(item.getDescription())
                    .tags(joinTags(item.getTags()))
                    .sightingCount(1)
                    .firstSeenAt(Instant.now())
                    .lastSeenAt(Instant.now())
                    .build();
            threatIndicatorRepository.save(created);
            upserted++;
        }

        log.info("Bulk upsert finished, processed={}", upserted);
        return upserted;
    }

    @Transactional
    public void incrementSighting(Long id) {
        ThreatIndicator indicator = threatIndicatorRepository.findById(id)
                .orElseThrow(() -> new IndicatorNotFoundException("Threat indicator not found: " + id));

        int current = indicator.getSightingCount() != null ? indicator.getSightingCount() : 0;
        indicator.setSightingCount(current + 1);

        Instant now = Instant.now();
        if (indicator.getFirstSeenAt() == null) {
            indicator.setFirstSeenAt(now);
        }
        indicator.setLastSeenAt(now);

        threatIndicatorRepository.save(indicator);
        log.debug("Incremented sighting for indicator id={} -> {}", id, indicator.getSightingCount());
    }

    @Transactional(readOnly = true)
    public StatisticsResponse getStatistics() {
        List<ThreatIndicator> all = threatIndicatorRepository.findAll();

        Map<String, Long> bySource = all.stream()
                .filter(i -> i.getSourceName() != null)
                .collect(Collectors.groupingBy(ThreatIndicator::getSourceName, LinkedHashMap::new, Collectors.counting()));

        Map<String, Long> bySeverity = all.stream()
                .map(ThreatIndicator::getSeverity)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Severity::name, LinkedHashMap::new, Collectors.counting()));

        return StatisticsResponse.builder()
                .totalIndicators(threatIndicatorRepository.count())
                .activeIndicators(threatIndicatorRepository.countActive(Instant.now()))
                .bySource(bySource)
                .bySeverity(bySeverity)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ThreatIntelFeed> listFeeds() {
        return threatIntelFeedRepository.findAll();
    }

    private void applyUpsertUpdate(ThreatIndicator indicator, CreateIndicatorRequest item) {
        indicator.setIocType(item.getIocType());
        indicator.setIocInet(resolveIocInet(item.getIocType(), item.getIocValue()));
        indicator.setIndicatorType(defaultIfBlank(item.getIndicatorType(), indicator.getIndicatorType()));
        indicator.setDescription(item.getDescription() != null ? item.getDescription() : indicator.getDescription());

        if (item.getTags() != null) {
            indicator.setTags(joinTags(item.getTags()));
        }

        int existingConfidence = indicator.getConfidence() != null ? indicator.getConfidence() : 0;
        int incomingConfidence = defaultConfidence(item.getConfidence());
        if (incomingConfidence > existingConfidence) {
            indicator.setConfidence(incomingConfidence);
        }

        Severity existingSeverity = indicator.getSeverity() != null ? indicator.getSeverity() : Severity.MEDIUM;
        Severity incomingSeverity = item.getSeverity() != null ? item.getSeverity() : existingSeverity;
        if (compareSeverity(incomingSeverity, existingSeverity) < 0) {
            indicator.setSeverity(incomingSeverity);
        }

        if (item.getValidFrom() != null) {
            indicator.setValidFrom(item.getValidFrom());
        }
        if (item.getValidUntil() != null) {
            indicator.setValidUntil(item.getValidUntil());
        }

        int currentSighting = indicator.getSightingCount() != null ? indicator.getSightingCount() : 0;
        indicator.setSightingCount(currentSighting + 1);
        indicator.setLastSeenAt(Instant.now());
        if (indicator.getFirstSeenAt() == null) {
            indicator.setFirstSeenAt(Instant.now());
        }
    }

    private IndicatorResponse toResponse(ThreatIndicator indicator) {
        return IndicatorResponse.builder()
                .id(indicator.getId())
                .iocValue(indicator.getIocValue())
                .iocType(indicator.getIocType())
                .iocInet(indicator.getIocInet())
                .indicatorType(indicator.getIndicatorType())
                .pattern(indicator.getPattern())
                .patternType(indicator.getPatternType())
                .confidence(indicator.getConfidence())
                .validFrom(indicator.getValidFrom())
                .validUntil(indicator.getValidUntil())
                .severity(indicator.getSeverity())
                .sourceName(indicator.getSourceName())
                .description(indicator.getDescription())
                .tags(splitTags(indicator.getTags()))
                .sightingCount(indicator.getSightingCount())
                .firstSeenAt(indicator.getFirstSeenAt())
                .lastSeenAt(indicator.getLastSeenAt())
                .createdAt(indicator.getCreatedAt())
                .updatedAt(indicator.getUpdatedAt())
                .build();
    }

    private String resolveIocInet(IocType iocType, String iocValue) {
        if (iocType == null || iocValue == null) {
            return null;
        }
        return switch (iocType) {
            case IP_V4, IP_V6, CIDR -> iocValue;
            default -> null;
        };
    }

    private int defaultConfidence(Integer confidence) {
        if (confidence == null) {
            return 50;
        }
        return Math.max(0, Math.min(100, confidence));
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(","));
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String defaultIfBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private int compareSeverity(Severity a, Severity b) {
        return Integer.compare(severityRank(a), severityRank(b));
    }

    private int severityRank(Severity severity) {
        if (severity == null) {
            return 99;
        }
        return switch (severity) {
            case CRITICAL -> 1;
            case HIGH -> 2;
            case MEDIUM -> 3;
            case LOW -> 4;
            case INFO -> 5;
        };
    }

    private double calculateIntelWeight(int confidence, Severity severity) {
        double baseWeight;
        if (confidence <= 20) {
            baseWeight = 1.0;
        } else if (confidence <= 40) {
            baseWeight = 1.2;
        } else if (confidence <= 60) {
            baseWeight = 1.5;
        } else if (confidence <= 80) {
            baseWeight = 2.0;
        } else {
            baseWeight = 3.0;
        }

        double severityMultiplier = switch (severity != null ? severity : Severity.MEDIUM) {
            case CRITICAL -> 1.5;
            case HIGH -> 1.2;
            case MEDIUM -> 1.0;
            case LOW -> 0.8;
            case INFO -> 0.5;
        };

        return Math.max(1.0d, baseWeight * severityMultiplier);
    }
}
