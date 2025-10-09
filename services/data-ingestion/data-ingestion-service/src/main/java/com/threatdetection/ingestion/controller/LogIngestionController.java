package com.threatdetection.ingestion.controller;

import com.threatdetection.ingestion.model.AttackEvent;
import com.threatdetection.ingestion.model.StatusEvent;
import com.threatdetection.ingestion.service.KafkaProducerService;
import com.threatdetection.ingestion.service.LogParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/logs")
public class LogIngestionController {
    
    private static final Logger logger = LoggerFactory.getLogger(LogIngestionController.class);
    
    private final LogParserService logParserService;
    private final KafkaProducerService kafkaProducerService;
    
    public LogIngestionController(LogParserService logParserService, 
                                KafkaProducerService kafkaProducerService) {
        this.logParserService = logParserService;
        this.kafkaProducerService = kafkaProducerService;
    }
    
    @PostMapping("/ingest")
    public ResponseEntity<String> ingestLog(@RequestBody String rawLog) {
        try {
            logger.info("Received log for ingestion: {}", rawLog);
            
            // Debug: Log the raw input
            logger.debug("Raw log input: {}", rawLog);
            
            Optional<Object> parsedEvent = logParserService.parseLog(rawLog);
            
            if (parsedEvent.isPresent()) {
                Object event = parsedEvent.get();
                
                if (event instanceof AttackEvent) {
                    kafkaProducerService.sendAttackEvent((AttackEvent) event);
                    return ResponseEntity.ok("Attack event processed successfully");
                } else if (event instanceof StatusEvent) {
                    kafkaProducerService.sendStatusEvent((StatusEvent) event);
                    return ResponseEntity.ok("Status event processed successfully");
                }
            }
            
            logger.warn("Failed to parse or process log: {}", rawLog);
            return ResponseEntity.badRequest().body("Failed to process log");
            
        } catch (Exception e) {
            logger.error("Error processing log ingestion: {}", rawLog, e);
            return ResponseEntity.internalServerError().body("Internal server error");
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Log Ingestion Service is healthy");
    }
}