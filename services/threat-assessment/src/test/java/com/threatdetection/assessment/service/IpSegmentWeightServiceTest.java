package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.IpSegmentWeightConfig;
import com.threatdetection.assessment.repository.IpSegmentWeightConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * IP段权重服务单元测试
 * 
 * @author Security Team
 * @version 2.0
 * @since Phase 3
 */
@ExtendWith(MockitoExtension.class)
class IpSegmentWeightServiceTest {
    
    @Mock
    private IpSegmentWeightConfigRepository repository;
    
    @InjectMocks
    private IpSegmentWeightService ipSegmentWeightService;
    
    private IpSegmentWeightConfig privateConfig;
    private IpSegmentWeightConfig highRiskConfig;
    private IpSegmentWeightConfig maliciousConfig;
    
    @BeforeEach
    void setUp() {
        // 内网配置
        privateConfig = IpSegmentWeightConfig.builder()
            .id(1L)
            .segmentName("Private-192.168.0.0/16")
            .ipRangeStart("192.168.0.0")
            .ipRangeEnd("192.168.255.255")
            .weight(0.8)
            .category("PRIVATE")
            .description("内网C类地址段")
            .priority(10)
            .createdAt(Instant.now())
            .build();
        
        // 高危地区配置
        highRiskConfig = IpSegmentWeightConfig.builder()
            .id(2L)
            .segmentName("Russia-Moscow")
            .ipRangeStart("5.3.0.0")
            .ipRangeEnd("5.3.255.255")
            .weight(1.8)
            .category("HIGH_RISK_REGION")
            .description("俄罗斯莫斯科地区")
            .priority(80)
            .createdAt(Instant.now())
            .build();
        
        // 已知恶意网段
        maliciousConfig = IpSegmentWeightConfig.builder()
            .id(3L)
            .segmentName("Malicious-Botnet-1")
            .ipRangeStart("45.142.120.0")
            .ipRangeEnd("45.142.123.255")
            .weight(2.0)
            .category("MALICIOUS")
            .description("已知僵尸网络C2服务器")
            .priority(100)
            .createdAt(Instant.now())
            .build();
    }
    
    // ==================== IP权重查询测试 ====================
    
    @Test
    @DisplayName("查询内网IP权重应返回0.8")
    void testGetIpSegmentWeight_PrivateIp() {
        // Given
        when(repository.findByIpAddress("192.168.1.100"))
            .thenReturn(Optional.of(privateConfig));
        
        // When
        double weight = ipSegmentWeightService.getIpSegmentWeight("192.168.1.100");
        
        // Then
        assertEquals(0.8, weight, 0.01);
        verify(repository, times(1)).findByIpAddress("192.168.1.100");
    }
    
    @Test
    @DisplayName("查询高危IP权重应返回1.8")
    void testGetIpSegmentWeight_HighRiskIp() {
        // Given
        when(repository.findByIpAddress("5.3.100.50"))
            .thenReturn(Optional.of(highRiskConfig));
        
        // When
        double weight = ipSegmentWeightService.getIpSegmentWeight("5.3.100.50");
        
        // Then
        assertEquals(1.8, weight, 0.01);
        verify(repository, times(1)).findByIpAddress("5.3.100.50");
    }
    
    @Test
    @DisplayName("查询恶意IP权重应返回2.0")
    void testGetIpSegmentWeight_MaliciousIp() {
        // Given
        when(repository.findByIpAddress("45.142.121.10"))
            .thenReturn(Optional.of(maliciousConfig));
        
        // When
        double weight = ipSegmentWeightService.getIpSegmentWeight("45.142.121.10");
        
        // Then
        assertEquals(2.0, weight, 0.01);
        verify(repository, times(1)).findByIpAddress("45.142.121.10");
    }
    
    @Test
    @DisplayName("未匹配到网段应返回默认权重1.0")
    void testGetIpSegmentWeight_NoMatch() {
        // Given
        when(repository.findByIpAddress("8.8.8.8"))
            .thenReturn(Optional.empty());
        
        // When
        double weight = ipSegmentWeightService.getIpSegmentWeight("8.8.8.8");
        
        // Then
        assertEquals(1.0, weight, 0.01);
        verify(repository, times(1)).findByIpAddress("8.8.8.8");
    }
    
    @Test
    @DisplayName("空IP应返回默认权重1.0")
    void testGetIpSegmentWeight_NullIp() {
        // When
        double weight = ipSegmentWeightService.getIpSegmentWeight(null);
        
        // Then
        assertEquals(1.0, weight, 0.01);
        verify(repository, never()).findByIpAddress(anyString());
    }
    
    @Test
    @DisplayName("异常时应返回默认权重1.0")
    void testGetIpSegmentWeight_Exception() {
        // Given
        when(repository.findByIpAddress("invalid-ip"))
            .thenThrow(new RuntimeException("Database error"));
        
        // When
        double weight = ipSegmentWeightService.getIpSegmentWeight("invalid-ip");
        
        // Then
        assertEquals(1.0, weight, 0.01);
    }
    
    // ==================== 网段配置查询测试 ====================
    
