package com.threatdetection.assessment.controller;

import com.threatdetection.assessment.dto.*;
import com.threatdetection.assessment.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 权重管理Controller测试
 * 
 * @author ThreatDetection Team
 * @version 5.0
 * @since 2025-10-31
 */
@WebMvcTest(WeightManagementController.class)
public class WeightManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AttackSourceWeightService attackSourceWeightService;

    @MockBean
    private HoneypotSensitivityWeightService honeypotSensitivityWeightService;

    @MockBean
    private AttackPhasePortConfigService attackPhasePortConfigService;

    @MockBean
    private AptTemporalAccumulationService aptTemporalAccumulationService;

    @Test
    void testGetAttackSourceWeights() throws Exception {
        // Given
        AttackSourceWeightDto dto = AttackSourceWeightDto.builder()
                .id(1L)
                .customerId("customer-001")
                .ipSegment("192.168.1.0/24")
                .attackSourceWeight(BigDecimal.valueOf(2.5))
                .isActive(true)
                .build();
        
        when(attackSourceWeightService.findByCustomerId("customer-001"))
                .thenReturn(Arrays.asList(dto));

        // When & Then
        mockMvc.perform(get("/api/v1/weights/attack-source/customer-001"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].customer_id").value("customer-001"))
                .andExpect(jsonPath("$[0].ip_segment").value("192.168.1.0/24"));
    }

    @Test
    void testSaveAttackSourceWeight() throws Exception {
        // Given
        AttackSourceWeightDto savedDto = AttackSourceWeightDto.builder()
                .id(1L)
                .customerId("customer-001")
                .ipSegment("192.168.1.0/24")
                .attackSourceWeight(BigDecimal.valueOf(2.5))
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(attackSourceWeightService.save(any(AttackSourceWeightDto.class)))
                .thenReturn(savedDto);

        // When & Then
        mockMvc.perform(post("/api/v1/weights/attack-source")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "customer_id": "customer-001",
                        "ip_segment": "192.168.1.0/24",
                        "attack_source_weight": 2.5,
                        "is_active": true
                    }
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customer_id").value("customer-001"));
    }

    @Test
    void testDeleteAttackSourceWeight() throws Exception {
        // Given
        when(attackSourceWeightService.delete("customer-001", "192.168.1.0/24"))
                .thenReturn(true);

        // When & Then
        mockMvc.perform(delete("/api/v1/weights/attack-source/customer-001")
                        .param("ipSegment", "192.168.1.0/24"))
                .andExpect(status().isNoContent());
    }

    @Test
    void testGetAptTemporalAccumulations() throws Exception {
        // Given
        AptTemporalAccumulationDto dto = AptTemporalAccumulationDto.builder()
                .id(1L)
                .customerId("customer-001")
                .attackMac("00:11:22:33:44:55")
                .windowStart(Instant.now())
                .accumulatedScore(BigDecimal.valueOf(100.0))
                .decayAccumulatedScore(BigDecimal.valueOf(95.0))
                .build();

        when(aptTemporalAccumulationService.findByCustomerId("customer-001"))
                .thenReturn(Arrays.asList(dto));

        // When & Then
        mockMvc.perform(get("/api/v1/weights/apt-temporal/customer-001"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].customer_id").value("customer-001"))
                .andExpect(jsonPath("$[0].attack_mac").value("00:11:22:33:44:55"));
    }

    @Test
    void testGetCurrentThreatScore() throws Exception {
        // Given
        when(aptTemporalAccumulationService.calculateCurrentThreatScore("customer-001", "00:11:22:33:44:55"))
                .thenReturn(BigDecimal.valueOf(150.0));

        // When & Then
        mockMvc.perform(get("/api/v1/weights/apt-temporal/customer-001/00:11:22:33:44:55/threat-score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("customer-001"))
                .andExpect(jsonPath("$.attackMac").value("00:11:22:33:44:55"))
                .andExpect(jsonPath("$.threatScore").value(150.0));
    }

    @Test
    void testGetEffectiveAttackPhasePortConfigs() throws Exception {
        // Given
        AttackPhasePortConfigDto dto = AttackPhasePortConfigDto.builder()
                .id(1L)
                .customerId("customer-001")
                .phase("RECON")
                .port(80)
                .priority(5)
                .enabled(true)
                .build();

        when(attackPhasePortConfigService.getEffectiveConfigs("customer-001", "RECON"))
                .thenReturn(Arrays.asList(dto));

        // When & Then
        mockMvc.perform(get("/api/v1/weights/attack-phase/customer-001/RECON/effective"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].phase").value("RECON"))
                .andExpect(jsonPath("$[0].port").value(80));
    }
}
