package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.PortRiskConfig;
import com.threatdetection.assessment.repository.PortRiskConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 端口风险服务单元测试
 * 
 * @author Security Team
 * @version 2.0
 */
class PortRiskServiceTest {
    
    @Mock
    private PortRiskConfigRepository repository;
    
    private PortRiskService service;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PortRiskService(repository);
    }
    
    // ==================== 单端口查询测试 ====================
    
    @Test
    @DisplayName("查询已配置端口应返回配置评分")
    void testGetPortRiskScore_Configured() {
        // 模拟RDP端口 (3389)
        PortRiskConfig rdpConfig = new PortRiskConfig(3389, "RDP", 3.0, "REMOTE_ACCESS", "远程桌面");
        when(repository.findByPortNumber(3389)).thenReturn(Optional.of(rdpConfig));
        
        double score = service.getPortRiskScore(3389);
        
        assertEquals(3.0, score, 0.001);
        verify(repository).findByPortNumber(3389);
    }
    
    @Test
    @DisplayName("查询未配置端口应返回默认评分1.0")
    void testGetPortRiskScore_Unconfigured() {
        when(repository.findByPortNumber(12345)).thenReturn(Optional.empty());
        
        double score = service.getPortRiskScore(12345);
        
        assertEquals(1.0, score, 0.001);
    }
    
    // ==================== 批量查询测试 ====================
    
    @Test
    @DisplayName("批量查询应返回所有端口评分")
    void testGetBatchPortRiskScores() {
        List<Integer> ports = Arrays.asList(22, 3389, 445, 12345);
        
        List<PortRiskConfig> configs = Arrays.asList(
            new PortRiskConfig(22, "SSH", 2.0, "REMOTE_ACCESS", "SSH"),
            new PortRiskConfig(3389, "RDP", 3.0, "REMOTE_ACCESS", "RDP"),
            new PortRiskConfig(445, "SMB", 3.0, "WINDOWS", "SMB")
        );
        when(repository.findByPortNumberIn(ports)).thenReturn(configs);
        
        Map<Integer, Double> scoreMap = service.getBatchPortRiskScores(ports);
        
        assertEquals(4, scoreMap.size());
        assertEquals(2.0, scoreMap.get(22), 0.001);
        assertEquals(3.0, scoreMap.get(3389), 0.001);
        assertEquals(3.0, scoreMap.get(445), 0.001);
        assertEquals(1.0, scoreMap.get(12345), 0.001);  // 未配置端口
    }
    
    @Test
    @DisplayName("空端口列表应返回空映射")
    void testGetBatchPortRiskScores_EmptyList() {
        Map<Integer, Double> scoreMap = service.getBatchPortRiskScores(Collections.emptyList());
        
        assertTrue(scoreMap.isEmpty());
    }
    
    // ==================== 端口权重计算测试 ====================
    
    @Test
    @DisplayName("高危端口组合应返回高权重")
    void testCalculatePortRiskWeight_HighRisk() {
        List<Integer> ports = Arrays.asList(3389, 445, 22);  // RDP + SMB + SSH
        
        List<PortRiskConfig> configs = Arrays.asList(
            new PortRiskConfig(3389, "RDP", 3.0, "REMOTE_ACCESS", "RDP"),
            new PortRiskConfig(445, "SMB", 3.0, "WINDOWS", "SMB"),
            new PortRiskConfig(22, "SSH", 2.0, "REMOTE_ACCESS", "SSH")
        );
        when(repository.findByPortNumberIn(ports)).thenReturn(configs);
        
        double weight = service.calculatePortRiskWeight(ports, 3);
        
        // 平均评分 = (3.0 + 3.0 + 2.0) / 3 = 2.67
        // 配置权重 = 1.0 + (2.67 / 5.0) = 1.534
        // 多样性权重 = 1.2 (3个端口)
        // 最终权重 = max(1.534, 1.2) = 1.534
        assertTrue(weight > 1.5);
        assertTrue(weight < 1.6);
    }
    
    @Test
    @DisplayName("低危端口应返回低权重")
    void testCalculatePortRiskWeight_LowRisk() {
        List<Integer> ports = Arrays.asList(80, 443);  // HTTP + HTTPS
        
        List<PortRiskConfig> configs = Arrays.asList(
            new PortRiskConfig(80, "HTTP", 1.5, "WEB", "HTTP"),
            new PortRiskConfig(443, "HTTPS", 1.2, "WEB", "HTTPS")
        );
        when(repository.findByPortNumberIn(ports)).thenReturn(configs);
        
        double weight = service.calculatePortRiskWeight(ports, 2);
        
        // 平均评分 = (1.5 + 1.2) / 2 = 1.35
        // 配置权重 = 1.0 + (1.35 / 5.0) = 1.27
        // 多样性权重 = 1.2 (2个端口)
        // 最终权重 = max(1.27, 1.2) = 1.27
        assertTrue(weight > 1.2);
        assertTrue(weight < 1.3);
    }
    
    @Test
    @DisplayName("大量端口应返回高权重")
    void testCalculatePortRiskWeight_ManyPorts() {
        List<Integer> ports = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            ports.add(i * 100);
        }
        
        when(repository.findByPortNumberIn(ports)).thenReturn(Collections.emptyList());
        
        double weight = service.calculatePortRiskWeight(ports, 25);
        
        // 多样性权重 = 2.0 (25个端口)
        assertEquals(2.0, weight, 0.001);
    }
    
    // ==================== 高危端口查询测试 ====================
    
    @Test
    @DisplayName("查询高危端口应返回评分>=阈值的端口")
    void testGetHighRiskPorts() {
        List<PortRiskConfig> highRiskPorts = Arrays.asList(
            new PortRiskConfig(3389, "RDP", 3.0, "REMOTE_ACCESS", "RDP"),
            new PortRiskConfig(445, "SMB", 3.0, "WINDOWS", "SMB"),
            new PortRiskConfig(23, "Telnet", 3.0, "REMOTE_ACCESS", "Telnet")
        );
        when(repository.findHighRiskPorts(2.5)).thenReturn(highRiskPorts);
        
        List<PortRiskConfig> result = service.getHighRiskPorts(2.5);
        
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(p -> p.getRiskScore() >= 2.5));
    }
    
    // ==================== 初始化测试 ====================
    
    @Test
    @DisplayName("首次初始化应创建默认配置")
    void testInitializeDefaultPorts_FirstTime() {
        when(repository.countConfiguredPorts()).thenReturn(0L);
        
        service.initializeDefaultPorts();
        
        verify(repository).saveAll(anyList());
    }
    
    @Test
    @DisplayName("已有配置应跳过初始化")
    void testInitializeDefaultPorts_AlreadyInitialized() {
        when(repository.countConfiguredPorts()).thenReturn(50L);
        
        service.initializeDefaultPorts();
        
        verify(repository, never()).saveAll(anyList());
    }
}
