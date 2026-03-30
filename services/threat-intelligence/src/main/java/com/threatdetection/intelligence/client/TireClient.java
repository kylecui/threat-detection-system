package com.threatdetection.intelligence.client;

import com.threatdetection.intelligence.dto.TireLookupResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * REST client for the TIRE (Threat Intelligence Reputation Engine) service.
 *
 * <p>Calls TIRE's IP lookup endpoint to enrich threat intelligence data
 * with multi-source reputation scoring.
 *
 * <p>Designed to fail gracefully — returns {@code null} on any error
 * so that the core lookup flow is never blocked by TIRE unavailability.
 */
@Component
public class TireClient {

    private static final Logger log = LoggerFactory.getLogger(TireClient.class);

    private final RestTemplate restTemplate;
    private final String tireBaseUrl;

    public TireClient(
            RestTemplate restTemplate,
            @Value("${tire.base-url:http://tire:8000}") String tireBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.tireBaseUrl = tireBaseUrl;
    }

    /**
     * Look up an IP address against TIRE's multi-source reputation engine.
     *
     * @param ip the IPv4/IPv6 address to query
     * @return TireLookupResult with score/level/verdict, or {@code null} on failure
     */
    public TireLookupResult lookupIp(String ip) {
        String url = tireBaseUrl + "/api/v1/ip/" + ip;
        try {
            ResponseEntity<TireLookupResult> response = restTemplate.getForEntity(url, TireLookupResult.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("TIRE lookup succeeded for ip={} score={} level={}",
                        ip, response.getBody().getScore(), response.getBody().getLevel());
                return response.getBody();
            }
            log.warn("TIRE lookup returned non-success status={} for ip={}", response.getStatusCode(), ip);
            return null;
        } catch (RestClientException e) {
            log.warn("TIRE lookup failed for ip={}: {}", ip, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error during TIRE lookup for ip={}", ip, e);
            return null;
        }
    }

    /**
     * Check if the TIRE service is reachable.
     *
     * @return true if TIRE /healthz returns 200
     */
    public boolean isAvailable() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(tireBaseUrl + "/healthz", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
