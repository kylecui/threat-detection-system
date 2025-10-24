package com.threatdetection.assessment.service;

import com.threatdetection.assessment.dto.AggregatedAttackData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 威胁评分计算器单元测试
 * 
 * @author Security Team
 * @version 2.0
 */
@ExtendWith(MockitoExtension.class)
class ThreatScoreCalculatorTest {
    
    @Mock
    private PortRiskService portRiskService;
    
    @Mock
    private IpSegmentWeightService ipSegmentWeightService;
    
    @Mock
    private IpSegmentWeightServiceV4 ipSegmentWeightServiceV4;

    private ThreatScoreCalculator calculator;
    
    @BeforeEach
    void setUp() {
        calculator = new ThreatScoreCalculator(portRiskService, ipSegmentWeightService, ipSegmentWeightServiceV4);
    }
    
    // ==================== 时间权重测试 ====================
    // 注意: 测试使用本地时间(中国时区 UTC+8)
    
    @Test
    @DisplayName("深夜时段权重 (00:00-06:00) 应为 1.2")
    void testTimeWeight_Midnight() {
        // UTC+8: 02:00 本地时间 = 18:00 前一天 UTC
        Instant midnight = Instant.parse("2025-01-14T18:00:00Z");
        assertEquals(1.2, calculator.calculateTimeWeight(midnight), 0.001);
    }
    
    @Test
    @DisplayName("早晨时段权重 (06:00-09:00) 应为 1.1")
    void testTimeWeight_Morning() {
        // UTC+8: 07:30 本地时间 = 23:30 前一天 UTC
        Instant morning = Instant.parse("2025-01-14T23:30:00Z");
        assertEquals(1.1, calculator.calculateTimeWeight(morning), 0.001);
    }
    
    @Test
    @DisplayName("工作时段权重 (09:00-17:00) 应为 1.0")
    void testTimeWeight_WorkHours() {
        // UTC+8: 14:00 本地时间 = 06:00 UTC
        Instant workHour = Instant.parse("2025-01-15T06:00:00Z");
        assertEquals(1.0, calculator.calculateTimeWeight(workHour), 0.001);
    }
    
    @Test
    @DisplayName("傍晚时段权重 (17:00-21:00) 应为 0.9")
    void testTimeWeight_Evening() {
        // UTC+8: 19:00 本地时间 = 11:00 UTC
        Instant evening = Instant.parse("2025-01-15T11:00:00Z");
        assertEquals(0.9, calculator.calculateTimeWeight(evening), 0.001);
    }
    
    @Test
    @DisplayName("夜间时段权重 (21:00-24:00) 应为 0.8")
    void testTimeWeight_Night() {
        // UTC+8: 22:30 本地时间 = 14:30 UTC
        Instant night = Instant.parse("2025-01-15T14:30:00Z");
        assertEquals(0.8, calculator.calculateTimeWeight(night), 0.001);
    }
    
    // ==================== IP权重测试 ====================
    
    @Test
    @DisplayName("单一IP权重应为 1.0")
    void testIpWeight_SingleIp() {
        assertEquals(1.0, calculator.calculateIpWeight(1), 0.001);
    }
    
    @Test
    @DisplayName("2-3个IP权重应为 1.3")
    void testIpWeight_FewIps() {
        assertEquals(1.3, calculator.calculateIpWeight(2), 0.001);
        assertEquals(1.3, calculator.calculateIpWeight(3), 0.001);
    }
    
    @Test
    @DisplayName("4-5个IP权重应为 1.5")
    void testIpWeight_MediumIps() {
        assertEquals(1.5, calculator.calculateIpWeight(4), 0.001);
        assertEquals(1.5, calculator.calculateIpWeight(5), 0.001);
    }
    
        @Test
    @DisplayName("6-10个IP权重应为 1.7")
    void testIpWeight_ManyIps() {
        assertEquals(1.7, calculator.calculateIpWeight(8), 0.001);
    }
    
    @Test
    @DisplayName("10+个IP权重应为 2.0")
    void testIpWeight_MassiveIps() {
        assertEquals(2.0, calculator.calculateIpWeight(11), 0.001);
        assertEquals(2.0, calculator.calculateIpWeight(100), 0.001);
    }
    
    // ==================== 端口权重测试 ====================
    
    @Test
    @DisplayName("单端口权重应为 1.0")
    void testPortWeight_SinglePort() {
        assertEquals(1.0, calculator.calculatePortWeight(1), 0.001);
    }
    
    @Test
    @DisplayName("2-3个端口权重应为 1.2")
    void testPortWeight_FewPorts() {
        assertEquals(1.2, calculator.calculatePortWeight(2), 0.001);
        assertEquals(1.2, calculator.calculatePortWeight(3), 0.001);
    }
    
