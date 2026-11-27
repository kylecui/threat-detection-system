package com.threatdetection.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

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
    public ResponseEntity<Map<String, Object>> customerManagementFallback(ServerWebExchange exchange) {
        return buildFallbackResponse("Customer Management Service", exchange);
    }

    @GetMapping("/data-ingestion")
    public ResponseEntity<Map<String, Object>> dataIngestionFallback(ServerWebExchange exchange) {
        return buildFallbackResponse("Data Ingestion Service", exchange);
    }

    @GetMapping("/threat-assessment")
    public ResponseEntity<Map<String, Object>> threatAssessmentFallback(ServerWebExchange exchange) {
        return buildFallbackResponse("Threat Assessment Service", exchange);
    }

    @GetMapping("/alert-management")
    public ResponseEntity<Map<String, Object>> alertManagementFallback(ServerWebExchange exchange) {
        return buildFallbackResponse("Alert Management Service", exchange);
    }

    /**
     * 构建降级响应
     */
    private ResponseEntity<Map<String, Object>> buildFallbackResponse(
            String serviceName, ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(
                ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        String requestId = exchange.getRequest().getId();
        if (cause == null) {
            log.warn("{} is unavailable; requestId={}, cause=unknown", serviceName, requestId);
        } else {
            log.warn("{} is unavailable; requestId={}, causeType={}, causeMessage={}",
                    serviceName, requestId, cause.getClass().getSimpleName(), cause.getMessage());
        }
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Service Unavailable");
        response.put("code", "DEPENDENCY_NOT_READY");
        response.put("message", serviceName + " is temporarily unavailable. Please try again later.");
        response.put("timestamp", Instant.now().toString());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("requestId", requestId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "5");
        return new ResponseEntity<>(response, headers, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
