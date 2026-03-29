package com.threatdetection.stream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NetWeightServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(NetWeightServiceClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String serviceUrl;
    private final HttpClient httpClient;
    private final Map<String, List<CidrWeight>> weightCache;

    public NetWeightServiceClient(String serviceUrl) {
        this.serviceUrl = serviceUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.weightCache = Collections.synchronizedMap(new LinkedHashMap<String, List<CidrWeight>>(100, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<CidrWeight>> eldest) {
                return size() > 500;
            }
        });

        logger.info("NetWeightServiceClient initialized: serviceUrl={}", serviceUrl);
    }

    public double getNetWeight(String customerId, String ip) {
        if (customerId == null || customerId.isEmpty()) {
            logger.warn("CustomerId is null or empty, using default netWeight=1.0");
            return 1.0;
        }

        if (ip == null || ip.isEmpty()) {
            logger.debug("IP is null or empty for customer {}, using default netWeight=1.0", customerId);
            return 1.0;
        }

        List<CidrWeight> cidrWeights = weightCache.get(customerId);
        if (cidrWeights == null) {
            cidrWeights = fetchNetWeights(customerId);
            if (cidrWeights == null) {
                return 1.0;
            }
            weightCache.put(customerId, cidrWeights);
        }

        CidrWeight bestMatch = null;
        int bestPrefix = -1;

        for (CidrWeight cidrWeight : cidrWeights) {
            if (containsIp(cidrWeight.cidr(), ip) && cidrWeight.prefixLength() > bestPrefix) {
                bestMatch = cidrWeight;
                bestPrefix = cidrWeight.prefixLength();
            }
        }

        if (bestMatch != null) {
            return bestMatch.weight();
        }

        logger.debug("No CIDR matched IP {} for customer {}, using default netWeight=1.0", ip, customerId);
        return 1.0;
    }

    private List<CidrWeight> fetchNetWeights(String customerId) {
        try {
            String endpoint = String.format("%s/api/v1/customers/%s/net-weights", serviceUrl, customerId);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

            logger.debug("Querying net segment weights for customer {}", customerId);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warn("Failed to query net weights: customerId={}, statusCode={}, response={}",
                    customerId, response.statusCode(), response.body());
                return null;
            }

            JsonNode rootNode = objectMapper.readTree(response.body());
            List<CidrWeight> result = new ArrayList<>();

            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    String cidr = node.path("cidr").asText(null);
                    if (cidr == null || cidr.isEmpty()) {
                        continue;
                    }

                    Integer prefixLength = parsePrefixLength(cidr);
                    if (prefixLength == null) {
                        logger.warn("Invalid CIDR in net-weight response: customerId={}, cidr={}", customerId, cidr);
                        continue;
                    }

                    double weight = node.path("weight").asDouble(1.0);
                    result.add(new CidrWeight(cidr, weight, prefixLength));
                }
            }

            logger.debug("Retrieved net segment weights for customer {}: {} entries", customerId, result.size());
            return result;

        } catch (IOException e) {
            logger.error("IOException while querying net weights: customerId={}, error={}", customerId, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            logger.error("Interrupted while querying net weights: customerId={}", customerId);
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            logger.error("Unexpected error querying net weights: customerId={}, error={}", customerId, e.getMessage(), e);
            return null;
        }
    }

    private Integer parsePrefixLength(String cidr) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            return null;
        }

        try {
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) {
                return null;
            }
            return prefix;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean containsIp(String cidr, String ip) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }

            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) {
                return false;
            }

            byte[] ipBytes = InetAddress.getByName(ip).getAddress();
            byte[] networkBytes = InetAddress.getByName(parts[0]).getAddress();

            if (ipBytes.length != 4 || networkBytes.length != 4) {
                return false;
            }

            int ipInt = bytesToInt(ipBytes);
            int networkInt = bytesToInt(networkBytes);

            int mask = prefix == 0 ? 0 : (-1 << (32 - prefix));
            return (ipInt & mask) == (networkInt & mask);
        } catch (Exception e) {
            logger.debug("Failed CIDR match check: cidr={}, ip={}, error={}", cidr, ip, e.getMessage());
            return false;
        }
    }

    private int bytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24)
            | ((bytes[1] & 0xFF) << 16)
            | ((bytes[2] & 0xFF) << 8)
            | (bytes[3] & 0xFF);
    }

    public void clearCache() {
        weightCache.clear();
        logger.info("Net weight cache cleared");
    }

    public int getCacheSize() {
        return weightCache.size();
    }

    private record CidrWeight(String cidr, double weight, int prefixLength) {
    }
}
