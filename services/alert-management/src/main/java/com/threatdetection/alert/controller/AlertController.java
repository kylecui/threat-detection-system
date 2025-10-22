package com.threatdetection.alert.controller;

import com.threatdetection.alert.model.*;
import com.threatdetection.alert.service.alert.AlertService;
import com.threatdetection.alert.service.alert.AlertStatistics;
import com.threatdetection.alert.service.escalation.EscalationService;
import com.threatdetection.alert.service.escalation.EscalationStatistics;
import com.threatdetection.alert.service.notification.NotificationService;
import com.threatdetection.alert.service.notification.NotificationStatistics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 告警管理REST API控制器
 */
@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alert Management", description = "告警管理API")
@Validated
public class AlertController {

    private static final Logger logger = LoggerFactory.getLogger(AlertController.class);

    @Autowired
    private AlertService alertService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EscalationService escalationService;

    /**
     * 创建新告警
     */
    @PostMapping
    @Operation(summary = "创建告警", description = "创建新的安全告警")
    public ResponseEntity<Alert> createAlert(@Valid @RequestBody Alert alert) {
        logger.info("Creating alert: {}", alert.getTitle());

        Alert createdAlert = alertService.createAlert(alert);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAlert);
    }

    /**
     * 获取告警详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get alert by ID", description = "Retrieve a specific alert by its ID")
    public ResponseEntity<?> getAlert(
            @Parameter(description = "Alert ID")
            @PathVariable Long id) {
        logger.info("Fetching alert with ID: {}", id);
        try {
            Optional<Alert> alert = alertService.findById(id);
            if (alert.isEmpty()) {
                logger.warn("Alert not found: {}", id);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(alert.get());
        } catch (Exception e) {
            logger.error("Error fetching alert: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 查询告警列表
     */
    @GetMapping
    @Operation(summary = "查询告警", description = "分页查询告警列表，支持多种过滤条件")
    public ResponseEntity<Page<Alert>> getAlerts(
            @Parameter(description = "告警状态") @RequestParam(required = false) AlertStatus status,
            @Parameter(description = "告警严重程度") @RequestParam(required = false) AlertSeverity severity,
            @Parameter(description = "开始时间") @RequestParam(required = false) LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false) LocalDateTime endTime,
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "排序字段") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "排序方向") @RequestParam(defaultValue = "DESC") Sort.Direction sortDir) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDir, sortBy));
        Page<Alert> alerts = alertService.findAlerts(status, severity, startTime, endTime, pageable);

        return ResponseEntity.ok(alerts);
    }

    /**
     * 更新告警状态
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "更新告警状态", description = "更新告警的状态")
    public ResponseEntity<Alert> updateAlertStatus(
            @Parameter(description = "告警ID") @PathVariable Long id,
            @Parameter(description = "新状态") @RequestParam AlertStatus status) {

        logger.info("Updating alert {} status to {}", id, status);

        Alert updatedAlert = alertService.updateStatus(id, status);
        return ResponseEntity.ok(updatedAlert);
    }

    /**
     * 解决告警
     */
    @PostMapping("/{id}/resolve")
    @Operation(summary = "解决告警", description = "标记告警为已解决")
    public ResponseEntity<Alert> resolveAlert(
            @Parameter(description = "告警ID") @PathVariable Long id,
            @Valid @RequestBody ResolveAlertRequest request) {

        logger.info("Resolving alert {} by {}", id, request.getResolvedBy());

        Alert resolvedAlert = alertService.resolveAlert(id, request.getResolution(), request.getResolvedBy());
        return ResponseEntity.ok(resolvedAlert);
    }

    /**
     * 分配告警
     */
    @PostMapping("/{id}/assign")
    @Operation(summary = "分配告警", description = "将告警分配给指定人员")
    public ResponseEntity<Alert> assignAlert(
            @Parameter(description = "告警ID") @PathVariable Long id,
            @Valid @RequestBody AssignAlertRequest request) {

        logger.info("Assigning alert {} to {}", id, request.getAssignedTo());

        Alert assignedAlert = alertService.assignAlert(id, request.getAssignedTo());
        return ResponseEntity.ok(assignedAlert);
    }

    /**
     * 手动升级告警
     */
    @PostMapping("/{id}/escalate")
    @Operation(summary = "升级告警", description = "手动升级告警")
    public ResponseEntity<Void> escalateAlert(
            @Parameter(description = "告警ID") @PathVariable Long id,
            @Valid @RequestBody EscalateAlertRequest request) {

        logger.info("Manually escalating alert {}: {}", id, request.getReason());

        escalationService.manuallyEscalateAlert(id, request.getReason());
        return ResponseEntity.ok().build();
    }

    /**
     * 取消告警升级
     */
    @PostMapping("/{id}/cancel-escalation")
    @Operation(summary = "取消升级", description = "取消告警的升级状态")
    public ResponseEntity<Void> cancelEscalation(
            @Parameter(description = "告警ID") @PathVariable Long id) {

        logger.info("Canceling escalation for alert {}", id);

        escalationService.cancelEscalation(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取告警统计信息
     */
    @GetMapping("/analytics")
    @Operation(summary = "告警统计", description = "获取告警的统计信息")
    public ResponseEntity<AlertStatistics> getAlertAnalytics() {
        AlertStatistics stats = alertService.getAlertStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取通知统计信息
     */
    @GetMapping("/notifications/analytics")
    @Operation(summary = "通知统计", description = "获取通知的统计信息")
    public ResponseEntity<NotificationStatistics> getNotificationAnalytics() {
        NotificationStatistics stats = notificationService.getNotificationStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取升级统计信息
     */
    @GetMapping("/escalations/analytics")
    @Operation(summary = "升级统计", description = "获取告警升级的统计信息")
    public ResponseEntity<EscalationStatistics> getEscalationAnalytics() {
        EscalationStatistics stats = escalationService.getEscalationStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * 手动发送邮件通知
     */
    @PostMapping("/notify/email")
    @Operation(summary = "发送邮件通知", description = "手动发送邮件通知")
    public ResponseEntity<Map<String, Object>> sendEmailNotification(
            @Valid @RequestBody SendEmailRequest request) {

        logger.info("Sending manual email notification to: {}", request.getRecipient());

        try {
            // 创建通知对象
            Notification notification = new Notification();
            notification.setChannel(NotificationChannel.EMAIL);
            notification.setRecipient(request.getRecipient());
            notification.setSubject(request.getSubject());
            notification.setContent(request.getContent());
            notification.setStatus(NotificationStatus.PENDING);

            // 如果提供了threatId，尝试关联告警
            if (request.getThreatId() != null) {
                Optional<Alert> alert = alertService.findById(request.getThreatId());
                if (alert.isPresent()) {
                    notification.setAlert(alert.get());
                }
            }

            // 发送通知
            notificationService.sendNotification(notification);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Email notification sent successfully");
            response.put("recipient", request.getRecipient());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to send email notification: {}", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Failed to send email notification: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 归档旧告警
     */
    @PostMapping("/archive")
    @Operation(summary = "归档旧告警", description = "将指定天数前的已解决告警归档")
    public ResponseEntity<Map<String, Integer>> archiveOldAlerts(
            @Parameter(description = "天数") @RequestParam(defaultValue = "30") @Min(1) int daysOld) {

        logger.info("Archiving alerts older than {} days", daysOld);

        int archivedCount = alertService.archiveOldAlerts(daysOld);

        Map<String, Integer> result = new HashMap<>();
        result.put("archivedCount", archivedCount);

        return ResponseEntity.ok(result);
    }

    // 请求DTO类
    public static class ResolveAlertRequest {
        @NotBlank
        private String resolution;

        @NotBlank
        private String resolvedBy;

        public String getResolution() { return resolution; }
        public void setResolution(String resolution) { this.resolution = resolution; }

        public String getResolvedBy() { return resolvedBy; }
        public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    }

    public static class AssignAlertRequest {
        @NotBlank
        private String assignedTo;

        public String getAssignedTo() { return assignedTo; }
        public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    }

    public static class EscalateAlertRequest {
        @NotBlank
        private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class SendEmailRequest {
        @NotBlank
        private String recipient;

        @NotBlank
        private String subject;

        @NotBlank
        private String content;

        private Long threatId;

        public String getRecipient() { return recipient; }
        public void setRecipient(String recipient) { this.recipient = recipient; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public Long getThreatId() { return threatId; }
        public void setThreatId(Long threatId) { this.threatId = threatId; }
    }
}