package com.threatdetection.intelligence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "threat_intel_feeds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreatIntelFeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feed_name", nullable = false, unique = true, length = 100)
    private String feedName;

    @Column(name = "feed_url", columnDefinition = "TEXT")
    private String feedUrl;

    @Column(name = "feed_type", nullable = false, length = 50)
    private String feedType;

    @Builder.Default
    @Column(name = "enabled")
    private Boolean enabled = true;

    @Builder.Default
    @Column(name = "poll_interval_hours")
    private Integer pollIntervalHours = 6;

    @Column(name = "api_key_env_var", length = 100)
    private String apiKeyEnvVar;

    @Builder.Default
    @Column(name = "source_weight")
    private Double sourceWeight = 0.5d;

    @Column(name = "last_poll_at")
    private Instant lastPollAt;

    @Column(name = "last_poll_status", length = 20)
    private String lastPollStatus;

    @Column(name = "last_poll_error", columnDefinition = "TEXT")
    private String lastPollError;

    @Builder.Default
    @Column(name = "indicator_count")
    private Integer indicatorCount = 0;

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
        if (enabled == null) {
            enabled = true;
        }
        if (pollIntervalHours == null) {
            pollIntervalHours = 6;
        }
        if (sourceWeight == null) {
            sourceWeight = 0.5d;
        }
        if (indicatorCount == null) {
            indicatorCount = 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
