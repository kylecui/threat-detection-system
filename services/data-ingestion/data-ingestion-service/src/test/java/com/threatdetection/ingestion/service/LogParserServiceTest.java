package com.threatdetection.ingestion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Optional;

import com.threatdetection.ingestion.model.AttackEvent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1A: LogParserService 单元测试
 * 测试增强的错误处理、数据验证和解析功能
 */
class LogParserServiceTest {

    @Mock
    private DevSerialToCustomerMappingService mappingService;

    private LogParserService logParserService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        logParserService = new LogParserService(mappingService);
    }

    @Test
    @DisplayName("Phase 1A: 测试有效攻击日志解析")
    void testParseValidAttackLog() {
        // Given
        String validAttackLog = "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1," +
                "attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200," +
                "response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600," +
                "eth_type=2048,ip_type=6";

        // When
        Optional<Object> result = logParserService.parseLog(validAttackLog);

        // Then
        assertTrue(result.isPresent(), "应该成功解析有效的攻击日志");
        assertTrue(result.get() instanceof com.threatdetection.ingestion.model.AttackEvent,
                "应该返回AttackEvent对象");
    }

    @Test
    @DisplayName("Phase 1A: 测试有效状态日志解析")
    void testParseValidStatusLog() {
        // Given
        String validStatusLog = "syslog_version=1.10.0,dev_serial=ABC123,log_type=2," +
                "sentry_count=5,real_host_count=10,dev_start_time=1728462000," +
                "dev_end_time=1728465600,time=2025-10-09 10:00:00";

        // When
        Optional<Object> result = logParserService.parseLog(validStatusLog);

        // Then
        assertTrue(result.isPresent(), "应该成功解析有效的状态日志");
        assertTrue(result.get() instanceof com.threatdetection.ingestion.model.StatusEvent,
                "应该返回StatusEvent对象");
    }

    @Test
    @DisplayName("Phase 1A: 测试JSON格式日志解析")
    void testParseJsonFormatLog() {
        // Given
        String jsonLog = "{\"@timestamp\":\"2025-10-09T10:00:00.000Z\",\"host\":\"test-sensor-01\"," +
                "\"message\":\"syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1," +
                "attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200," +
                "response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600," +
                "eth_type=2048,ip_type=6\",\"type\":\"threat-log\"}";

        // When
        Optional<Object> result = logParserService.parseLog(jsonLog);

        // Then
        assertTrue(result.isPresent(), "应该成功解析JSON格式的日志");
    }

    @Test
    @DisplayName("Phase 1A: 测试无效输入验证")
    void testInvalidInputValidation() {
        // Test null input
        assertFalse(logParserService.parseLog(null).isPresent(), "null输入应该被拒绝");

        // Test empty input
        assertFalse(logParserService.parseLog("").isPresent(), "空字符串应该被拒绝");

        // Test input without required fields
        assertFalse(logParserService.parseLog("invalid log content").isPresent(),
                "缺少必需字段的日志应该被拒绝");

        // Test oversized input
        String oversizedLog = "x".repeat(10001);
        assertFalse(logParserService.parseLog(oversizedLog).isPresent(),
                "超大日志应该被拒绝");
    }

    @Test
    @DisplayName("Phase 1A: 测试数据验证 - 无效IP地址")
    void testInvalidIpAddress() {
        // Given
        String invalidIpLog = "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1," +
                "attack_mac=00:11:22:33:44:55,attack_ip=999.999.999.999,response_ip=192.168.1.200," +
                "response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600," +
                "eth_type=2048,ip_type=6";

        // When
        Optional<Object> result = logParserService.parseLog(invalidIpLog);

        // Then
        assertFalse(result.isPresent(), "无效IP地址应该导致解析失败");
    }

    @Test
    @DisplayName("Phase 1A: 测试数据验证 - 无效MAC地址")
    void testInvalidMacAddress() {
        // Given
        String invalidMacLog = "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1," +
                "attack_mac=invalid-mac,attack_ip=192.168.1.100,response_ip=192.168.1.200," +
                "response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600," +
                "eth_type=2048,ip_type=6";

        // When
        Optional<Object> result = logParserService.parseLog(invalidMacLog);

        // Then
        assertFalse(result.isPresent(), "无效MAC地址应该导致解析失败");
    }

    @Test
    @DisplayName("Phase 1A: 测试数据验证 - 特殊端口号（超出标准范围）")
    void testSpecialPortNumbers() {
        // 测试超出标准范围但有效的端口号
        String[] specialPortLogs = {
            "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1," +
            "attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200," +
            "response_port=65536,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600," +
            "eth_type=2048,ip_type=6",

            "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1," +
            "attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200," +
            "response_port=65537,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600," +
            "eth_type=2048,ip_type=6",

            "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1," +
            "attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200," +
            "response_port=0,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600," +
            "eth_type=2048,ip_type=6",

            "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1," +
            "attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200," +
            "response_port=-1,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600," +
            "eth_type=2048,ip_type=6"
        };

        for (String log : specialPortLogs) {
            // When
            Optional<Object> result = logParserService.parseLog(log);

            // Then
            assertTrue(result.isPresent(), "特殊端口号应该被接受: " + log);
            assertTrue(result.get() instanceof AttackEvent, "应该解析为AttackEvent");

            AttackEvent event = (AttackEvent) result.get();
            // 验证端口值被正确保留
            int expectedPort = extractPortFromLog(log);
            assertEquals(expectedPort, event.getResponsePort(), "端口值应该被正确保留");
        }
    }

    private int extractPortFromLog(String log) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("response_port=(-?\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(log);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }

    @Test
    @DisplayName("Phase 1A: 测试解析统计信息")
    void testParseStatistics() {
        // Given
        String validAttackLog = "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1," +
                "attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200," +
                "response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600," +
                "eth_type=2048,ip_type=6";

        String invalidLog = "invalid log content";

        // When
        logParserService.parseLog(validAttackLog); // Should succeed
        logParserService.parseLog(invalidLog);     // Should fail

        // Then
        Map<String, Integer> stats = logParserService.getParseStatistics();
        assertTrue(stats.getOrDefault("attack_events_parsed", 0) > 0, "应该记录成功解析的攻击事件");
        assertTrue(stats.getOrDefault("invalid_input", 0) > 0, "应该记录输入验证失败");

        // Test reset
        logParserService.resetStatistics();
        Map<String, Integer> resetStats = logParserService.getParseStatistics();
        assertTrue(resetStats.isEmpty() || resetStats.values().stream().allMatch(v -> v == 0),
                "重置后统计信息应该清零");
    }

    @Test
    @DisplayName("Phase 1A: 测试异常处理")
    void testExceptionHandling() {
        // Given - 构造一个会导致NumberFormatException的日志 (数字太大)
        String malformedLog = "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1," +
                "attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200," +
                "response_port=999999999999999999,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600," +
                "eth_type=2048,ip_type=6";

        // When
        Optional<Object> result = logParserService.parseLog(malformedLog);

        // Then
        assertFalse(result.isPresent(), "解析异常应该被优雅处理，返回Optional.empty");

        // Verify statistics are updated
        Map<String, Integer> stats = logParserService.getParseStatistics();
        assertTrue(stats.getOrDefault("unexpected_errors", 0) > 0, "应该记录意外错误");
    }
}