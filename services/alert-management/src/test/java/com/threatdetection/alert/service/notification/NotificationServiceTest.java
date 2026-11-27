package com.threatdetection.alert.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.alert.model.Alert;
import com.threatdetection.alert.model.AlertSeverity;
import com.threatdetection.alert.model.Notification;
import com.threatdetection.alert.model.NotificationChannel;
import com.threatdetection.alert.model.NotificationStatus;
import com.threatdetection.alert.repository.CustomerNotificationConfigRepository;
import com.threatdetection.alert.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private CustomerNotificationConfigRepository customerNotificationConfigRepository;
    @Mock private DynamicMailSenderService dynamicMailSenderService;
    @Mock private WebClient.Builder webClientBuilder;
    @Mock private SmsServiceRouter smsServiceRouter;
    @Mock private SmsService smsService;
    @Mock private SlackService slackService;
    @Mock private TeamsService teamsService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationService notificationService;

    private Notification emailNotification;

    @BeforeEach
    void setUp() {
        Alert alert = Alert.builder()
                .id(1L)
                .title("Test Alert")
                .description("Test Description")
                .severity(AlertSeverity.HIGH)
                .build();
        emailNotification = new Notification(alert, NotificationChannel.EMAIL, "test@example.com");
        emailNotification.setId(11L);
        emailNotification.setSubject("Alert Notification");
        emailNotification.setContent("This is a test alert");
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void emailDeliveryMarksNotificationSent() {
        notificationService.sendNotification(emailNotification);

        verify(dynamicMailSenderService).sendMail(
                null, "test@example.com", "Alert Notification", "This is a test alert");
        assertEquals(NotificationStatus.SENT, emailNotification.getStatus());
    }

    @Test
    void failedDeliverySchedulesRetry() {
        doThrow(new IllegalStateException("SMTP error"))
                .when(dynamicMailSenderService)
                .sendMail(null, "test@example.com", "Alert Notification", "This is a test alert");

        notificationService.sendNotification(emailNotification);

        assertEquals(NotificationStatus.RETRYING, emailNotification.getStatus());
        assertEquals(0, emailNotification.getRetryCount());
        verify(notificationRepository).save(emailNotification);
    }

    @Test
    void smsUsesConfiguredProviderRouter() {
        Notification sms = new Notification(
                emailNotification.getAlert(), NotificationChannel.SMS, "+8613800000000");
        sms.setId(12L);
        sms.setContent("test");
        when(smsServiceRouter.currentProvider()).thenReturn(smsService);
        when(smsService.sendSms("+8613800000000", "test"))
                .thenReturn(SmsService.SmsResult.success("message-id"));

        notificationService.sendNotification(sms);

        verify(smsService).sendSms("+8613800000000", "test");
        assertEquals(NotificationStatus.SENT, sms.getStatus());
    }
}
