package com.threatdetection.alert.service.notification;

import com.threatdetection.alert.model.*;
import com.threatdetection.alert.repository.NotificationRepository;
import com.threatdetection.alert.repository.CustomerNotificationConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 通知服务 - 处理多通道通知
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CustomerNotificationConfigRepository customerNotificationConfigRepository;

    @Autowired
    private DynamicMailSenderService dynamicMailSenderService;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Autowired
    @Qualifier("twilioSmsService")
    private SmsService smsService;

    @Autowired
    private SlackService slackService;

    @Autowired
    private TeamsService teamsService;

    /**
     * 发送通知
     */
    @Async
    public void sendNotification(Notification notification) {
        logger.info("Sending notification via {} to {}", notification.getChannel(), notification.getRecipient());

        try {
            switch (notification.getChannel()) {
                case EMAIL:
                    sendEmail(notification);
                    break;
                case SMS:
                    sendSms(notification);
                    break;
                case WEBHOOK:
                    sendWebhook(notification);
                    break;
                case SLACK:
                    sendSlack(notification);
                    break;
                case TEAMS:
                    sendTeams(notification);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported channel: " + notification.getChannel());
            }

            notification.markAsSent();
            logger.info("Notification sent successfully");

        } catch (Exception e) {
            logger.error("Failed to send notification: {}", e.getMessage());
            notification.markAsFailed(e.getMessage());

            // 如果可以重试，则标记为重试状态
            if (notification.canRetry()) {
                notification.setStatus(NotificationStatus.RETRYING);
                notification.incrementRetryCount();
                // 这里可以触发重试逻辑
            }
        }

        notificationRepository.save(notification);
    }

    /**
     * 发送邮件通知 - 使用动态SMTP配置
     */
    private void sendEmail(Notification notification) {
        try {
            // 使用DynamicMailSenderService发送邮件
            dynamicMailSenderService.sendMail(
                null,  // 使用配置中的默认发件人地址
                notification.getRecipient(),
                notification.getSubject(),
                notification.getContent()
            );
            logger.debug("Email sent to {} via DynamicMailSenderService", notification.getRecipient());
        } catch (MailException e) {
            logger.error("Failed to send email: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 发送SMS通知
     */
    private void sendSms(Notification notification) {
        SmsService.SmsResult result = smsService.sendSms(notification.getRecipient(), notification.getContent());

        if (!result.isSuccess()) {
            throw new RuntimeException("SMS sending failed: " + result.getErrorMessage());
        }

        logger.debug("SMS sent to {} with message ID: {}", notification.getRecipient(), result.getMessageId());
    }

    /**
     * 发送Webhook通知
     */
    private void sendWebhook(Notification notification) {
        try {
            WebClient webClient = webClientBuilder.build();

            Mono<String> response = webClient.post()
                    .uri(notification.getRecipient()) // recipient存储URL
                    .bodyValue(createWebhookPayload(notification))
                    .retrieve()
                    .bodyToMono(String.class);

            response.block(); // 同步等待结果

            logger.debug("Webhook sent to {}", notification.getRecipient());

        } catch (Exception e) {
            logger.error("Failed to send webhook: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 发送Slack通知
     */
    private void sendSlack(Notification notification) {
        SlackService.SlackResult result = slackService.sendMessage(notification.getRecipient(), notification.getContent());

        if (!result.isSuccess()) {
            throw new RuntimeException("Slack message sending failed: " + result.getErrorMessage());
        }

        logger.debug("Slack message sent to webhook: {}", notification.getRecipient());
    }

    /**
     * 发送Teams通知
     */
    private void sendTeams(Notification notification) {
        TeamsService.TeamsResult result = teamsService.sendMessage(
                notification.getRecipient(),
                notification.getSubject() != null ? notification.getSubject() : "Alert Notification",
                notification.getContent()
        );

        if (!result.isSuccess()) {
            throw new RuntimeException("Teams message sending failed: " + result.getErrorMessage());
        }

        logger.debug("Teams message sent to webhook: {}", notification.getRecipient());
    }

    /**
     * 创建Webhook负载
     */
    private Object createWebhookPayload(Notification notification) {
        return new WebhookPayload(
                notification.getAlert().getId(),
                notification.getAlert().getTitle(),
                notification.getAlert().getDescription(),
                notification.getAlert().getSeverity().toString(),
                notification.getAlert().getThreatScore(),
                notification.getContent(),
                LocalDateTime.now()
        );
    }

    /**
     * 批量发送通知
     */
    @Async
    public void sendNotifications(List<Notification> notifications) {
        logger.info("Sending {} notifications", notifications.size());

        for (Notification notification : notifications) {
            try {
                sendNotification(notification);
                // 小延迟避免过载
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 重试失败的通知
     */
    public void retryFailedNotifications() {
        List<Notification> failedNotifications = notificationRepository.findNotificationsForRetry();

        logger.info("Retrying {} failed notifications", failedNotifications.size());

        for (Notification notification : failedNotifications) {
            if (notification.canRetry()) {
                logger.debug("Retrying notification {}", notification.getId());
                sendNotification(notification);
            }
        }
    }

    /**
     * 获取通知统计
     */
    @Transactional(readOnly = true)
    public NotificationStatistics getNotificationStatistics() {
        NotificationStatistics stats = new NotificationStatistics();

        stats.setTotalNotifications(notificationRepository.count());
        stats.setSuccessfulNotifications(notificationRepository.countSuccessfulNotifications());
        stats.setFailedNotifications(notificationRepository.countFailedNotifications());

        // 按通道统计
        List<Object[]> channelStats = notificationRepository.countNotificationsByChannel();
        for (Object[] stat : channelStats) {
            NotificationChannel channel = (NotificationChannel) stat[0];
            Long count = (Long) stat[1];
            stats.getByChannel().put(channel, count);
        }

        // 按状态统计
        List<Object[]> statusStats = notificationRepository.countNotificationsByStatus();
        for (Object[] stat : statusStats) {
            NotificationStatus status = (NotificationStatus) stat[0];
            Long count = (Long) stat[1];
            stats.getByStatus().put(status, count);
        }

        return stats;
    }

    /**
     * Webhook负载类
     */
    public static class WebhookPayload {
        private final Long alertId;
        private final String title;
        private final String description;
        private final String severity;
        private final Double threatScore;
        private final String content;
        private final LocalDateTime timestamp;

        public WebhookPayload(Long alertId, String title, String description,
                            String severity, Double threatScore, String content,
                            LocalDateTime timestamp) {
            this.alertId = alertId;
            this.title = title;
            this.description = description;
            this.severity = severity;
            this.threatScore = threatScore;
            this.content = content;
            this.timestamp = timestamp;
        }

        // Getters
        public Long getAlertId() { return alertId; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getSeverity() { return severity; }
        public Double getThreatScore() { return threatScore; }
        public String getContent() { return content; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}