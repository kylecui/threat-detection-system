package com.threatdetection.customer.service;

import com.threatdetection.customer.dto.CreateNetSegmentWeightRequest;
import com.threatdetection.customer.dto.NetSegmentWeightResponse;
import com.threatdetection.customer.dto.UpdateNetSegmentWeightRequest;
import com.threatdetection.customer.exception.CustomerNotFoundException;
import com.threatdetection.customer.exception.NetSegmentWeightAlreadyExistsException;
import com.threatdetection.customer.exception.NetSegmentWeightNotFoundException;
import com.threatdetection.customer.model.NetSegmentWeight;
import com.threatdetection.customer.repository.CustomerRepository;
import com.threatdetection.customer.repository.NetSegmentWeightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NetSegmentWeightService {

    private final NetSegmentWeightRepository netSegmentWeightRepository;
    private final CustomerRepository customerRepository;

    public List<NetSegmentWeightResponse> getWeightsByCustomerId(String customerId) {
        log.info("Fetching net-segment weights: customerId={}", customerId);
        ensureCustomerExists(customerId);

        return netSegmentWeightRepository.findByCustomerId(customerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NetSegmentWeightResponse createWeight(String customerId, CreateNetSegmentWeightRequest request) {
        log.info("Creating net-segment weight: customerId={}, cidr={}", customerId, request.getCidr());
        ensureCustomerExists(customerId);

        if (netSegmentWeightRepository.existsByCustomerIdAndCidr(customerId, request.getCidr())) {
            throw new NetSegmentWeightAlreadyExistsException(
                    "Net segment weight already exists for customerId='" + customerId + "', cidr='" + request.getCidr() + "'"
            );
        }

        NetSegmentWeight entity = NetSegmentWeight.builder()
                .customerId(customerId)
                .cidr(request.getCidr())
                .weight(request.getWeight() != null ? request.getWeight() : new BigDecimal("1.0"))
                .description(request.getDescription())
                .build();

        NetSegmentWeight saved = netSegmentWeightRepository.save(entity);
        log.info("Created net-segment weight: customerId={}, weightId={}", customerId, saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public NetSegmentWeightResponse updateWeight(String customerId, Long weightId, UpdateNetSegmentWeightRequest request) {
        log.info("Updating net-segment weight: customerId={}, weightId={}", customerId, weightId);
        ensureCustomerExists(customerId);

        NetSegmentWeight entity = netSegmentWeightRepository.findById(weightId)
                .filter(it -> customerId.equals(it.getCustomerId()))
                .orElseThrow(() -> new NetSegmentWeightNotFoundException(
                        "Net segment weight not found: customerId='" + customerId + "', weightId=" + weightId
                ));

        if (request.getWeight() != null) {
            entity.setWeight(request.getWeight());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }

        NetSegmentWeight updated = netSegmentWeightRepository.save(entity);
        log.info("Updated net-segment weight: customerId={}, weightId={}", customerId, weightId);
        return toResponse(updated);
    }

    @Transactional
    public void deleteWeight(String customerId, Long weightId) {
        log.info("Deleting net-segment weight: customerId={}, weightId={}", customerId, weightId);
        ensureCustomerExists(customerId);

        NetSegmentWeight entity = netSegmentWeightRepository.findById(weightId)
                .filter(it -> customerId.equals(it.getCustomerId()))
                .orElseThrow(() -> new NetSegmentWeightNotFoundException(
                        "Net segment weight not found: customerId='" + customerId + "', weightId=" + weightId
                ));

        netSegmentWeightRepository.delete(entity);
        log.info("Deleted net-segment weight: customerId={}, weightId={}", customerId, weightId);
    }

    private void ensureCustomerExists(String customerId) {
        if (!customerRepository.existsByCustomerId(customerId)) {
            throw new CustomerNotFoundException("Customer not found: " + customerId);
        }
    }

    private NetSegmentWeightResponse toResponse(NetSegmentWeight entity) {
        return NetSegmentWeightResponse.builder()
                .id(entity.getId())
                .customerId(entity.getCustomerId())
                .cidr(entity.getCidr())
                .weight(entity.getWeight())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