    @Test
    @DisplayName("4-5个端口权重应为 1.4")
    void testPortWeight_MediumPorts() {
        assertEquals(1.4, calculator.calculatePortWeight(4), 0.001);
        assertEquals(1.4, calculator.calculatePortWeight(5), 0.001);
    }
    
    @Test
    @DisplayName("6-10个端口权重应为 1.6")
    void testPortWeight_ManyPorts() {
        assertEquals(1.6, calculator.calculatePortWeight(6), 0.001);
        assertEquals(1.6, calculator.calculatePortWeight(10), 0.001);
    }
    
        @Test
    @DisplayName("11-20个端口权重应为 1.8")
    void testPortWeight_LotsOfPorts() {
        assertEquals(1.8, calculator.calculatePortWeight(15), 0.001);
    }
    
    @Test
    @DisplayName("20+个端口权重应为 2.0")
    void testPortWeight_MassivePorts() {
        assertEquals(2.0, calculator.calculatePortWeight(21), 0.001);
        assertEquals(2.0, calculator.calculatePortWeight(100), 0.001);
    }
    
    // ==================== 设备权重测试 ====================
    
    @Test
    @DisplayName("单设备权重应为 1.0")
    void testDeviceWeight_SingleDevice() {
        assertEquals(1.0, calculator.calculateDeviceWeight(1), 0.001);
    }
    
    @Test
    @DisplayName("多设备权重应为 1.5")
    void testDeviceWeight_MultipleDevices() {
        assertEquals(1.5, calculator.calculateDeviceWeight(2), 0.001);
        assertEquals(1.5, calculator.calculateDeviceWeight(10), 0.001);
    }
    
    // ==================== 威胁等级测试 ====================
    
    @Test
    @DisplayName("评分 < 10 应判定为 INFO")
    void testThreatLevel_Info() {
        assertEquals("INFO", calculator.determineThreatLevel(5.0));
        assertEquals("INFO", calculator.determineThreatLevel(9.99));
    }
    
    @Test
    @DisplayName("评分 10-50 应判定为 LOW")
    void testThreatLevel_Low() {
        assertEquals("LOW", calculator.determineThreatLevel(10.01));
        assertEquals("LOW", calculator.determineThreatLevel(25.0));
        assertEquals("LOW", calculator.determineThreatLevel(50.0));
    }
    
    @Test
    @DisplayName("评分 50-100 应判定为 MEDIUM")
    void testThreatLevel_Medium() {
        assertEquals("MEDIUM", calculator.determineThreatLevel(50.01));
        assertEquals("MEDIUM", calculator.determineThreatLevel(75.0));
        assertEquals("MEDIUM", calculator.determineThreatLevel(100.0));
    }
    
    @Test
    @DisplayName("评分 100-200 应判定为 HIGH")
    void testThreatLevel_High() {
        assertEquals("HIGH", calculator.determineThreatLevel(100.01));
        assertEquals("HIGH", calculator.determineThreatLevel(150.0));
        assertEquals("HIGH", calculator.determineThreatLevel(200.0));
    }
    
    @Test
    @DisplayName("评分 > 200 应判定为 CRITICAL")
    void testThreatLevel_Critical() {
        assertEquals("CRITICAL", calculator.determineThreatLevel(200.01));
        assertEquals("CRITICAL", calculator.determineThreatLevel(1000.0));
        assertEquals("CRITICAL", calculator.determineThreatLevel(10000.0));
    }
    
    // ==================== 完整评分计算测试 ====================
    
    @Test
    @DisplayName("场景1: CRITICAL级别威胁 - 深夜大规模横向移动")
    void testCalculateThreatScore_CriticalThreat() {
        AggregatedAttackData data = AggregatedAttackData.builder()
            .customerId("customer-001")
            .attackMac("04:42:1a:8e:e3:65")
            .attackIp("192.168.75.188")
            .attackCount(150)
            .uniqueIps(5)
            .uniquePorts(3)
            .uniqueDevices(2)
            .timestamp(Instant.parse("2025-01-14T18:30:00Z"))  // 深夜 (UTC+8: 02:30)
            .build();
        
        // Mock IP段权重 (192.168.x.x 内网IP默认0.7权重)
        when(ipSegmentWeightService.getIpSegmentWeight("192.168.75.188"))
            .thenReturn(0.7);
        
        double score = calculator.calculateThreatScore(data);
        
        // 预期计算 (Phase 3增加IP段权重):
        // baseScore = 150 × 5 × 3 = 2250
        // timeWeight = 1.2 (深夜)
        // ipWeight = 1.5 (5个IP)
        // portWeight = 1.2 (3个端口)
        // deviceWeight = 1.5 (2个设备)
        // ipSegmentWeight = 0.7 (内网IP)
        // finalScore = 2250 × 1.2 × 1.5 × 1.2 × 1.5 × 0.7 = 5103.0
        
        assertEquals(5103.0, score, 0.1);
        assertEquals("CRITICAL", calculator.determineThreatLevel(score));
    }
    
