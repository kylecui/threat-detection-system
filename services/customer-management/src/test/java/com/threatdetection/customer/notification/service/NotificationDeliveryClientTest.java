package com.threatdetection.customer.notification.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NotificationDeliveryClientTest {

    private static final String TOKEN = "b".repeat(64);

    @Test
    void sendsInternalCredentialWhenDelegatingRealNotificationTest() {
        NotificationDeliveryClient client = new NotificationDeliveryClient(
                new RestTemplateBuilder(), "http://localhost:8082", TOKEN);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(
                        "http://localhost:8082/api/notification-config/customer/customer-1/test"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", TOKEN))
                .andRespond(withSuccess("{\"tested\":true}", MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.testCustomerConfig("customer-1");

        assertEquals(Boolean.TRUE, result.get("tested"));
        server.verify();
    }

    @Test
    void failsClosedWhenInternalCredentialIsWeak() {
        assertThrows(IllegalStateException.class, () -> new NotificationDeliveryClient(
                new RestTemplateBuilder(), "http://localhost:8082", "weak"));
    }
}
