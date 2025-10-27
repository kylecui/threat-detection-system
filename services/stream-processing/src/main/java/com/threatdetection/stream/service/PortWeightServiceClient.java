package com.threatdetection.stream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Port Weight Service Client for Flink Stream Processing
 * 
 * Purpose: Query customer-specific port weight configurations from threat-assessment service
 * Strategy: Hybrid port weight = max(configured weight, diversity weight)
 * 
 * Design Principles:
 * - Multi-tenant isolation: Always query with customerId
 * - Priority matching: Customer custom > Global default > Default(1.0)
 * - Batch optimization: Minimize HTTP calls by batching port queries
 * - Fallback: Use diversity weight if service unavailable
 * - Caching: Simple in-memory cache to reduce latency
 */
public class PortWeightServiceClient {
    
    private static final Logger logger = LoggerFactory.getLogger(PortWeightServiceClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final String serviceUrl;
    private final HttpClient httpClient;
    private final Map<String, Double> weightCache; // Simple cache: "customerId:port" -> weight
    
    /**
     * Constructor with service URL and cache configuration
     * 
     * @param serviceUrl Base URL of threat-assessment service (e.g., "http://threat-assessment:8082")
     * @param cacheTtlSeconds Cache time-to-live in seconds (default: 300 = 5 minutes)
     */
    public PortWeightServiceClient(String serviceUrl, int cacheTtlSeconds) {
        this.serviceUrl = serviceUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.weightCache = Collections.synchronizedMap(new LinkedHashMap<String, Double>(100, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Double> eldest) {
                return size() > 1000; // Max 1000 cache entries
            }
        });
        
        logger.info("PortWeightServiceClient initialized: serviceUrl={}, cacheTtl={}s", serviceUrl, cacheTtlSeconds);
    }
    
    /**
     * Constructor with default cache TTL (5 minutes)
     */
    public PortWeightServiceClient(String serviceUrl) {
        this(serviceUrl, 300); // Default 5 minutes cache
    }
    
    /**
     * Get port weights for a batch of ports (optimized for Flink processing)
     * 
     * @param customerId Customer ID for multi-tenant isolation
     * @param ports Set of port numbers to query
     * @return Map of port -> configured weight (or null if query failed)
     */
    public Map<Integer, Double> getPortWeightsBatch(String customerId, Set<Integer> ports) {
        if (customerId == null || customerId.isEmpty()) {
            logger.warn("CustomerId is null or empty, cannot query port weights");
            return null;
        }
        
        if (ports == null || ports.isEmpty()) {
            logger.debug("Port set is empty for customer {}", customerId);
            return Collections.emptyMap();
        }
        
        try {
            // Build batch query request
            String endpoint = String.format("%s/api/customer-port-weights/%s/batch", serviceUrl, customerId);
            String requestBody = objectMapper.writeValueAsString(Map.of("portNumbers", ports));
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(3))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            logger.debug("Querying port weights for customer {}: {} ports", customerId, ports.size());
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                logger.warn("Failed to query port weights: customerId={}, statusCode={}, response={}", 
                           customerId, response.statusCode(), response.body());
                return null;
            }
            
            // Parse response: Map<Integer, Double>
            JsonNode rootNode = objectMapper.readTree(response.body());
            Map<Integer, Double> weightMap = new HashMap<>();
            
            rootNode.fields().forEachRemaining(entry -> {
                try {
                    int port = Integer.parseInt(entry.getKey());
                    double weight = entry.getValue().asDouble();
                    weightMap.put(port, weight);
                    
                    // Update cache
                    String cacheKey = customerId + ":" + port;
                    weightCache.put(cacheKey, weight);
                } catch (NumberFormatException e) {
                    logger.error("Invalid port number in response: {}", entry.getKey());
                }
            });
            
            logger.debug("Retrieved port weights for customer {}: {} weights", customerId, weightMap.size());
            return weightMap;
            
        } catch (IOException e) {
            logger.error("IOException while querying port weights: customerId={}, error={}", customerId, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            logger.error("Interrupted while querying port weights: customerId={}", customerId);
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error querying port weights: customerId={}, error={}", customerId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get single port weight (with cache lookup)
     * 
     * @param customerId Customer ID
     * @param port Port number
     * @return Configured weight or null if not found
     */
    public Double getPortWeight(String customerId, int port) {
        // Check cache first
        String cacheKey = customerId + ":" + port;
        Double cachedWeight = weightCache.get(cacheKey);
        if (cachedWeight != null) {
            return cachedWeight;
        }
        
        // Query from service
        Map<Integer, Double> result = getPortWeightsBatch(customerId, Set.of(port));
        if (result != null && result.containsKey(port)) {
            return result.get(port);
        }
        
        return null;
    }
    
    /**
     * Calculate hybrid port weight: max(configured weight, diversity weight)
     * 
     * Strategy:
     * 1. Query configured weights from customer_port_weights (via API)
     * 2. Calculate diversity weight based on unique port count
     * 3. Return maximum of the two for each port
     * 4. If no configured weight found, use diversity weight as fallback
     * 
     * @param customerId Customer ID
     * @param uniquePorts Set of unique ports attacked
     * @return Hybrid port weight (max of config and diversity)
     */
    public double calculateHybridPortWeight(String customerId, Set<Integer> uniquePorts) {
        if (uniquePorts == null || uniquePorts.isEmpty()) {
            return 1.0; // Default weight for no ports
        }
        
        // Calculate diversity weight based on port count
        double diversityWeight = calculateDiversityWeight(uniquePorts.size());
        
        // Query configured weights
        Map<Integer, Double> configuredWeights = getPortWeightsBatch(customerId, uniquePorts);
        
        if (configuredWeights == null || configuredWeights.isEmpty()) {
            // Fallback: Use diversity weight if service unavailable or no configs
            logger.debug("No configured port weights for customer {}, using diversity weight: {}", 
                        customerId, diversityWeight);
            return diversityWeight;
        }
        
        // Calculate max configured weight across all ports
        double maxConfiguredWeight = configuredWeights.values().stream()
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(1.0);
        
        // Hybrid strategy: max(configured, diversity)
        double hybridWeight = Math.max(maxConfiguredWeight, diversityWeight);
        
        logger.debug("Hybrid port weight for customer {}: configured={}, diversity={}, hybrid={}", 
                    customerId, maxConfiguredWeight, diversityWeight, hybridWeight);
        
        return hybridWeight;
    }
    
    /**
     * Calculate diversity weight based on unique port count
     * Aligned with StreamProcessingJob.ThreatScoreCalculator.calculatePortWeight()
     * 
     * @param uniquePortCount Number of unique ports
     * @return Diversity weight (1.0 - 2.0)
     */
    private double calculateDiversityWeight(int uniquePortCount) {
        if (uniquePortCount <= 1) return 1.0;      // Single port - basic scan
        else if (uniquePortCount <= 5) return 1.2;  // Few ports - targeted scan
        else if (uniquePortCount <= 10) return 1.5; // Moderate ports - broader scan
        else if (uniquePortCount <= 20) return 1.8; // Many ports - comprehensive scan
        else return 2.0; // Very high port diversity - sophisticated attack
    }
    
    /**
     * Clear cache (for testing or manual refresh)
     */
    public void clearCache() {
        weightCache.clear();
        logger.info("Port weight cache cleared");
    }
    
    /**
     * Get cache size (for monitoring)
     */
    public int getCacheSize() {
        return weightCache.size();
    }
}
