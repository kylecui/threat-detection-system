package com.threatdetection.alert.controller;

import com.threatdetection.alert.dto.SmtpConfigRequest;
import com.threatdetection.alert.dto.SmtpConfigResponse;
import com.threatdetection.alert.model.SmtpConfig;
import com.threatdetection.alert.repository.CustomerNotificationConfigRepository;
import com.threatdetection.alert.repository.SmtpConfigRepository;
import com.threatdetection.alert.service.notification.DynamicMailSenderService;
import com.threatdetection.alert.service.SecretEncryptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationConfigControllerTest {

    @Mock
    private SmtpConfigRepository smtpConfigRepository;

    @Mock
    private CustomerNotificationConfigRepository customerNotificationConfigRepository;

    @Mock
    private DynamicMailSenderService dynamicMailSenderService;

    @Mock
    private SecretEncryptionService secretEncryptionService;

    @InjectMocks
    private NotificationConfigController controller;

    @Test
    void emptyDefaultIsANormalUnconfiguredState() {
        when(smtpConfigRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());

        var response = controller.getDefaultSmtpConfig();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isConfigured());
        assertFalse(response.getBody().isPasswordConfigured());
    }

    @Test
    void responseExposesOnlyWhetherAPasswordExists() {
        SmtpConfig config = SmtpConfig.builder()
                .id(7L)
                .configName("default")
                .host("smtp.example.com")
                .port(587)
                .username("sender")
                .password("do-not-return-this-secret")
                .fromAddress("sender@example.com")
                .isDefault(true)
                .isActive(true)
                .build();
        when(smtpConfigRepository.findByIsDefaultTrue()).thenReturn(Optional.of(config));

        SmtpConfigResponse body = controller.getDefaultSmtpConfig().getBody();

        assertNotNull(body);
        assertTrue(body.isConfigured());
        assertTrue(body.isPasswordConfigured());
        assertEquals("smtp.example.com", body.getHost());
    }

    @Test
    void firstDefaultSaveCreatesTheSingletonAndRefreshesTheSender() {
        SmtpConfigRequest request = new SmtpConfigRequest();
        request.setHost("smtp.example.com");
        request.setPort(587);
        request.setUsername("sender");
        request.setPassword("smtp-secret");
        request.setFromAddress("sender@example.com");
        request.setEncryption("STARTTLS");

        when(smtpConfigRepository.findByIsDefaultTrue()).thenReturn(Optional.empty());
        when(secretEncryptionService.encrypt("smtp-secret")).thenReturn("enc:v1:test:test");
        when(smtpConfigRepository.save(any(SmtpConfig.class))).thenAnswer(invocation -> {
            SmtpConfig saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        var response = controller.upsertDefaultSmtpConfig(request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isConfigured());
        assertTrue(response.getBody().isPasswordConfigured());
        assertEquals("STARTTLS", response.getBody().getEncryption());
        verify(secretEncryptionService).encrypt("smtp-secret");
        verify(dynamicMailSenderService).refreshCache();
    }
}
