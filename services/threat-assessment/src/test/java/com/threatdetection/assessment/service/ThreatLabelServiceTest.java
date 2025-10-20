package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.ThreatLabel;
import com.threatdetection.assessment.repository.ThreatLabelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 威胁标签服务单元测试
 * 
 * @author Security Team
 * @version 2.0
 * @since Phase 5
 */
@ExtendWith(MockitoExtension.class)
class ThreatLabelServiceTest {
    
    @Mock
    private ThreatLabelRepository repository;
    
    @InjectMocks
    private ThreatLabelService threatLabelService;
    
    private ThreatLabel aptLabel;
    private ThreatLabel ransomwareLabel;
    private ThreatLabel scanLabel;
    
    @BeforeEach
    void setUp() {
        // APT标签
        aptLabel = ThreatLabel.builder()
            .id(1L)
            .labelCode("APT_LATERAL_MOVE")
            .labelName("APT横向移动")
            .category("APT")
            .severity("CRITICAL")
            .description("使用RDP/SMB等协议进行内网横向移动")
            .createdAt(Instant.now())
            .build();
        
        // 勒索软件标签
        ransomwareLabel = ThreatLabel.builder()
            .id(2L)
            .labelCode("RANSOMWARE_SMB")
            .labelName("勒索软件SMB传播")
            .category("RANSOMWARE")
            .severity("CRITICAL")
            .description("通过SMB协议传播的勒索软件")
            .createdAt(Instant.now())
            .build();
        
        // 扫描标签
        scanLabel = ThreatLabel.builder()
            .id(3L)
            .labelCode("SCAN_PORT_FULL")
            .labelName("全端口扫描")
            .category("SCANNING")
            .severity("MEDIUM")
            .description("扫描大量端口(20+)")
            .createdAt(Instant.now())
            .build();
    }
    
    // ==================== 标签查询测试 ====================
    
    @Test
    @DisplayName("根据标签代码查询应返回正确的标签")
    void testGetLabelByCode_Success() {
        // Given
        when(repository.findByLabelCode("APT_LATERAL_MOVE"))
            .thenReturn(Optional.of(aptLabel));
        
        // When
        Optional<ThreatLabel> result = threatLabelService.getLabelByCode("APT_LATERAL_MOVE");
        
        // Then
        assertTrue(result.isPresent());
        assertEquals("APT_LATERAL_MOVE", result.get().getLabelCode());
        assertEquals("CRITICAL", result.get().getSeverity());
        verify(repository, times(1)).findByLabelCode("APT_LATERAL_MOVE");
    }
    
    @Test
    @DisplayName("查询不存在的标签代码应返回空")
    void testGetLabelByCode_NotFound() {
        // Given
        when(repository.findByLabelCode("UNKNOWN_LABEL"))
            .thenReturn(Optional.empty());
        
        // When
        Optional<ThreatLabel> result = threatLabelService.getLabelByCode("UNKNOWN_LABEL");
        
        // Then
        assertFalse(result.isPresent());
    }
    
    @Test
    @DisplayName("空标签代码应返回空")
    void testGetLabelByCode_EmptyCode() {
        // When
        Optional<ThreatLabel> result1 = threatLabelService.getLabelByCode(null);
        Optional<ThreatLabel> result2 = threatLabelService.getLabelByCode("");
        
        // Then
        assertFalse(result1.isPresent());
        assertFalse(result2.isPresent());
        verify(repository, never()).findByLabelCode(anyString());
    }
    
    @Test
    @DisplayName("根据分类查询应返回该分类的所有标签")
    void testGetLabelsByCategory() {
        // Given
        List<ThreatLabel> aptLabels = Arrays.asList(aptLabel);
        when(repository.findByCategory("APT"))
            .thenReturn(aptLabels);
        
        // When
        List<ThreatLabel> result = threatLabelService.getLabelsByCategory("APT");
        
        // Then
        assertEquals(1, result.size());
        assertEquals("APT", result.get(0).getCategory());
    }
    
