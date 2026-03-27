package com.threatdetection.intelligence.controller;

import com.threatdetection.intelligence.dto.BulkUpsertRequest;
import com.threatdetection.intelligence.dto.CreateIndicatorRequest;
import com.threatdetection.intelligence.dto.IndicatorResponse;
import com.threatdetection.intelligence.dto.LookupResponse;
import com.threatdetection.intelligence.dto.StatisticsResponse;
import com.threatdetection.intelligence.dto.UpdateIndicatorRequest;
import com.threatdetection.intelligence.model.ThreatIntelFeed;
import com.threatdetection.intelligence.service.ThreatIndicatorService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/threat-intel")
@Slf4j
public class ThreatIndicatorController {

    private static final Logger logger = LoggerFactory.getLogger(ThreatIndicatorController.class);

    private final ThreatIndicatorService threatIndicatorService;

    public ThreatIndicatorController(ThreatIndicatorService threatIndicatorService) {
        this.threatIndicatorService = threatIndicatorService;
    }

    @GetMapping("/lookup")
    public ResponseEntity<LookupResponse> lookupIp(@RequestParam("ip") String ip) {
        logger.debug("API: lookup ip={}", ip);
        return ResponseEntity.ok(threatIndicatorService.lookupIp(ip));
    }

    @GetMapping("/indicators")
    public ResponseEntity<Page<IndicatorResponse>> listIndicators(Pageable pageable) {
        return ResponseEntity.ok(threatIndicatorService.listIndicators(pageable));
    }

    @GetMapping("/indicators/{id}")
    public ResponseEntity<IndicatorResponse> getIndicator(@PathVariable Long id) {
        return ResponseEntity.ok(threatIndicatorService.getIndicator(id));
    }

    @PostMapping("/indicators")
    public ResponseEntity<IndicatorResponse> createIndicator(@Valid @RequestBody CreateIndicatorRequest request) {
        IndicatorResponse response = threatIndicatorService.createIndicator(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/indicators/{id}")
    public ResponseEntity<IndicatorResponse> updateIndicator(
            @PathVariable Long id,
            @Valid @RequestBody UpdateIndicatorRequest request
    ) {
        return ResponseEntity.ok(threatIndicatorService.updateIndicator(id, request));
    }

    @DeleteMapping("/indicators/{id}")
    public ResponseEntity<Void> deleteIndicator(@PathVariable Long id) {
        threatIndicatorService.deleteIndicator(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/indicators/bulk")
    public ResponseEntity<Map<String, Object>> bulkUpsert(@Valid @RequestBody BulkUpsertRequest request) {
        int count = threatIndicatorService.bulkUpsert(request);
        return ResponseEntity.ok(Map.of("upserted", count));
    }

    @PostMapping("/indicators/{id}/sighting")
    public ResponseEntity<Void> incrementSighting(@PathVariable Long id) {
        threatIndicatorService.incrementSighting(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/feeds")
    public ResponseEntity<List<ThreatIntelFeed>> listFeeds() {
        return ResponseEntity.ok(threatIndicatorService.listFeeds());
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> getStatistics() {
        return ResponseEntity.ok(threatIndicatorService.getStatistics());
    }
}
