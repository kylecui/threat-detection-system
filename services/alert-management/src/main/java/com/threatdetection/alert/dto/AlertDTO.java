package com.threatdetection.alert.dto;

import com.threatdetection.alert.model.Alert;
import com.threatdetection.alert.model.AlertSeverity;
import com.threatdetection.alert.model.AlertStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 告警DTO - 用于API响应，避免Hibernate懒加载问题
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertDTO {
    private Long id;
    private String title;
    private String description;
    private AlertStatus status;
    private AlertSeverity severity;
    private String source;
    private String eventType;
    private String metadata;
    private String attackMac;
    private Double threatScore;
    private List<String> affectedAssets;
    private List<String> recommendations;
    private String assignedTo;
    private String resolution;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime lastNotifiedAt;
    private Integer escalationLevel;
    private String escalationReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean escalated;
    private boolean resolved;

    /**
     * 从Alert实体转换到DTO
     */
    public static AlertDTO fromAlert(Alert alert) {
        return AlertDTO.builder()
                .id(alert.getId())
                .title(alert.getTitle())
                .description(alert.getDescription())
                .status(alert.getStatus())
                .severity(alert.getSeverity())
                .source(alert.getSource())
                .eventType(alert.getEventType())
                .metadata(alert.getMetadata())
                .attackMac(alert.getAttackMac())
                .threatScore(alert.getThreatScore())
                .affectedAssets(new ArrayList<>(alert.getAffectedAssets()))
                .recommendations(new ArrayList<>(alert.getRecommendations()))
                .assignedTo(alert.getAssignedTo())
                .resolution(alert.getResolution())
                .resolvedBy(alert.getResolvedBy())
                .resolvedAt(alert.getResolvedAt())
                .lastNotifiedAt(alert.getLastNotifiedAt())
                .escalationLevel(alert.getEscalationLevel())
                .escalationReason(alert.getEscalationReason())
                .createdAt(alert.getCreatedAt())
                .updatedAt(alert.getUpdatedAt())
                .escalated(alert.isEscalated())
                .resolved(alert.isResolved())
                .build();
    }
}
