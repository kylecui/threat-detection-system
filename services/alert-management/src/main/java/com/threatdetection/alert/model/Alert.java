package com.threatdetection.alert.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 告警实体类
 */
@Entity
@Table(name = "alerts", indexes = {
    @Index(name = "idx_alert_status", columnList = "status"),
    @Index(name = "idx_alert_severity", columnList = "severity"),
    @Index(name = "idx_alert_attack_mac", columnList = "attack_mac"),
    @Index(name = "idx_alert_created_at", columnList = "created_at"),
    @Index(name = "idx_alert_source", columnList = "source")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String title;

    @Size(max = 2000)
    @Column(length = 2000)
    private String description;    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status = AlertStatus.NEW;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Size(max = 100)
    @Column(length = 100)
    private String source = "threat-detection-system";

    @Size(max = 100)
    @Column(length = 100)
    private String eventType;

    @Column
    private String metadata;

    @Size(max = 17)
    @Column(name = "attack_mac", length = 17)
    private String attackMac;

    @Column(columnDefinition = "DOUBLE PRECISION")
    private Double threatScore;

    @ElementCollection
    @CollectionTable(name = "alert_affected_assets",
                     joinColumns = @JoinColumn(name = "alert_id"))
    @Column(name = "asset")
    private List<String> affectedAssets = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "alert_recommendations",
                     joinColumns = @JoinColumn(name = "alert_id"))
    @Column(name = "recommendation")
    private List<String> recommendations = new ArrayList<>();

    @Size(max = 100)
    @Column(length = 100)
    private String assignedTo;

    @Size(max = 1000)
    @Column(length = 1000)
    private String resolution;

    @Size(max = 100)
    @Column(length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;

    @Column(name = "escalation_level")
    private Integer escalationLevel = 0;

    @Column(name = "escalation_reason")
    private String escalationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Helper methods
    public boolean isResolved() {
        return status == AlertStatus.RESOLVED;
    }

    public boolean isEscalated() {
        return escalationLevel != null && escalationLevel > 0;
    }

    public void resolve(String resolution, String resolvedBy) {
        this.status = AlertStatus.RESOLVED;
        this.resolution = resolution;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "Alert{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", status=" + status +
                ", severity=" + severity +
                ", source='" + source + '\'' +
                ", attackMac='" + attackMac + '\'' +
                ", threatScore=" + threatScore +
                ", createdAt=" + createdAt +
                '}';
    }
}