    @Test
    @DisplayName("查询高危标签应返回CRITICAL和HIGH级别的标签")
    void testGetHighSeverityLabels() {
        // Given
        List<ThreatLabel> highLabels = Arrays.asList(aptLabel, ransomwareLabel);
        when(repository.findHighSeverityLabels())
            .thenReturn(highLabels);
        
        // When
        List<ThreatLabel> result = threatLabelService.getHighSeverityLabels();
        
        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(l -> 
            "CRITICAL".equals(l.getSeverity()) || "HIGH".equals(l.getSeverity())));
    }
    
    // ==================== 自动推荐标签测试 ====================
    
    @Test
    @DisplayName("RDP端口应推荐RDP相关标签")
    void testRecommendLabels_RdpPort() {
        // Given
        List<Integer> ports = Arrays.asList(3389);
        
        // When
        List<String> labels = threatLabelService.recommendLabels(
            1, 2, 50.0, ports, null
        );
        
        // Then
        assertTrue(labels.contains("LATERAL_RDP"));
        assertTrue(labels.contains("APT_LATERAL_MOVE"));
    }
    
    @Test
    @DisplayName("SMB端口应推荐SMB相关标签")
    void testRecommendLabels_SmbPort() {
        // Given
        List<Integer> ports = Arrays.asList(445);
        
        // When - uniqueIps=5触发APT_LATERAL_MOVE
        List<String> labels = threatLabelService.recommendLabels(
            1, 5, 80.0, ports, null
        );
        
        // Then
        assertTrue(labels.contains("LATERAL_SMB"));
        assertTrue(labels.contains("RANSOMWARE_SMB"), 
            "uniqueIps>1 should trigger RANSOMWARE_SMB");
        assertTrue(labels.contains("APT_LATERAL_MOVE"), 
            "uniqueIps>=5 should trigger APT_LATERAL_MOVE");
    }
    
    @Test
    @DisplayName("SSH端口应推荐SSH标签")
    void testRecommendLabels_SshPort() {
        // Given
        List<Integer> ports = Arrays.asList(22);
        
        // When
        List<String> labels = threatLabelService.recommendLabels(
            1, 1, 30.0, ports, null
        );
        
        // Then
        assertTrue(labels.contains("LATERAL_SSH"));
    }
    
    @Test
    @DisplayName("数据库端口应推荐数据窃取标签")
    void testRecommendLabels_DatabasePort() {
        // Given
        List<Integer> ports = Arrays.asList(3306, 5432);
        
        // When
        List<String> labels = threatLabelService.recommendLabels(
            2, 1, 40.0, ports, null
        );
        
        // Then
        assertTrue(labels.contains("DATA_EXFIL_DB"));
    }
    
    @Test
    @DisplayName("20+端口应推荐全端口扫描标签")
    void testRecommendLabels_FullPortScan() {
        // Given
        int uniquePorts = 25;
        
        // When
        List<String> labels = threatLabelService.recommendLabels(
            uniquePorts, 1, 100.0, null, null
        );
        
        // Then
        assertTrue(labels.contains("SCAN_PORT_FULL"));
        assertTrue(labels.contains("APT_RECON"));
    }
    
    @Test
    @DisplayName("5+端口应推荐常用端口扫描标签")
    void testRecommendLabels_CommonPortScan() {
        // Given
        int uniquePorts = 7;
        
        // When
        List<String> labels = threatLabelService.recommendLabels(
            uniquePorts, 1, 50.0, null, null
        );
        
        // Then
        assertTrue(labels.contains("SCAN_PORT_COMMON"));
    }
    
    @Test
    @DisplayName("5+IP应推荐横向移动标签")
    void testRecommendLabels_LateralMovement() {
        // Given
        int uniqueIps = 6;
        
        // When
        List<String> labels = threatLabelService.recommendLabels(
            1, uniqueIps, 80.0, null, null
        );
        
        // Then
        assertTrue(labels.contains("APT_LATERAL_MOVE"));
    }
    
    @Test
    @DisplayName("恶意IP段应推荐恶意软件标签")
    void testRecommendLabels_MaliciousSegment() {
        // Given
        String ipCategory = "MALICIOUS";
        
        // When
        List<String> labels = threatLabelService.recommendLabels(
            1, 1, 50.0, null, ipCategory
        );
        
        // Then
        assertTrue(labels.contains("MALWARE_BOTNET"));
        assertTrue(labels.contains("APT_C2_COMM"));
    }
    
    @Test
    @DisplayName("Tor出口节点应推荐Tor标签")
    void testRecommendLabels_TorExit() {
        // Given
        String ipCategory = "TOR_EXIT";
        
        // When
        List<String> labels = threatLabelService.recommendLabels(
            1, 1, 40.0, null, ipCategory
        );
        
        // Then
        assertTrue(labels.contains("NETWORK_TOR"));
    }
    
    @Test
    @DisplayName("VPN提供商应推荐VPN标签")
    void testRecommendLabels_VpnProvider() {
        // Given
        String ipCategory = "VPN_PROVIDER";
        
        // When
        List<String> labels = threatLabelService.recommendLabels(
            1, 1, 30.0, null, ipCategory
        );
        
        // Then
        assertTrue(labels.contains("NETWORK_VPN"));
    }
    
    @Test
    @DisplayName("高危地区应推荐高危地区标签")
    void testRecommendLabels_HighRiskRegion() {
        // Given
        String ipCategory = "HIGH_RISK_REGION";
        
        // When
        List<String> labels = threatLabelService.recommendLabels(
            1, 1, 60.0, null, ipCategory
        );
        
        // Then
        assertTrue(labels.contains("NETWORK_HIGH_RISK_GEO"));
    }
    
    @Test
    @DisplayName("高威胁评分应推荐APT标签")
    void testRecommendLabels_HighThreatScore() {
        // Given
        double threatScore = 250.0;
        
        // When
        List<String> labels = threatLabelService.recommendLabels(
            1, 1, threatScore, null, null
        );
        
        // Then
        assertTrue(labels.contains("APT_C2_COMM"));
    }
    
    @Test
    @DisplayName("综合场景应推荐多个标签")
    void testRecommendLabels_ComplexScenario() {
        // Given
        List<Integer> ports = Arrays.asList(3389, 445, 22);
        String ipCategory = "HIGH_RISK_REGION";
        
        // When
        List<String> labels = threatLabelService.recommendLabels(
            3, 5, 150.0, ports, ipCategory
        );
        
        // Then
        assertTrue(labels.contains("LATERAL_RDP"));
        assertTrue(labels.contains("LATERAL_SMB"));
        assertTrue(labels.contains("LATERAL_SSH"));
        assertTrue(labels.contains("APT_LATERAL_MOVE"));
        assertTrue(labels.contains("NETWORK_HIGH_RISK_GEO"));
        // 去重验证
        assertEquals(labels.size(), new HashSet<>(labels).size());
    }
    
    // ==================== 批量操作测试 ====================
    
    @Test
    @DisplayName("批量查询标签应返回所有存在的标签")
    void testGetLabelsByCodes() {
        // Given
        List<String> codes = Arrays.asList("APT_LATERAL_MOVE", "RANSOMWARE_SMB");
        when(repository.findByLabelCode("APT_LATERAL_MOVE"))
            .thenReturn(Optional.of(aptLabel));
        when(repository.findByLabelCode("RANSOMWARE_SMB"))
            .thenReturn(Optional.of(ransomwareLabel));
        
        // When
        List<ThreatLabel> result = threatLabelService.getLabelsByCodes(codes);
        
        // Then
        assertEquals(2, result.size());
    }
    
    @Test
    @DisplayName("空标签列表应返回空列表")
    void testGetLabelsByCodes_EmptyList() {
        // When
        List<ThreatLabel> result1 = threatLabelService.getLabelsByCodes(null);
        List<ThreatLabel> result2 = threatLabelService.getLabelsByCodes(Collections.emptyList());
        
        // Then
        assertTrue(result1.isEmpty());
        assertTrue(result2.isEmpty());
    }
    
    // ==================== 统计测试 ====================
    
    @Test
    @DisplayName("统计分类应返回正确的映射")
    void testCountByCategory() {
        // Given
        List<Object[]> stats = Arrays.asList(
            new Object[]{"APT", 5L},
            new Object[]{"RANSOMWARE", 4L},
            new Object[]{"SCANNING", 5L}
        );
        when(repository.countByCategory()).thenReturn(stats);
        
        // When
        Map<String, Long> result = threatLabelService.countByCategory();
        
        // Then
        assertEquals(3, result.size());
        assertEquals(5L, result.get("APT"));
        assertEquals(4L, result.get("RANSOMWARE"));
        assertEquals(5L, result.get("SCANNING"));
    }
    
    // ==================== 初始化测试 ====================
    
    @Test
    @DisplayName("已有标签时应跳过初始化")
    void testInitializeDefaultLabels_AlreadyInitialized() {
        // Given
        when(repository.count()).thenReturn(50L);
        when(repository.countByCategory()).thenReturn(Arrays.asList(
            new Object[]{"APT", 5L},
            new Object[]{"RANSOMWARE", 4L}
        ));
        
        // When
        threatLabelService.initializeDefaultLabels();
        
        // Then
        verify(repository, times(1)).count();
        verify(repository, times(1)).countByCategory();
    }
    
    @Test
    @DisplayName("无标签时应警告")
    void testInitializeDefaultLabels_NoLabels() {
        // Given
        when(repository.count()).thenReturn(0L);
        
        // When
        threatLabelService.initializeDefaultLabels();
        
        // Then
        verify(repository, times(1)).count();
        verify(repository, never()).countByCategory();
    }
}
