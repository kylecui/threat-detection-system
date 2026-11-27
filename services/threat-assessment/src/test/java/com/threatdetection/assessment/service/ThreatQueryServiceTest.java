package com.threatdetection.assessment.service;

import com.threatdetection.assessment.dto.ThreatAssessmentDetailResponse;
import com.threatdetection.assessment.dto.PortDistribution;
import com.threatdetection.assessment.model.ThreatAssessment;
import com.threatdetection.assessment.repository.ThreatAssessmentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThreatQueryServiceTest {

    @Test
    void assessmentDetailReturnsPersistedAttackIp() {
        ThreatAssessmentRepository repository = mock(ThreatAssessmentRepository.class);
        ThreatQueryService service = new ThreatQueryService(repository);

        ThreatAssessment assessment = new ThreatAssessment();
        assessment.setId(146L);
        assessment.setCustomerId("customer");
        assessment.setAttackMac("28:D0:F5:D0:84:E1");
        assessment.setAttackIp("192.168.2.223");
        assessment.setThreatScore(45.05);
        assessment.setThreatLevel("MEDIUM");
        assessment.setAttackCount(5);
        assessment.setUniqueIps(4);
        assessment.setUniquePorts(2);
        assessment.setUniqueDevices(1);
        assessment.setAssessmentTime(Instant.parse("2026-09-25T05:48:22Z"));

        when(repository.findById(146L)).thenReturn(Optional.of(assessment));

        ThreatAssessmentDetailResponse response = service.getAssessmentDetail(146L);

        assertEquals("192.168.2.223", response.getAttackIp());
    }

    @Test
    void portDistributionUsesRawEventCountsAndNormalizesInvalidPorts() {
        ThreatAssessmentRepository repository = mock(ThreatAssessmentRepository.class);
        ThreatQueryService service = new ThreatQueryService(repository);

        when(repository.getPortDistributionRows(eq("customer"), any(Instant.class), eq(15)))
                .thenReturn(List.of(
                        new Object[] {443, 42L, 50L},
                        new Object[] {0, 8L, 50L}
                ));

        List<PortDistribution> result = service.getPortDistribution("customer", 24);

        assertEquals(2, result.size());
        assertEquals(443, result.get(0).getPort());
        assertEquals("HTTPS", result.get(0).getPortName());
        assertEquals(42L, result.get(0).getCount());
        assertEquals(84.0, result.get(0).getPercentage());
        assertEquals(0, result.get(1).getPort());
        assertNull(result.get(1).getPortName());
        assertEquals(16.0, result.get(1).getPercentage());
    }

    @Test
    void deleteAssessmentHonorsTenantScope() {
        ThreatAssessmentRepository repository = mock(ThreatAssessmentRepository.class);
        ThreatQueryService service = new ThreatQueryService(repository);
        ThreatAssessment assessment = new ThreatAssessment();
        assessment.setId(91L);
        assessment.setCustomerId("customer-a");

        when(repository.findById(91L)).thenReturn(Optional.of(assessment));
        when(repository.existsByIdAndTenantId(91L, 7L)).thenReturn(true);

        service.deleteAssessment(91L, null, 7L);

        verify(repository).delete(assessment);
    }

    @Test
    void batchDeleteIsAtomicWhenAnyAssessmentIsOutsideCustomerScope() {
        ThreatAssessmentRepository repository = mock(ThreatAssessmentRepository.class);
        ThreatQueryService service = new ThreatQueryService(repository);
        ThreatAssessment allowed = new ThreatAssessment();
        allowed.setId(1L);
        allowed.setCustomerId("customer-a");
        ThreatAssessment denied = new ThreatAssessment();
        denied.setId(2L);
        denied.setCustomerId("customer-b");
        List<Long> ids = List.of(1L, 2L);

        when(repository.findAllById(ids)).thenReturn(List.of(allowed, denied));

        assertThrows(IllegalArgumentException.class,
                () -> service.deleteAssessments(ids, "customer-a", null));
        verify(repository, never()).deleteAllInBatch(any());
    }
}
