package com.threatdetection.alert.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Requires a service credential for notification configuration endpoints.
 *
 * <p>The API gateway and customer-management service add this header on
 * server-to-server requests. Requiring it here prevents callers from bypassing
 * gateway JWT/RBAC checks by connecting to alert-management's published port.
 */
@Component
public class NotificationConfigInternalAuthFilter extends OncePerRequestFilter {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String PROTECTED_PREFIX = "/api/notification-config";

    private final byte[] expectedToken;

    public NotificationConfigInternalAuthFilter(
            @Value("${security.alert-service-token}") String configuredToken) {
        if (configuredToken == null || configuredToken.length() < 64) {
            throw new IllegalStateException(
                    "ALERT_INTERNAL_SERVICE_TOKEN must contain at least 64 characters");
        }
        this.expectedToken = configuredToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals(PROTECTED_PREFIX) || path.startsWith(PROTECTED_PREFIX + "/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        List<String> tokens = java.util.Collections.list(request.getHeaders(INTERNAL_TOKEN_HEADER));
        if (tokens.size() != 1 || !matches(tokens.get(0))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            response.getWriter().write(
                    "{\"error\":\"Unauthorized\",\"message\":\"Valid internal service credentials are required\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matches(String suppliedToken) {
        if (suppliedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedToken,
                suppliedToken.getBytes(StandardCharsets.UTF_8));
    }
}
