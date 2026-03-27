package com.threatdetection.intelligence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "threat_indicators")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreatIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ioc_value", nullable = false, columnDefinition = "TEXT")
    private String iocValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "ioc_type", nullable = false, length = 20)
    private IocType iocType;

    @Column(name = "ioc_inet")
    private String iocInet;

    @Builder.Default
    @Column(name = "indicator_type", length = 50)
    private String indicatorType = "malicious-activity";

    @Column(name = "pattern", columnDefinition = "TEXT")
    private String pattern;

    @Builder.Default
    @Column(name = "pattern_type", length = 20)
    private String patternType = "stix";

    @Builder.Default
    @Column(name = "confidence")
    private Integer confidence = 50;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity = Severity.MEDIUM;

    @Column(name = "source_name", nullable = false, length = 100)
    private String sourceName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Builder.Default
    @Column(name = "sighting_count")
    private Integer sightingCount = 1;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (validFrom == null) {
            validFrom = now;
        }
        if (firstSeenAt == null) {
            firstSeenAt = now;
        }
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
        if (confidence == null) {
            confidence = 50;
        }
        if (indicatorType == null || indicatorType.isBlank()) {
            indicatorType = "malicious-activity";
        }
        if (patternType == null || patternType.isBlank()) {
            patternType = "stix";
        }
        if (severity == null) {
            severity = Severity.MEDIUM;
        }
        if (sightingCount == null || sightingCount < 1) {
            sightingCount = 1;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
