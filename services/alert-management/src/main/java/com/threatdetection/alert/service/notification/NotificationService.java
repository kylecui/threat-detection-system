package com.threatdetection.alert.service.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.alert.model.Alert;
import com.threatdetection.alert.model.CustomerNotificationConfig;
import com.threatdetection.alert.model.Notification;
import com.threatdetection.alert.model.NotificationChannel;
import com.threatdetection.alert.model.NotificationStatus;
import com.threatdetection.alert.repository.CustomerNotificationConfigRepository;
import com.threatdetection.alert.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Sends, records and retries multi-channel notifications. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final CustomerNotificationConfigRepository customerNotificationConfigRepository;
    private final DynamicMailSenderService dynamicMailSenderService;
    private final WebClient.Builder webClientBuilder;
    private final SmsServiceRouter smsServiceRouter;
    private final SlackService slackService;
    private final TeamsService teamsService;
    private final ObjectMapper objectMapper;

    @Value("${alert.notification.retry-delay:30}")
    private long retryDelaySeconds;

    /** Queues an initial delivery on the configured async executor. */
    @Async("notificationTaskExecutor")
    public void sendNotification(Notification notification) {
        deliverAndPersist(notification, false);
    }

    /**
     * Performs a real delivery without creating a retry job. This is used by
     * the configuration test endpoint so the response represents the actual
     * provider result rather than a simulated success.
     */
    public NotificationDeliveryResult testNotification(Notification notification) {
        try {
            dispatch(notification);
            return NotificationDeliveryResult.success("Delivered successfully");
        } catch (Exception exception) {
            logger.warn("Notification test failed via {}: {}",
                    notification.getChannel(), exception.getMessage());
            return NotificationDeliveryResult.failure(safeError(exception));
        }
    }

    private NotificationDeliveryResult deliverAndPersist(Notification notification, boolean retryAttempt) {
        logger.info("Sending notification via {} to {}", notification.getChannel(), notification.getRecipient());
        if (notification.getId() == null) {
            notification.setStatus(NotificationStatus.PENDING);
            notification = notificationRepository.save(notification);
        }
        if (retryAttempt) {
            notification.incrementRetryCount();
            notification.setStatus(NotificationStatus.PENDING);
            notification.setNextRetryAt(null);
        }

        try {
            dispatch(notification);
            notification.markAsSent();
            notificationRepository.save(notification);
            logger.info("Notification {} sent successfully", notification.getId());
            return NotificationDeliveryResult.success("Delivered successfully");
        } catch (Exception exception) {
            String error = safeError(exception);
            notification.markAsFailed(error);
            if (notification.canRetry()) {
                notification.setStatus(NotificationStatus.RETRYING);
                notification.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.max(1, retryDelaySeconds)));
            } else {
                notification.setStatus(NotificationStatus.FAILED);
                notification.setNextRetryAt(null);
            }
            notificationRepository.save(notification);
            logger.error("Notification {} delivery failed (retry {}/{}): {}",
                    notification.getId(), notification.getRetryCount(), notification.getMaxRetries(), error);
            return NotificationDeliveryResult.failure(error);
        }
    }

    private void dispatch(Notification notification) {
        switch (notification.getChannel()) {
            case EMAIL -> sendEmail(notification);
            case SMS -> sendSms(notification);
            case WEBHOOK -> sendWebhook(notification);
            case SLACK -> sendSlack(notification);
            case TEAMS -> sendTeams(notification);
            default -> throw new UnsupportedOperationException(
                    "Unsupported channel: " + notification.getChannel());
        }
    }

    public Notification createNotification(Notification notification) {
        if (notification.getStatus() == null) {
            notification.setStatus(NotificationStatus.PENDING);
        }
        return notificationRepository.save(notification);
    }

    private void sendEmail(Notification notification) {
        dynamicMailSenderService.sendMail(
                null,
                notification.getRecipient(),
                notification.getSubject(),
                notification.getContent());
    }

    private void sendSms(Notification notification) {
        SmsService.SmsResult result = smsServiceRouter.currentProvider()
                .sendSms(notification.getRecipient(), notification.getContent());
        if (!result.isSuccess()) {
            throw new IllegalStateException("SMS sending failed via "
                    + smsServiceRouter.currentProviderName() + ": " + result.getErrorMessage());
        }
    }

    private void sendWebhook(Notification notification) {
        Map<String, String> configuredHeaders = resolveWebhookHeaders(notification.getCustomerId());
        webClientBuilder.build()
                .post()
                .uri(notification.getRecipient())
                .headers(headers -> configuredHeaders.forEach(headers::set))
                .bodyValue(createWebhookPayload(notification))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private Map<String, String> resolveWebhookHeaders(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return Collections.emptyMap();
        }
        return customerNotificationConfigRepository.findByCustomerIdAndIsActive(customerId, true)
                .map(CustomerNotificationConfig::getWebhookHeaders)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> {
                    try {
                        return objectMapper.readValue(value, new TypeReference<Map<String, String>>() { });
                    } catch (Exception exception) {
                        throw new IllegalStateException("Invalid webhook headers configuration", exception);
                    }
                })
                .orElseGet(Collections::emptyMap);
    }

    private void sendSlack(Notification notification) {
        SlackService.SlackResult result = slackService.sendMessage(
                notification.getRecipient(), notification.getContent());
        if (!result.isSuccess()) {
            throw new IllegalStateException("Slack message sending failed: " + result.getErrorMessage());
        }
    }

    private void sendTeams(Notification notification) {
        TeamsService.TeamsResult result = teamsService.sendMessage(
                notification.getRecipient(),
                notification.getSubject() != null ? notification.getSubject() : "Alert Notification",
                notification.getContent());
        if (!result.isSuccess()) {
            throw new IllegalStateException("Teams message sending failed: " + result.getErrorMessage());
        }
    }

    private Object createWebhookPayload(Notification notification) {
        Alert alert = notification.getAlert();
        return new WebhookPayload(
                alert == null ? null : alert.getId(),
                alert == null ? notification.getSubject() : alert.getTitle(),
                alert == null ? "Notification configuration test" : alert.getDescription(),
                alert == null || alert.getSeverity() == null ? "TEST" : alert.getSeverity().toString(),
                alert == null ? null : alert.getThreatScore(),
                notification.getContent(),
                LocalDateTime.now());
    }

    @Async("notificationTaskExecutor")
    public void sendNotifications(List<Notification> notifications) {
        for (Notification notification : notifications) {
            deliverAndPersist(notification, false);
        }
    }

    /** Polls due RETRYING rows; exhausted deliveries end in FAILED. */
    @Scheduled(
            fixedDelayString = "${alert.notification.retry-scan-delay-ms:30000}",
            initialDelayString = "${alert.notification.retry-scan-initial-delay-ms:30000}")
    public void retryFailedNotifications() {
        List<Notification> dueNotifications =
                notificationRepository.findNotificationsForRetry(LocalDateTime.now());
        if (!dueNotifications.isEmpty()) {
            logger.info("Retrying {} due notifications", dueNotifications.size());
        }
        for (Notification notification : dueNotifications) {
            deliverAndPersist(notification, true);
        }
    }

    @Transactional(readOnly = true)
    public NotificationStatistics getNotificationStatistics() {
        NotificationStatistics stats = new NotificationStatistics();
        stats.setTotalNotifications(notificationRepository.count());
        stats.setSuccessfulNotifications(notificationRepository.countSuccessfulNotifications());
        stats.setFailedNotifications(notificationRepository.countFailedNotifications());

        for (Object[] stat : notificationRepository.countNotificationsByChannel()) {
            stats.getByChannel().put((NotificationChannel) stat[0], (Long) stat[1]);
        }
        for (Object[] stat : notificationRepository.countNotificationsByStatus()) {
            stats.getByStatus().put((NotificationStatus) stat[0], (Long) stat[1]);
        }
        return stats;
    }

    private String safeError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    public record NotificationDeliveryResult(boolean success, String message) {
        public static NotificationDeliveryResult success(String message) {
            return new NotificationDeliveryResult(true, message);
        }

        public static NotificationDeliveryResult failure(String message) {
            return new NotificationDeliveryResult(false, message);
        }
    }

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

        public Long getAlertId() { return alertId; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getSeverity() { return severity; }
        public Double getThreatScore() { return threatScore; }
        public String getContent() { return content; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
