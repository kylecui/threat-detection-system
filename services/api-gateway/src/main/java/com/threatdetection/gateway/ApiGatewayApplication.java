package com.threatdetection.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

import lombok.extern.slf4j.Slf4j;

/**
 * API Gateway应用主类
 * 
 * <p>提供统一的API入口，路由到各个微服务：
 * <ul>
 *   <li>/api/v1/customers/** → Customer Management (8084)</li>
 *   <li>/api/v1/devices/** → Customer Management (8084)</li>
 *   <li>/api/v1/notifications/** → Customer Management (8084)</li>
 *   <li>/api/v1/logs/** → Data Ingestion (8080)</li>
 *   <li>/api/v1/assessment/** → Threat Assessment (8081)</li>
 *   <li>/api/v1/alerts/** → Alert Management (8082)</li>
 *   <li>/api/notification-config/** → Alert Management (8082)</li>
 *   <li>/api/v1/threat-intel/** → Threat Intelligence (8085)</li>
 *   <li>/api/v1/ml/** → ML Detection (8086)</li>
 * </ul>
 * 
 * <p>核心功能：
 * <ul>
 *   <li>路由转发</li>
 *   <li>跨域配置</li>
 *   <li>限流保护</li>
 *   <li>日志记录</li>
 *   <li>统一异常处理</li>
 * </ul>
 * 
 * @author Threat Detection Team
 * @version 1.0
 * @since 2025-10-19
 */
@Slf4j
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        log.info("Starting API Gateway Application...");
        SpringApplication.run(ApiGatewayApplication.class, args);
        log.info("API Gateway Application started successfully!");
    }

    /**
     * 配置路由规则
     * 
     * <p>基于路径前缀路由到不同的微服务
     * 
     * @param builder RouteLocatorBuilder
     * @return RouteLocator
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // Customer Management Service (8084)
            .route("customer-management-customers", r -> r
                .path("/api/v1/customers/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Service", "customer-management")
                    .circuitBreaker(c -> c
                        .setName("customerManagementCB")
                        .setFallbackUri("forward:/fallback/customer-management")))
                .uri("http://customer-management:8084"))
            
            .route("customer-management-devices", r -> r
                .path("/api/v1/devices/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Service", "customer-management"))
                .uri("http://customer-management:8084"))
            
            .route("customer-management-notifications", r -> r
                .path("/api/v1/notifications/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Service", "customer-management"))
                .uri("http://customer-management:8084"))
            
            // Data Ingestion Service (8080)
            .route("data-ingestion", r -> r
                .path("/api/v1/logs/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Service", "data-ingestion")
                    .circuitBreaker(c -> c
                        .setName("dataIngestionCB")
                        .setFallbackUri("forward:/fallback/data-ingestion")))
                .uri("http://data-ingestion:8080"))

            // Data Ingestion Service - Scenario Aware Import (8080)
            .route("data-ingestion-scenario-aware", r -> r
                .path("/api/v1/logs/import/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Service", "data-ingestion")
                    .circuitBreaker(c -> c
                        .setName("dataIngestionScenarioAwareCB")
                        .setFallbackUri("forward:/fallback/data-ingestion")))
                .uri("http://data-ingestion:8080"))

            // Threat Assessment Service (8083)
            .route("threat-assessment", r -> r
                .path("/api/v1/assessment/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Service", "threat-assessment")
                    .circuitBreaker(c -> c
                        .setName("threatAssessmentCB")
                        .setFallbackUri("forward:/fallback/threat-assessment")))
                .uri("http://threat-assessment:8083"))

            // Alert Management Service (8082)
            .route("alert-management", r -> r
                .path("/api/v1/alerts/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Service", "alert-management")
                    .circuitBreaker(c -> c
                        .setName("alertManagementCB")
                        .setFallbackUri("forward:/fallback/alert-management")))
                .uri("http://alert-management:8082"))

            // Alert Management - Notification Config (SMTP)
            .route("alert-management-notification-config", r -> r
                .path("/api/notification-config/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Service", "alert-management"))
                .uri("http://alert-management:8082"))

            // Threat Intelligence Service (8085)
            .route("threat-intelligence", r -> r
                .path("/api/v1/threat-intel/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Service", "threat-intelligence"))
                .uri("http://threat-intelligence:8085"))

            // ML Detection Service (8086) - health endpoint rewrite
            .route("ml-detection-health", r -> r
                .path("/api/v1/ml/health")
                .filters(f -> f
                    .rewritePath("/api/v1/ml/health", "/health")
                    .addRequestHeader("X-Gateway-Service", "ml-detection"))
                .uri("http://ml-detection:8086"))

            // ML Detection Service (8086)
            .route("ml-detection", r -> r
                .path("/api/v1/ml/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Service", "ml-detection"))
                .uri("http://ml-detection:8086"))
            
            // Health Check (直接返回Gateway健康状态)
            .route("health", r -> r
                .path("/health", "/actuator/health")
                .uri("http://localhost:8888"))
            
            .build();
    }
}
