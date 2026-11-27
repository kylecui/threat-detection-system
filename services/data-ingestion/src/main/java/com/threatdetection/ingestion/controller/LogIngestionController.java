package com.threatdetection.ingestion.controller;

import com.threatdetection.ingestion.model.AttackEvent;
import com.threatdetection.ingestion.model.BatchLogRequest;
import com.threatdetection.ingestion.model.BatchLogResponse;
import com.threatdetection.ingestion.model.StatusEvent;
import com.threatdetection.ingestion.service.AsyncBatchLogIngestionService;
import com.threatdetection.ingestion.service.KafkaProducerService;
import com.threatdetection.ingestion.service.LogParserService;
import com.threatdetection.ingestion.service.MetricsService;
import com.threatdetection.ingestion.service.DevSerialToCustomerMappingService;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/logs")
public class LogIngestionController {
    
    private static final Logger logger = LoggerFactory.getLogger(LogIngestionController.class);
    
    private final LogParserService logParserService;
    private final KafkaProducerService kafkaProducerService;
    private final AsyncBatchLogIngestionService batchIngestionService;
    private final MetricsService metricsService;
    private final DevSerialToCustomerMappingService devSerialToCustomerMappingService;
    
    public LogIngestionController(LogParserService logParserService, 
                                KafkaProducerService kafkaProducerService,
                                AsyncBatchLogIngestionService batchIngestionService,
                                MetricsService metricsService,
                                DevSerialToCustomerMappingService devSerialToCustomerMappingService) {
        this.logParserService = logParserService;
        this.kafkaProducerService = kafkaProducerService;
        this.batchIngestionService = batchIngestionService;
        this.metricsService = metricsService;
        this.devSerialToCustomerMappingService = devSerialToCustomerMappingService;
    }
    
