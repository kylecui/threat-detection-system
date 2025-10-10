package com.threatdetection.alert.service.notification;

import com.threatdetection.alert.model.*;
import com.threatdetection.alert.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 通知服务测试
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private SmsService smsService;

    @Mock
    private SlackService slackService;

    @Mock
    private TeamsService teamsService;

    @InjectMocks
    private NotificationService notificationService;

    private Notification emailNotification;
    private Notification smsNotification;
    private Notification webhookNotification;
    private Notification slackNotification;
    private Notification teamsNotification;
    private Alert testAlert;

    @BeforeEach
    void setUp() {
        testAlert = Alert.builder()
                .id(1L)
                .title("Test Alert")
                .description("Test Description")
                .severity(AlertSeverity.HIGH)
                .build();

        emailNotification = new Notification(testAlert, NotificationChannel.EMAIL, "test@example.com");
        emailNotification.setSubject("Alert Notification");
        emailNotification.setContent("This is a test alert");

        smsNotification = new Notification(testAlert, NotificationChannel.SMS, "+1234567890");
        smsNotification.setContent("SMS Alert: Test");

        webhookNotification = new Notification(testAlert, NotificationChannel.WEBHOOK, "https://example.com/webhook");
        webhookNotification.setContent("Webhook Alert");

        slackNotification = new Notification(testAlert, NotificationChannel.SLACK, "https://hooks.slack.com/...");
        slackNotification.setContent("Webhook Alert");

        teamsNotification = new Notification(testAlert, NotificationChannel.TEAMS, "https://outlook.office.com/webhook/...");
        teamsNotification.setSubject("Teams Alert");
        teamsNotification.setContent("Teams Alert Content");
    }

    @Test
    void sendNotification_Email_ShouldSendSuccessfully() {
        // Given
        when(notificationRepository.save(any(Notification.class))).thenReturn(emailNotification);

        // When
        notificationService.sendNotification(emailNotification);

        // Then
        verify(mailSender).send(any(SimpleMailMessage.class));
        verify(notificationRepository).save(emailNotification);
    }

    @Test
    void sendNotification_Email_ShouldHandleFailure() {
        // Given
        when(notificationRepository.save(any(Notification.class))).thenReturn(emailNotification);
        doThrow(new MailException("SMTP error") {}).when(mailSender).send(any(SimpleMailMessage.class));

        // When
        notificationService.sendNotification(emailNotification);

        // Then
        verify(notificationRepository).save(emailNotification);
    }

    @Test
    void sendNotification_Sms_ShouldSendSuccessfully() {
        // Given
        when(smsService.sendSms(anyString(), anyString())).thenReturn(SmsService.SmsResult.success("msg123"));
        when(notificationRepository.save(any(Notification.class))).thenReturn(smsNotification);

        // When
        notificationService.sendNotification(smsNotification);

        // Then
        verify(smsService).sendSms("+1234567890", "SMS Alert: Test");
        verify(notificationRepository).save(smsNotification);
    }

    @Test
    void sendNotification_Slack_ShouldSendSuccessfully() {
        // Given
        when(slackService.sendMessage(anyString(), anyString())).thenReturn(SlackService.SlackResult.success());
        when(notificationRepository.save(any(Notification.class))).thenReturn(slackNotification);

        // When
        notificationService.sendNotification(slackNotification);

        // Then
        verify(slackService).sendMessage("https://hooks.slack.com/...", "Webhook Alert");
        verify(notificationRepository).save(slackNotification);
    }

    @Test
    void sendNotification_Teams_ShouldSendSuccessfully() {
        // Given
        when(teamsService.sendMessage(anyString(), anyString(), anyString())).thenReturn(TeamsService.TeamsResult.success());
        when(notificationRepository.save(any(Notification.class))).thenReturn(teamsNotification);

        // When
        notificationService.sendNotification(teamsNotification);

        // Then
        verify(teamsService).sendMessage("https://outlook.office.com/webhook/...", "Teams Alert", "Teams Alert Content");
        verify(notificationRepository).save(teamsNotification);
    }
}