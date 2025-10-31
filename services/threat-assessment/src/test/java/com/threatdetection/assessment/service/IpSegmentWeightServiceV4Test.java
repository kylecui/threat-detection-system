package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.AttackSourceWeight;
import com.threatdetection.assessment.model.HoneypotSensitivityWeight;
import com.threatdetection.assessment.repository.AttackSourceWeightRepository;
import com.threatdetection.assessment.repository.HoneypotSensitivityWeightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * IP网段权重服务V4.0单元测试
 * 
 * @author ThreatDetection Team
 * @version 4.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IpSegmentWeightServiceV4 单元测试")
class IpSegmentWeightServiceV4Test {
    
    @Mock
    private AttackSourceWeightRepository attackSourceWeightRepository;
    
    @Mock
    private HoneypotSensitivityWeightRepository honeypotSensitivityWeightRepository;
    
    @InjectMocks
    private IpSegmentWeightServiceV4 service;
    
    private static final String CUSTOMER_ID = "test-customer";
    
    @BeforeEach
    void setUp() {
        // 初始化测试数据
    }
    
    @Test
    @DisplayName("场景1: IoT设备扫描管理区蜜罐 - 应返回高综合权重")
    void testIoTDeviceToManagementHoneypot() {
        // Given: IoT设备配置（权重0.9）
        AttackSourceWeight iotWeight = AttackSourceWeight.builder()
            .customerId(CUSTOMER_ID)
            .ipSegment("192.168.50.0/24")
            .attackSourceWeight(new BigDecimal("0.90"))
            .isActive(true)
            .build();
        
        // Given: 管理区蜜罐配置（权重3.5）
        HoneypotSensitivityWeight managementHoneypot = HoneypotSensitivityWeight.builder()
            .customerId(CUSTOMER_ID)
            .ipSegment("10.0.100.0/24")
            .honeypotSensitivityWeight(new BigDecimal("3.50"))
            .isActive(true)
            .build();
        
        when(attackSourceWeightRepository.findByCustomerIdAndIpAddress(CUSTOMER_ID, "192.168.50.10"))
            .thenReturn(Optional.of(iotWeight));
        when(honeypotSensitivityWeightRepository.findByCustomerIdAndHoneypotIp(CUSTOMER_ID, "10.0.100.50"))
            .thenReturn(Optional.of(managementHoneypot));
        
        // When
        double attackSourceWeight = service.getAttackSourceWeight(CUSTOMER_ID, "192.168.50.10");
        double honeypotWeight = service.getHoneypotSensitivityWeight(CUSTOMER_ID, "10.0.100.50");
        double combinedWeight = service.getCombinedSegmentWeight(CUSTOMER_ID, "192.168.50.10", "10.0.100.50");
        
        // Then
        assertEquals(0.90, attackSourceWeight, 0.01, "IoT设备权重应为0.9");
        assertEquals(3.50, honeypotWeight, 0.01, "管理区蜜罐权重应为3.5");
        assertEquals(3.15, combinedWeight, 0.01, "综合权重应为3.15 (0.9 × 3.5)");
        
        verify(attackSourceWeightRepository, times(2)).findByCustomerIdAndIpAddress(CUSTOMER_ID, "192.168.50.10");
        verify(honeypotSensitivityWeightRepository, times(2)).findByCustomerIdAndHoneypotIp(CUSTOMER_ID, "10.0.100.50");
    }
    
    @Test
    @DisplayName("场景2: 数据库服务器扫描办公区蜜罐 - 双重高风险")
    void testDatabaseServerToOfficeHoneypot() {
        // Given: 数据库服务器配置（权重3.0）
        AttackSourceWeight dbWeight = AttackSourceWeight.builder()
            .customerId(CUSTOMER_ID)
            .ipSegment("10.0.3.0/24")
            .attackSourceWeight(new BigDecimal("3.00"))
            .isActive(true)
            .build();
        
        // Given: 办公区蜜罐配置（权重1.3）
        HoneypotSensitivityWeight officeHoneypot = HoneypotSensitivityWeight.builder()
            .customerId(CUSTOMER_ID)
            .ipSegment("192.168.10.0/24")
            .honeypotSensitivityWeight(new BigDecimal("1.30"))
            .isActive(true)
            .build();
        
        when(attackSourceWeightRepository.findByCustomerIdAndIpAddress(CUSTOMER_ID, "10.0.3.50"))
            .thenReturn(Optional.of(dbWeight));
        when(honeypotSensitivityWeightRepository.findByCustomerIdAndHoneypotIp(CUSTOMER_ID, "192.168.10.50"))
            .thenReturn(Optional.of(officeHoneypot));
        
        // When
        double combinedWeight = service.getCombinedSegmentWeight(CUSTOMER_ID, "10.0.3.50", "192.168.10.50");
        
        // Then
        assertEquals(3.90, combinedWeight, 0.01, "综合权重应为3.9 (3.0 × 1.3)");
    }
    
