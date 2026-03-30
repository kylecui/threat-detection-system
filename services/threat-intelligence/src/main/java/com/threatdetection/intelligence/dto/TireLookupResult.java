package com.threatdetection.intelligence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO mapping the TIRE (Threat Intelligence Reputation Engine) API response.
 *
 * <p>Maps the JSON returned by {@code GET /api/v1/ip/{ip}} from the TIRE service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TireLookupResult {

    /** The queried IP address */
    private String ip;

    /** Composite reputation score (0-100, higher = more malicious) */
    private Integer score;

    /** Risk level: Low, Medium, High, Critical */
    private String level;

    /** Human-readable verdict summary */
    private String verdict;

    /** Evidence items supporting the verdict */
    private List<Map<String, Object>> evidence;

    /** Whether this result was served from cache */
    private Boolean cached;

    /** Timestamp of the analysis */
    private String timestamp;
}