    @Test
    @DisplayName("查询IP网段配置应返回配置对象")
    void testGetIpSegmentConfig() {
        // Given
        when(repository.findByIpAddress("192.168.1.100"))
            .thenReturn(Optional.of(privateConfig));
        
        // When
        Optional<IpSegmentWeightConfig> config = ipSegmentWeightService.getIpSegmentConfig("192.168.1.100");
        
        // Then
        assertTrue(config.isPresent());
        assertEquals("Private-192.168.0.0/16", config.get().getSegmentName());
        assertEquals(0.8, config.get().getWeight(), 0.01);
    }
    
    @Test
    @DisplayName("查询不存在的IP配置应返回空")
    void testGetIpSegmentConfig_NotFound() {
        // Given
        when(repository.findByIpAddress("8.8.8.8"))
            .thenReturn(Optional.empty());
        
        // When
        Optional<IpSegmentWeightConfig> config = ipSegmentWeightService.getIpSegmentConfig("8.8.8.8");
        
        // Then
        assertFalse(config.isPresent());
    }
    
    // ==================== 高危网段查询测试 ====================
    
    @Test
    @DisplayName("查询高危网段应返回权重>=1.7的网段")
    void testGetHighRiskSegments() {
        // Given
        List<IpSegmentWeightConfig> highRiskList = Arrays.asList(
            maliciousConfig,  // 2.0
            highRiskConfig    // 1.8
        );
        when(repository.findHighRiskSegments(1.7))
            .thenReturn(highRiskList);
        
        // When
        List<IpSegmentWeightConfig> result = ipSegmentWeightService.getHighRiskSegments(1.7);
        
        // Then
        assertEquals(2, result.size());
        assertEquals(2.0, result.get(0).getWeight(), 0.01);
        assertEquals(1.8, result.get(1).getWeight(), 0.01);
        verify(repository, times(1)).findHighRiskSegments(1.7);
    }
    
    @Test
    @DisplayName("查询恶意网段应返回MALICIOUS分类的网段")
    void testGetMaliciousSegments() {
        // Given
        List<IpSegmentWeightConfig> maliciousList = Arrays.asList(maliciousConfig);
        when(repository.findMaliciousSegments())
            .thenReturn(maliciousList);
        
        // When
        List<IpSegmentWeightConfig> result = ipSegmentWeightService.getMaliciousSegments();
        
        // Then
        assertEquals(1, result.size());
        assertEquals("MALICIOUS", result.get(0).getCategory());
        assertEquals(2.0, result.get(0).getWeight(), 0.01);
    }
    
    // ==================== 分类查询测试 ====================
    
    @Test
    @DisplayName("按分类查询应返回该分类的所有网段")
    void testGetSegmentsByCategory() {
        // Given
        List<IpSegmentWeightConfig> privateList = Arrays.asList(privateConfig);
        when(repository.findByCategory("PRIVATE"))
            .thenReturn(privateList);
        
        // When
        List<IpSegmentWeightConfig> result = ipSegmentWeightService.getSegmentsByCategory("PRIVATE");
        
        // Then
        assertEquals(1, result.size());
        assertEquals("PRIVATE", result.get(0).getCategory());
        assertEquals(0.8, result.get(0).getWeight(), 0.01);
    }
    
    // ==================== 内网检测测试 ====================
    
    @Test
    @DisplayName("检测内网IP应返回true")
    void testIsPrivateIp_True() {
        // Given
        when(repository.isPrivateIp("192.168.1.100"))
            .thenReturn(true);
        
        // When
        boolean isPrivate = ipSegmentWeightService.isPrivateIp("192.168.1.100");
        
        // Then
        assertTrue(isPrivate);
    }
    
    @Test
    @DisplayName("检测公网IP应返回false")
    void testIsPrivateIp_False() {
        // Given
        when(repository.isPrivateIp("8.8.8.8"))
            .thenReturn(false);
        
        // When
        boolean isPrivate = ipSegmentWeightService.isPrivateIp("8.8.8.8");
        
        // Then
        assertFalse(isPrivate);
    }
    
    @Test
    @DisplayName("空IP检测应返回false")
    void testIsPrivateIp_NullIp() {
        // When
        boolean isPrivate = ipSegmentWeightService.isPrivateIp(null);
        
        // Then
        assertFalse(isPrivate);
        verify(repository, never()).isPrivateIp(anyString());
    }
    
    // ==================== 初始化测试 ====================
    
    @Test
    @DisplayName("已有配置时应跳过初始化")
    void testInitializeDefaultSegments_AlreadyInitialized() {
        // Given
        when(repository.countConfiguredSegments())
            .thenReturn(50L);
        
        // When
        ipSegmentWeightService.initializeDefaultSegments();
        
        // Then
        verify(repository, times(1)).countConfiguredSegments();
        verify(repository, never()).countByCategory();
    }
    
    @Test
    @DisplayName("无配置时应执行初始化检查")
    void testInitializeDefaultSegments_NoConfiguration() {
        // Given
        when(repository.countConfiguredSegments())
            .thenReturn(0L)  // 首次检查
            .thenReturn(50L); // 初始化后检查
        
        when(repository.countByCategory())
            .thenReturn(Arrays.asList(
                new Object[]{"PRIVATE", 5L},
                new Object[]{"CLOUD_AWS", 10L},
                new Object[]{"HIGH_RISK_REGION", 8L}
            ));
        
        // When
        ipSegmentWeightService.initializeDefaultSegments();
        
        // Then
        verify(repository, times(2)).countConfiguredSegments();
        verify(repository, times(1)).countByCategory();
    }
}
