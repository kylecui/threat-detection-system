package com.threatdetection.alert.service.integration;

import com.threatdetection.alert.model.*;
import com.threatdetection.alert.service.alert.AlertService;
import com.threatdetection.alert.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 集成测试服务
 * 监听威胁事件并发送邮件通知（仅限CRITICAL等级，每10分钟最多5封）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationTestService {

    private final NotificationService notificationService;
    private final AlertService alertService;

    @Value("${integration-test.email.recipient:kylecui@outlook.com}")
    private String testEmailRecipient;

    // 通知频率控制：每10分钟最多5封邮件
    private static final int MAX_EMAILS_PER_WINDOW = 5;
    private static final int WINDOW_MINUTES = 10;

    // 线程安全的计数器和时间窗口跟踪
    private final ConcurrentHashMap<String, AtomicInteger> emailCountPerWindow = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> windowStartTime = new ConcurrentHashMap<>();

    /**
     * 监听威胁检测事件（用于集成测试）
     */
    @KafkaListener(
            topics = "${app.kafka.topics.threat-events}",
            groupId = "integration-test-group",
            containerFactory = "kafkaManualAckListenerContainerFactory"
    )
    public void consumeThreatEventsForNotification(
            @Payload List<Map<String, Object>> events,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) List<Long> offsets,
            Acknowledgment acknowledgment) {

        log.info("集成测试服务接收到 {} 个威胁检测事件", events.size());

        try {
            for (Map<String, Object> event : events) {
                processThreatEventForNotification(event);
            }

            acknowledgment.acknowledge();
            log.info("集成测试服务成功处理 {} 个威胁检测事件", events.size());

        } catch (Exception e) {
            log.error("集成测试服务处理威胁事件时发生错误", e);
        }
    }

    /**
     * 处理单个威胁事件并决定是否发送通知
     */
    private void processThreatEventForNotification(Map<String, Object> event) {
        try {
            Integer severity = (Integer) event.get("severity");
            String eventType = (String) event.get("eventType");
            String source = (String) event.get("source");
            String message = (String) event.get("message");

            // 如果是threat-assessment服务发布的threat-events，使用不同的字段映射
            if (eventType == null && event.containsKey("title")) {
                eventType = "THREAT_ASSESSMENT";
                source = "threat-assessment-service";
                message = (String) event.get("description");
            }

            log.debug("处理威胁事件: 类型={}, 来源={}, 消息={}, 严重程度={}",
                    eventType, source, message, severity);

            // 只处理CRITICAL等级的威胁（severity == 0 表示CRITICAL）
            if (severity == null || severity != 0) {
                log.debug("跳过非CRITICAL等级威胁: severity={}", severity);
                return;
            }

            // 检查邮件发送频率限制
            if (!canSendEmail()) {
                log.info("达到邮件发送频率限制，跳过通知: severity={}, eventType={}", severity, eventType);
                return;
            }

            // 创建告警对象用于通知
            Alert alert = Alert.builder()
                    .title(generateAlertTitle(eventType, source))
                    .description(message)
                    .severity(AlertSeverity.CRITICAL)
                    .threatScore(severity * 100.0) // 转换为威胁分数
                    .source(source)
                    .eventType(eventType)
                    .createdAt(LocalDateTime.now())
                    .build();

            // 如果有threatScore字段，使用它
            if (event.containsKey("threatScore")) {
                Double threatScore = null;
                if (event.get("threatScore") instanceof Double) {
                    threatScore = (Double) event.get("threatScore");
                } else if (event.get("threatScore") instanceof Integer) {
                    threatScore = ((Integer) event.get("threatScore")).doubleValue();
                }
                if (threatScore != null) {
                    alert.setThreatScore(threatScore);
                }
            }

            // 如果有title字段，使用它来生成更好的标题
            if (event.containsKey("title")) {
                String threatTitle = (String) event.get("title");
                if (threatTitle != null && !threatTitle.isEmpty()) {
                    alert.setTitle(String.format("[%s] %s", source, threatTitle));
                }
            }

            // 先保存告警到数据库
            Alert savedAlert = alertService.createAlert(alert);

            // 创建邮件通知
            Notification emailNotification = new Notification(
                    savedAlert,  // 使用已保存的告警
                    NotificationChannel.EMAIL,
                    testEmailRecipient
            );

            emailNotification.setSubject(generateEmailSubject(alert));
            emailNotification.setContent(generateEmailContent(alert, event));

            // 发送通知
            log.info("发送CRITICAL等级威胁邮件通知到: {}", testEmailRecipient);
            notificationService.sendNotification(emailNotification);

            // 记录发送统计
            recordEmailSent();

        } catch (Exception e) {
            log.error("处理威胁事件通知时发生错误", e);
        }
    }

    /**
     * 检查是否可以发送邮件（频率限制）
     */
    private boolean canSendEmail() {
        String windowKey = getCurrentWindowKey();
        LocalDateTime now = LocalDateTime.now();

        // 获取或创建当前窗口的计数器
        AtomicInteger count = emailCountPerWindow.computeIfAbsent(windowKey, k -> new AtomicInteger(0));
        LocalDateTime windowStart = windowStartTime.computeIfAbsent(windowKey, k -> now);

        // 检查是否需要重置窗口（超过10分钟）
        if (windowStart.plusMinutes(WINDOW_MINUTES).isBefore(now)) {
            // 重置窗口
            emailCountPerWindow.put(windowKey, new AtomicInteger(0));
            windowStartTime.put(windowKey, now);
            count = emailCountPerWindow.get(windowKey);
        }

        // 检查是否超过限制
        return count.get() < MAX_EMAILS_PER_WINDOW;
    }

    /**
     * 记录邮件发送
     */
    private void recordEmailSent() {
        String windowKey = getCurrentWindowKey();
        AtomicInteger count = emailCountPerWindow.get(windowKey);
        if (count != null) {
            int newCount = count.incrementAndGet();
            log.info("邮件发送统计 - 当前窗口: {}, 已发送: {}/{}",
                    windowKey, newCount, MAX_EMAILS_PER_WINDOW);
        }
    }

    /**
     * 获取当前时间窗口的键
     */
    private String getCurrentWindowKey() {
        LocalDateTime now = LocalDateTime.now();
        // 按10分钟窗口分组
        long windowIndex = now.toEpochSecond(java.time.ZoneOffset.UTC) / (WINDOW_MINUTES * 60);
        return "window_" + windowIndex;
    }

    /**
     * 生成告警标题
     */
    private String generateAlertTitle(String eventType, String source) {
        return String.format("[%s] %s 威胁检测", source, eventType);
    }

    /**
     * 生成邮件主题
     */
    private String generateEmailSubject(Alert alert) {
        return String.format("🚨 CRITICAL威胁告警 - %s", alert.getTitle());
    }

    /**
     * 生成邮件内容
     */
    private String generateEmailContent(Alert alert, Map<String, Object> event) {
        StringBuilder content = new StringBuilder();
        content.append("威胁检测系统 - CRITICAL等级告警\n\n");
        content.append("告警信息:\n");
        content.append("- 标题: ").append(alert.getTitle()).append("\n");
        content.append("- 描述: ").append(alert.getDescription()).append("\n");
        content.append("- 严重程度: ").append(alert.getSeverity()).append("\n");
        content.append("- 威胁分数: ").append(alert.getThreatScore()).append("\n");
        content.append("- 来源: ").append(alert.getSource()).append("\n");
        content.append("- 事件类型: ").append(alert.getEventType()).append("\n");
        content.append("- 检测时间: ").append(alert.getCreatedAt()).append("\n\n");

        content.append("原始事件数据:\n");
        event.forEach((key, value) ->
            content.append("- ").append(key).append(": ").append(value).append("\n")
        );

        content.append("\n请立即处理此高危威胁！\n");
        content.append("威胁检测系统自动告警");

        return content.toString();
    }

    /**
     * 获取通知统计信息
     */
    public NotificationStats getNotificationStats() {
        String currentWindow = getCurrentWindowKey();
        AtomicInteger currentCount = emailCountPerWindow.getOrDefault(currentWindow, new AtomicInteger(0));
        LocalDateTime windowStart = windowStartTime.get(currentWindow);

        return new NotificationStats(
                currentCount.get(),
                MAX_EMAILS_PER_WINDOW,
                WINDOW_MINUTES,
                windowStart,
                LocalDateTime.now()
        );
    }

    /**
     * 通知统计信息类
     */
    public static class NotificationStats {
        private final int emailsSentInCurrentWindow;
        private final int maxEmailsPerWindow;
        private final int windowMinutes;
        private final LocalDateTime windowStart;
        private final LocalDateTime currentTime;

        public NotificationStats(int emailsSentInCurrentWindow, int maxEmailsPerWindow,
                               int windowMinutes, LocalDateTime windowStart, LocalDateTime currentTime) {
            this.emailsSentInCurrentWindow = emailsSentInCurrentWindow;
            this.maxEmailsPerWindow = maxEmailsPerWindow;
            this.windowMinutes = windowMinutes;
            this.windowStart = windowStart;
            this.currentTime = currentTime;
        }

        // Getters
        public int getEmailsSentInCurrentWindow() { return emailsSentInCurrentWindow; }
        public int getMaxEmailsPerWindow() { return maxEmailsPerWindow; }
        public int getWindowMinutes() { return windowMinutes; }
        public LocalDateTime getWindowStart() { return windowStart; }
        public LocalDateTime getCurrentTime() { return currentTime; }
        public int getRemainingEmails() { return maxEmailsPerWindow - emailsSentInCurrentWindow; }
    }
}