    @Test
    @DisplayName("场景2: MEDIUM级别威胁 - 工作时间单目标探测")
    void testCalculateThreatScore_MediumThreat() {
        AggregatedAttackData data = AggregatedAttackData.builder()
            .customerId("customer-001")
            .attackMac("aa:bb:cc:dd:ee:ff")
            .attackIp("10.0.1.50")
            .attackCount(30)
            .uniqueIps(2)
            .uniquePorts(1)
            .uniqueDevices(1)
            .timestamp(Instant.parse("2025-01-15T06:30:00Z"))  // 工作时间 (UTC+8: 14:30)
            .build();
        
        // Mock IP段权重 (10.x.x.x 内网IP默认0.7权重)
        when(ipSegmentWeightService.getIpSegmentWeight("10.0.1.50"))
            .thenReturn(0.7);
        
        double score = calculator.calculateThreatScore(data);
        
        // 预期计算 (Phase 3增加IP段权重):
        // baseScore = 30 × 2 × 1 = 60
        // timeWeight = 1.0 (工作时间)
        // ipWeight = 1.3 (2个IP)
        // portWeight = 1.0 (1个端口)
        // deviceWeight = 1.0 (1个设备)
        // ipSegmentWeight = 0.7 (内网IP)
        // finalScore = 60 × 1.0 × 1.3 × 1.0 × 1.0 × 0.7 = 54.6
        
        assertEquals(54.6, score, 0.1);
        assertEquals("MEDIUM", calculator.determineThreatLevel(score));
    }
    
    @Test
    @DisplayName("场景3: LOW级别威胁 - 夜间小规模探测")
    void testCalculateThreatScore_LowThreat() {
        AggregatedAttackData data = AggregatedAttackData.builder()
            .customerId("customer-001")
            .attackMac("11:22:33:44:55:66")
            .attackIp("172.16.0.100")
            .attackCount(10)
            .uniqueIps(1)
            .uniquePorts(1)
            .uniqueDevices(1)
            .timestamp(Instant.parse("2025-01-15T14:00:00Z"))  // 夜间 (UTC+8: 22:00)
            .build();
        
        // Mock IP段权重
        when(ipSegmentWeightService.getIpSegmentWeight("172.16.0.100"))
            .thenReturn(0.7);
        
        double score = calculator.calculateThreatScore(data);
        
        // 预期计算 (Phase 3增加IP段权重):
        // baseScore = 10 × 1 × 1 = 10
        // timeWeight = 0.8 (夜间)
        // ipWeight = 1.0 (1个IP)
        // portWeight = 1.0 (1个端口)
        // deviceWeight = 1.0 (1个设备)
        // ipSegmentWeight = 0.7 (内网IP)
        // finalScore = 10 × 0.8 × 1.0 × 1.0 × 1.0 × 0.7 = 5.6
        
        assertEquals(5.6, score, 0.1);
        assertEquals("INFO", calculator.determineThreatLevel(score));  // < 10
    }
    
    @Test
    @DisplayName("场景4: HIGH级别威胁 - 工作时间广泛扫描")
    void testCalculateThreatScore_HighThreat() {
        AggregatedAttackData data = AggregatedAttackData.builder()
            .customerId("customer-001")
            .attackMac("66:77:88:99:aa:bb")
            .attackIp("10.0.2.50")
            .attackCount(50)
            .uniqueIps(4)
            .uniquePorts(6)
            .uniqueDevices(1)
            .timestamp(Instant.parse("2025-01-15T02:00:00Z"))  // 工作时间 (UTC+8: 10:00)
            .build();
        
        // Mock IP段权重
        when(ipSegmentWeightService.getIpSegmentWeight("10.0.2.50"))
            .thenReturn(0.7);
        
        double score = calculator.calculateThreatScore(data);
        
        // 预期计算 (Phase 3增加IP段权重):
        // baseScore = 50 × 4 × 6 = 1200
        // timeWeight = 1.0 (工作时间)
        // ipWeight = 1.5 (4个IP)
        // portWeight = 1.6 (6个端口)
        // deviceWeight = 1.0 (1个设备)
        // ipSegmentWeight = 0.7 (内网IP)
        // finalScore = 1200 × 1.0 × 1.5 × 1.6 × 1.0 × 0.7 = 2016.0
        
        assertEquals(2016.0, score, 0.1);
        assertEquals("CRITICAL", calculator.determineThreatLevel(score));
    }
    
    @Test
    @DisplayName("无效数据应返回 0.0")
    void testCalculateThreatScore_InvalidData() {
        AggregatedAttackData invalidData = AggregatedAttackData.builder()
            .customerId(null)  // 缺少customerId
            .attackMac("aa:bb:cc:dd:ee:ff")
            .attackCount(10)
            .build();
        
        double score = calculator.calculateThreatScore(invalidData);
        assertEquals(0.0, score, 0.001);
    }
}
