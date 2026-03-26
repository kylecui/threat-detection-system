package com.threatdetection.customer.controller;

import com.threatdetection.customer.dto.CreateNetSegmentWeightRequest;
import com.threatdetection.customer.dto.NetSegmentWeightResponse;
import com.threatdetection.customer.dto.UpdateNetSegmentWeightRequest;
import com.threatdetection.customer.service.NetSegmentWeightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/net-weights")
@RequiredArgsConstructor
@Slf4j
public class NetSegmentWeightController {

    private final NetSegmentWeightService netSegmentWeightService;

    @GetMapping
    public ResponseEntity<List<NetSegmentWeightResponse>> getWeights(
            @PathVariable("customerId") String customerId) {
        log.info("API: Fetching net-segment weights: customerId={}", customerId);
        return ResponseEntity.ok(netSegmentWeightService.getWeightsByCustomerId(customerId));
    }

    @PostMapping
    public ResponseEntity<NetSegmentWeightResponse> createWeight(
            @PathVariable("customerId") String customerId,
            @Valid @RequestBody CreateNetSegmentWeightRequest request) {
        log.info("API: Creating net-segment weight: customerId={}, cidr={}", customerId, request.getCidr());
        NetSegmentWeightResponse response = netSegmentWeightService.createWeight(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{weightId}")
    public ResponseEntity<NetSegmentWeightResponse> updateWeight(
            @PathVariable("customerId") String customerId,
            @PathVariable("weightId") Long weightId,
            @Valid @RequestBody UpdateNetSegmentWeightRequest request) {
        log.info("API: Updating net-segment weight: customerId={}, weightId={}", customerId, weightId);
        return ResponseEntity.ok(netSegmentWeightService.updateWeight(customerId, weightId, request));
    }

    @DeleteMapping("/{weightId}")
    public ResponseEntity<Void> deleteWeight(
            @PathVariable("customerId") String customerId,
            @PathVariable("weightId") Long weightId) {
        log.info("API: Deleting net-segment weight: customerId={}, weightId={}", customerId, weightId);
        netSegmentWeightService.deleteWeight(customerId, weightId);
        return ResponseEntity.noContent().build();
    }
}
