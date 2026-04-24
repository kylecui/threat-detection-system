package com.threatdetection.intelligence.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 *
 * <p>TIRE response structure:
 * <pre>{@code
 * {
 *   "object": {"type": "ip", "value": "8.8.8.8"},
 *   "analysis": {
 *     "reputation_score": 0,
 *     "contextual_score": 0,
 *     "final_score": 0,
 *     "level": "Low",
 *     "confidence": 0.0,
 *     "decision": "allow_with_monitoring"
 *   },
 *   "summary": "IP 8.8.8.8 shows minimal threat indicators...",
 *   "tags": [],
 *   "evidence": [],
 *   "metadata": {"generated_by": "...", "version": "2.0.0"}
 * }
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TireLookupResult {

    private ObjectInfo object;

    private Analysis analysis;

    private String summary;

    private List<String> tags;

    private List<Map<String, Object>> evidence;

    private Map<String, Object> metadata;

    // --- Convenience accessors for the enrichment layer ---

    /**
     * Get the final composite score (0-100).
     */
    public Integer getScore() {
        if (analysis == null) return null;
        return analysis.getFinalScore() != null ? analysis.getFinalScore().intValue() : null;
    }

    /**
     * Get the risk level (Low, Medium, High, Critical).
     */
    public String getLevel() {
        return analysis != null ? analysis.getLevel() : null;
    }

    /**
     * Get the human-readable decision/verdict.
     */
    public String getVerdict() {
        return summary;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ObjectInfo {
        private String type;
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Analysis {
        private Double reputationScore;

        private Double contextualScore;

        private Double finalScore;

        private String level;

        private Double confidence;

        private String decision;

        public Double getReputationScore() {
            return reputationScore;
        }

        public Double getContextualScore() {
            return contextualScore;
        }

        public Double getFinalScore() {
            return finalScore;
        }

        public String getLevel() {
            return level;
        }
    }
}