    @Test
    @DisplayName("场景3: 办公区扫描办公区蜜罐 - 低-中等威胁")
    void testOfficeToOfficeHoneypot() {
        // Given: 办公区设备配置（权重1.0）
        AttackSourceWeight officeWeight = AttackSourceWeight.builder()
            .customerId(CUSTOMER_ID)
            .ipSegment("192.168.10.0/24")
            .attackSourceWeight(new BigDecimal("1.00"))
            .isActive(true)
            .build();
        
        // Given: 办公区蜜罐配置（权重1.3）
        HoneypotSensitivityWeight officeHoneypot = HoneypotSensitivityWeight.builder()
            .customerId(CUSTOMER_ID)
            .ipSegment("192.168.10.0/24")
            .honeypotSensitivityWeight(new BigDecimal("1.30"))
            .isActive(true)
            .build();
        
        when(attackSourceWeightRepository.findByCustomerIdAndIpAddress(CUSTOMER_ID, "192.168.10.100"))
            .thenReturn(Optional.of(officeWeight));
        when(honeypotSensitivityWeightRepository.findByCustomerIdAndHoneypotIp(CUSTOMER_ID, "192.168.10.50"))
            .thenReturn(Optional.of(officeHoneypot));
        
        // When
        double combinedWeight = service.getCombinedSegmentWeight(CUSTOMER_ID, "192.168.10.100", "192.168.10.50");
        
        // Then
        assertEquals(1.30, combinedWeight, 0.01, "综合权重应为1.3 (1.0 × 1.3)");
    }
    
    @Test
    @DisplayName("场景4: 访客网络扫描数据库蜜罐 - 隔离失效警报")
    void testGuestNetworkToDatabaseHoneypot() {
        // Given: 访客网络配置（权重0.6）
        AttackSourceWeight guestWeight = AttackSourceWeight.builder()
            .customerId(CUSTOMER_ID)
            .ipSegment("192.168.100.0/24")
            .attackSourceWeight(new BigDecimal("0.60"))
            .isActive(true)
            .build();
        
        // Given: 数据库蜜罐配置（权重3.5）
        HoneypotSensitivityWeight dbHoneypot = HoneypotSensitivityWeight.builder()
            .customerId(CUSTOMER_ID)
            .ipSegment("10.0.3.0/24")
            .honeypotSensitivityWeight(new BigDecimal("3.50"))
            .isActive(true)
            .build();
        
        when(attackSourceWeightRepository.findByCustomerIdAndIpAddress(CUSTOMER_ID, "192.168.100.20"))
            .thenReturn(Optional.of(guestWeight));
        when(honeypotSensitivityWeightRepository.findByCustomerIdAndHoneypotIp(CUSTOMER_ID, "10.0.3.50"))
            .thenReturn(Optional.of(dbHoneypot));
        
        // When
        double combinedWeight = service.getCombinedSegmentWeight(CUSTOMER_ID, "192.168.100.20", "10.0.3.50");
        
        // Then
        assertEquals(2.10, combinedWeight, 0.01, "综合权重应为2.1 (0.6 × 3.5) - 访客网络不应能访问数据库蜜罐！");
    }
    
    @Test
    @DisplayName("未匹配攻击源IP - 应返回默认权重1.0")
    void testUnmatchedAttackSourceIp() {
        // Given
        when(attackSourceWeightRepository.findByCustomerIdAndIpAddress(anyString(), anyString()))
            .thenReturn(Optional.empty());
        
        // When
        double weight = service.getAttackSourceWeight(CUSTOMER_ID, "1.2.3.4");
        
        // Then
        assertEquals(1.0, weight, "未匹配的攻击源IP应返回默认权重1.0");
    }
    
    @Test
    @DisplayName("未匹配蜜罐IP - 应返回默认权重1.0")
    void testUnmatchedHoneypotIp() {
        // Given
        when(honeypotSensitivityWeightRepository.findByCustomerIdAndHoneypotIp(anyString(), anyString()))
            .thenReturn(Optional.empty());
        
        // When
        double weight = service.getHoneypotSensitivityWeight(CUSTOMER_ID, "5.6.7.8");
        
        // Then
        assertEquals(1.0, weight, "未匹配的蜜罐IP应返回默认权重1.0");
    }
    
    @Test
    @DisplayName("空IP参数 - 应返回默认权重")
    void testNullOrEmptyIp() {
        // When/Then
        assertEquals(1.0, service.getAttackSourceWeight(CUSTOMER_ID, null));
        assertEquals(1.0, service.getAttackSourceWeight(CUSTOMER_ID, ""));
        assertEquals(1.0, service.getHoneypotSensitivityWeight(CUSTOMER_ID, null));
        assertEquals(1.0, service.getHoneypotSensitivityWeight(CUSTOMER_ID, ""));
        
        verifyNoInteractions(attackSourceWeightRepository);
        verifyNoInteractions(honeypotSensitivityWeightRepository);
    }
    
    @Test
    @DisplayName("数据库查询异常 - 应返回默认权重并记录错误")
    void testDatabaseException() {
        // Given
        when(attackSourceWeightRepository.findByCustomerIdAndIpAddress(anyString(), anyString()))
            .thenThrow(new RuntimeException("Database connection error"));
        
        // When
        double weight = service.getAttackSourceWeight(CUSTOMER_ID, "192.168.1.1");
        
        // Then
        assertEquals(1.0, weight, "发生异常时应返回默认权重1.0");
    }
    
    @Test
    @DisplayName("统计功能 - 验证计数方法")
    void testCountMethods() {
        // Given
        when(attackSourceWeightRepository.countByCustomerId(CUSTOMER_ID)).thenReturn(12L);
        when(honeypotSensitivityWeightRepository.countByCustomerId(CUSTOMER_ID)).thenReturn(10L);
        
        // When
        long attackSourceCount = service.countAttackSourceWeights(CUSTOMER_ID);
        long honeypotCount = service.countHoneypotSensitivityWeights(CUSTOMER_ID);
        
        // Then
        assertEquals(12, attackSourceCount, "应有12条攻击源权重配置");
        assertEquals(10, honeypotCount, "应有10条蜜罐敏感度配置");
        
        verify(attackSourceWeightRepository).countByCustomerId(CUSTOMER_ID);
        verify(honeypotSensitivityWeightRepository).countByCustomerId(CUSTOMER_ID);
    }
}