    @PostMapping("/ingest")
    public CompletableFuture<ResponseEntity<String>> ingestLog(@RequestBody String rawLog) {
        // Phase 1A: 记录接收到的日志
        metricsService.recordLogReceived();
        
        Timer.Sample sample = metricsService.startSingleLogProcessingTimer();
        
        try {
            logger.debug("Received log for ingestion: bytes={}", rawLog.length());
            
            Optional<Object> parsedEvent = logParserService.parseLog(rawLog);
            logger.debug("Parsed event result: {}", parsedEvent.isPresent() ? "present" : "empty");
            
            if (parsedEvent.isPresent()) {
                Object event = parsedEvent.get();
                
                if (event instanceof AttackEvent) {
                    return completeKafkaSend(
                            kafkaProducerService.sendAttackEvent((AttackEvent) event), true, sample);
                } else if (event instanceof StatusEvent) {
                    return completeKafkaSend(
                            kafkaProducerService.sendStatusEvent((StatusEvent) event), false, sample);
                }
            }
            
            logger.warn("Failed to parse or process log: bytes={}", rawLog.length());
            metricsService.recordLogFailed();
            sample.stop(metricsService.singleLogProcessingTimer);
            return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest().body("Failed to process log"));
            
        } catch (Exception e) {
            logger.error("Error processing log ingestion: bytes={}, error={}", rawLog.length(), e.getMessage());
            metricsService.recordLogFailed();
            sample.stop(metricsService.singleLogProcessingTimer);
            return CompletableFuture.completedFuture(
                    ResponseEntity.internalServerError().body("Internal server error"));
        }
    }

    private CompletableFuture<ResponseEntity<String>> completeKafkaSend(
            CompletableFuture<?> sendFuture,
            boolean attackEvent,
            Timer.Sample sample) {
        return sendFuture
                .orTimeout(10, TimeUnit.SECONDS)
                .thenApply(result -> {
                    metricsService.recordLogProcessed();
                    if (attackEvent) {
                        metricsService.recordAttackEvent();
                    } else {
                        metricsService.recordStatusEvent();
                    }
                    return ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(attackEvent ? "Attack event accepted" : "Status event accepted");
                })
                .exceptionally(error -> {
                    logger.error("Kafka acknowledgement failed for {} event: {}",
                            attackEvent ? "attack" : "status", error.getMessage());
                    metricsService.recordLogFailed();
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body("Event queue temporarily unavailable");
                })
                .whenComplete((response, error) -> sample.stop(metricsService.singleLogProcessingTimer));
    }
    
    /**
     * Phase 1A: 批量日志处理接口
     * 支持一次处理多个日志，提高吞吐量
     */
    @PostMapping("/batch")
    public ResponseEntity<BatchLogResponse> ingestBatch(@RequestBody BatchLogRequest request) {
        // Phase 1A: 记录批量请求
        metricsService.recordBatchRequest();
        
        Timer.Sample sample = metricsService.startBatchProcessingTimer();
        
        try {
            logger.info("Received batch log request with {} logs", 
                       request.getLogs() != null ? request.getLogs().size() : 0);
            
            // Phase 1A: 请求验证
            if (!request.isValid()) {
                logger.warn("Invalid batch request: logs is null, empty, or exceeds limit");
                return ResponseEntity.badRequest().body(
                    createErrorResponse(request.getLogs() != null ? request.getLogs().size() : 0, "Invalid request: batch size must be 1-1000")
                );
            }
            
            // 处理批量日志
            BatchLogResponse response = batchIngestionService.processBatch(request.getLogs());
            
            // Phase 1A: 记录批量处理指标
            metricsService.recordBatchProcessingTime(response.getProcessingTimeMs());
            
            // 根据处理结果返回适当的HTTP状态码
            if (response.getErrorCount() == 0) {
                return ResponseEntity.ok(response);
            } else if (response.getSuccessCount() > 0) {
                // 部分成功
                return ResponseEntity.ok(response);
            } else {
                // 全部失败
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error processing batch log ingestion", e);
            return ResponseEntity.internalServerError().body(
                createErrorResponse(request.getLogs() == null ? 0 : request.getLogs().size(), "Internal server error")
            );
        } finally {
            sample.stop(metricsService.batchProcessingTimer);
        }
    }
    
    /**
     * Phase 1A: 获取解析统计信息 (用于监控)
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getParseStatistics() {
        try {
            return ResponseEntity.ok(logParserService.getParseStatistics());
        } catch (Exception e) {
            logger.error("Error retrieving parse statistics", e);
            return ResponseEntity.internalServerError().body("Failed to retrieve statistics");
        }
    }
    
    /**
     * Phase 1A: 重置解析统计信息
     */
    @PostMapping("/stats/reset")
    public ResponseEntity<String> resetParseStatistics() {
        try {
            logParserService.resetStatistics();
            return ResponseEntity.ok("Statistics reset successfully");
        } catch (Exception e) {
            logger.error("Error resetting parse statistics", e);
            return ResponseEntity.internalServerError().body("Failed to reset statistics");
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Log Ingestion Service is healthy");
    }
    
    /**
     * Test endpoint to verify customer mapping from database
     */
    @GetMapping("/customer-mapping/{devSerial}")
    public ResponseEntity<String> getCustomerMapping(@PathVariable String devSerial) {
        try {
            String customerId = devSerialToCustomerMappingService.resolveCustomerId(devSerial);
            return ResponseEntity.ok("DevSerial: " + devSerial + " -> Customer: " + customerId);
        } catch (Exception e) {
            logger.error("Error resolving customer mapping for devSerial: " + devSerial, e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
    
    /**
     * 创建错误响应
     */
    private BatchLogResponse createErrorResponse(int totalCount, String errorMessage) {
        return new BatchLogResponse(totalCount, 0, totalCount, 
            java.util.Collections.singletonList(
                com.threatdetection.ingestion.model.IngestionResult.error(errorMessage)
            ), 
            0);
    }
}
