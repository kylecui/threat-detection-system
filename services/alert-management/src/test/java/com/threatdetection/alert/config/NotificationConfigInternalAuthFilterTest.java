package com.threatdetection.alert.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotificationConfigInternalAuthFilterTest {

    private static final String TOKEN = "a".repeat(64);

    @Test
    void rejectsMissingCredentialOnProtectedEndpoint() throws Exception {
        NotificationConfigInternalAuthFilter filter = new NotificationConfigInternalAuthFilter(TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notification-config/smtp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsInvalidOrAmbiguousCredentials() throws Exception {
        NotificationConfigInternalAuthFilter filter = new NotificationConfigInternalAuthFilter(TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/notification-config/customer/acme/test");
        request.addHeader(NotificationConfigInternalAuthFilter.INTERNAL_TOKEN_HEADER, TOKEN);
        request.addHeader(NotificationConfigInternalAuthFilter.INTERNAL_TOKEN_HEADER, TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void acceptsExactlyOneValidCredential() throws Exception {
        NotificationConfigInternalAuthFilter filter = new NotificationConfigInternalAuthFilter(TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notification-config/smtp/default");
        request.addHeader(NotificationConfigInternalAuthFilter.INTERNAL_TOKEN_HEADER, TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotProtectHealthOrAlertApi() throws Exception {
        NotificationConfigInternalAuthFilter filter = new NotificationConfigInternalAuthFilter(TOKEN);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void failsClosedWhenConfiguredTokenIsWeak() {
        assertThrows(IllegalStateException.class,
                () -> new NotificationConfigInternalAuthFilter("too-short"));
    }
}
