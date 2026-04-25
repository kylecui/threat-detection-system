package com.threatdetection.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated system health endpoint for gateway + downstream services.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemHealthController {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthController.class);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient.Builder webClientBuilder;

    public SystemHealthController(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Value("${gateway.health-check.data-ingestion-url:http://localhost:8080/actuator/health}")
    private String dataIngestionHealthUrl;

    @Value("${gateway.health-check.stream-processing-url:http://localhost:8081/overview}")
    private String streamProcessingHealthUrl;

    @Value("${gateway.health-check.threat-assessment-url:http://localhost:8083/api/v1/assessment/health}")
    private String threatAssessmentHealthUrl;

    @Value("${gateway.health-check.alert-management-url:http://localhost:8082/actuator/health}")
    private String alertManagementHealthUrl;

    @Value("${gateway.health-check.customer-management-url:http://localhost:8084/actuator/health}")
    private String customerManagementHealthUrl;

    @Value("${gateway.health-check.threat-intelligence-url:http://localhost:8085/actuator/health}")
    private String threatIntelligenceHealthUrl;

    @Value("${gateway.health-check.ml-detection-url:http://localhost:8086/health}")
    private String mlDetectionHealthUrl;

    @Value("${gateway.health-check.kafka-bootstrap:${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}}")
    private String kafkaBootstrap;

    @GetMapping("/health")
    public Mono<Map<String, Object>> getSystemHealth() {
        List<ServiceTarget> targets = List.of(
                new ServiceTarget("data-ingestion", dataIngestionHealthUrl),
                new ServiceTarget("stream-processing", streamProcessingHealthUrl),
                new ServiceTarget("threat-assessment", threatAssessmentHealthUrl),
                new ServiceTarget("alert-management", alertManagementHealthUrl),
                new ServiceTarget("customer-management", customerManagementHealthUrl),
                new ServiceTarget("threat-intelligence", threatIntelligenceHealthUrl),
                new ServiceTarget("ml-detection", mlDetectionHealthUrl)
        );

        Mono<List<ServiceCheckResult>> serviceChecks = Flux.fromIterable(targets)
                .flatMap(this::checkServiceHealth)
                .collectList();

        Mono<String> kafkaCheck = checkKafkaHealth();

        return Mono.zip(serviceChecks, kafkaCheck)
                .map(tuple -> buildResponse(tuple.getT1(), tuple.getT2()));
    }

    private Mono<ServiceCheckResult> checkServiceHealth(ServiceTarget target) {
        long startedAt = System.nanoTime();
        WebClient webClient = webClientBuilder.build();

        return webClient.get()
                .uri(target.url())
                .exchangeToMono(response -> toResultFromResponse(target, response.statusCode(), response.bodyToMono(Map.class), startedAt))
                .timeout(HEALTH_TIMEOUT)
                .onErrorResume(ex -> {
                    long latencyMs = toLatencyMs(startedAt);
                    log.warn("Health check failed for {} ({}): {}", target.name(), target.url(), ex.getMessage());
                    return Mono.just(new ServiceCheckResult(target.name(), "DOWN", latencyMs, Map.of()));
                });
    }

    private Mono<ServiceCheckResult> toResultFromResponse(
            ServiceTarget target,
            HttpStatusCode statusCode,
            Mono<Map> bodyMono,
            long startedAt
    ) {
        if (!statusCode.is2xxSuccessful()) {
            return Mono.just(new ServiceCheckResult(target.name(), "DOWN", toLatencyMs(startedAt), Map.of()));
        }

        return bodyMono
                .defaultIfEmpty(Map.of())
                .map(body -> {
                    long latencyMs = toLatencyMs(startedAt);
                    String serviceStatus = resolveServiceStatus(target.name(), body);
                    return new ServiceCheckResult(target.name(), serviceStatus, latencyMs, body);
                })
                .onErrorResume(ex -> Mono.just(new ServiceCheckResult(target.name(), "DOWN", toLatencyMs(startedAt), Map.of())));
    }

    private Map<String, Object> buildResponse(List<ServiceCheckResult> results, String kafkaStatus) {
        Map<String, Object> services = new LinkedHashMap<>();

        for (ServiceCheckResult result : results) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("status", result.status());
            detail.put("latencyMs", result.latencyMs());
            services.put(result.name(), detail);
        }

        services.put("kafka", Map.of("status", kafkaStatus));
        services.put("redis", Map.of("status", resolveInfraStatus(results, "redis")));
        services.put("postgres", Map.of("status", resolveInfraStatus(results, "postgres")));

        int downCount = (int) services.values().stream()
                .filter(value -> value instanceof Map<?, ?> map && "DOWN".equals(String.valueOf(map.get("status"))))
                .count();
        int totalCount = services.size();

        String overallStatus;
        if (downCount == 0) {
            overallStatus = "healthy";
        } else if (downCount > totalCount / 2.0) {
            overallStatus = "unhealthy";
        } else {
            overallStatus = "degraded";
        }

        Map<String, Object> pipeline = new LinkedHashMap<>();
        pipeline.put("lastEventReceived", null);
        pipeline.put("eventsLastHour", null);
        pipeline.put("kafkaLag", null);
        pipeline.put("flinkRunning", null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", overallStatus);
        response.put("timestamp", Instant.now().toString());
        response.put("services", services);
        response.put("pipeline", pipeline);
        return response;
    }

    private String resolveServiceStatus(String serviceName, Map<?, ?> body) {
        if ("stream-processing".equals(serviceName)) {
            return "UP";
        }

        Object rawStatus = body.get("status");
        if (rawStatus == null) {
            return body.isEmpty() ? "DOWN" : "UP";
        }

        String status = String.valueOf(rawStatus).toUpperCase();
        return ("UP".equals(status) || "HEALTHY".equals(status) || "OK".equals(status) || "RUNNING".equals(status))
                ? "UP"
                : "DOWN";
    }

    private String resolveInfraStatus(List<ServiceCheckResult> results, String infraName) {
        boolean infraUp = results.stream().anyMatch(result -> hasInfraUp(result.body(), infraName));
        return infraUp ? "UP" : "DOWN";
    }

    @SuppressWarnings("unchecked")
    private boolean hasInfraUp(Map<?, ?> body, String infraName) {
        if (body == null || body.isEmpty()) {
            return false;
        }

        if (isUpStatus(body.get(infraName))) {
            return true;
        }

        Object componentsObj = body.get("components");
        if (!(componentsObj instanceof Map<?, ?> components)) {
            return false;
        }

        if ("postgres".equals(infraName)) {
            return isUpStatus(components.get("postgres"))
                    || isUpStatus(components.get("db"))
                    || isUpStatus(components.get("r2dbc"));
        }

        return isUpStatus(components.get(infraName));
    }

    @SuppressWarnings("unchecked")
    private boolean isUpStatus(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof Map<?, ?> map) {
            Object status = map.get("status");
            return status != null && "UP".equalsIgnoreCase(String.valueOf(status));
        }
        return "UP".equalsIgnoreCase(String.valueOf(obj));
    }

    private long toLatencyMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    /**
     * Direct TCP connect to Kafka broker to verify it is reachable.
     * Runs on boundedElastic scheduler to avoid blocking the event loop.
     */
    private Mono<String> checkKafkaHealth() {
        return Mono.fromCallable(() -> {
            String host = kafkaBootstrap;
            int port = 9092;
            int colonIdx = kafkaBootstrap.lastIndexOf(':');
            if (colonIdx > 0) {
                host = kafkaBootstrap.substring(0, colonIdx);
                try {
                    port = Integer.parseInt(kafkaBootstrap.substring(colonIdx + 1));
                } catch (NumberFormatException ignored) {
                }
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 2000);
                return "UP";
            } catch (Exception e) {
                log.warn("Kafka health check failed ({}:{}): {}", host, port, e.getMessage());
                return "DOWN";
            }
        }).subscribeOn(Schedulers.boundedElastic())
          .timeout(HEALTH_TIMEOUT)
          .onErrorReturn("DOWN");
    }

    private record ServiceTarget(String name, String url) {}

    private record ServiceCheckResult(String name, String status, long latencyMs, Map<?, ?> body) {}
}
