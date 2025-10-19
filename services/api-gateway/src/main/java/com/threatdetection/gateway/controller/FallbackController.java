package com.threatdetection.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 熔断降级控制器
 * 
 * <p>当后端服务不可用时，返回友好的降级响应
 * 
 * @author Threat Detection Team
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/customer-management")
    public ResponseEntity<Map<String, Object>> customerManagementFallback() {
        log.warn("Customer Management Service is unavailable, returning fallback response");
        return buildFallbackResponse("Customer Management Service");
    }

    @GetMapping("/data-ingestion")
    public ResponseEntity<Map<String, Object>> dataIngestionFallback() {
        log.warn("Data Ingestion Service is unavailable, returning fallback response");
        return buildFallbackResponse("Data Ingestion Service");
    }

    @GetMapping("/threat-assessment")
    public ResponseEntity<Map<String, Object>> threatAssessmentFallback() {
        log.warn("Threat Assessment Service is unavailable, returning fallback response");
        return buildFallbackResponse("Threat Assessment Service");
    }

    @GetMapping("/alert-management")
    public ResponseEntity<Map<String, Object>> alertManagementFallback() {
        log.warn("Alert Management Service is unavailable, returning fallback response");
        return buildFallbackResponse("Alert Management Service");
    }

    /**
     * 构建降级响应
     */
    private ResponseEntity<Map<String, Object>> buildFallbackResponse(String serviceName) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Service Unavailable");
        response.put("message", serviceName + " is temporarily unavailable. Please try again later.");
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(response);
    }
}
