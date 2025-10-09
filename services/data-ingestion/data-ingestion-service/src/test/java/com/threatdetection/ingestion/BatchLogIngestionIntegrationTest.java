package com.threatdetection.ingestion;

import com.threatdetection.ingestion.model.BatchLogRequest;
import com.threatdetection.ingestion.model.BatchLogResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

import org.springframework.context.annotation.Import;

/**
 * Phase 1A: 集成测试
 * 测试完整的批量日志处理流程
 */
@SpringBootTest
@AutoConfigureWebMvc
@AutoConfigureMockMvc
@Import(TestConfig.class)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092"
})
class BatchLogIngestionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Phase 1A: 集成测试 - 批量处理有效日志")
    void testBatchIngestionWithValidLogs() throws Exception {
        // Given
        String validAttackLog = "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1," +
                "attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200," +
                "response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600," +
                "eth_type=2048,ip_type=6";

        String validStatusLog = "syslog_version=1.10.0,dev_serial=ABC123,log_type=2," +
                "sentry_count=5,real_host_count=10,dev_start_time=1728462000," +
                "dev_end_time=1728465600,time=2025-10-09 10:00:00";

        BatchLogRequest request = new BatchLogRequest(
            Arrays.asList(validAttackLog, validStatusLog)
        );

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/v1/logs/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "logs": [
                            "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1,attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200,response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600,eth_type=2048,ip_type=6",
                            "syslog_version=1.10.0,dev_serial=ABC123,log_type=2,sentry_count=5,real_host_count=10,dev_start_time=1728462000,dev_end_time=1728465600,time=2025-10-09 10:00:00"
                        ]
                    }
                    """))
                .andExpect(status().isOk())
                .andReturn();

        // Verify response structure
        String responseContent = result.getResponse().getContentAsString();
        assertTrue(responseContent.contains("totalCount"), "响应应该包含总数");
        assertTrue(responseContent.contains("successCount"), "响应应该包含成功数");
        assertTrue(responseContent.contains("results"), "响应应该包含结果列表");
    }

    @Test
    @DisplayName("Phase 1A: 集成测试 - 批量处理无效日志")
    void testBatchIngestionWithInvalidLogs() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/logs/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "logs": [
                            "invalid log content",
                            ""
                        ]
                    }
                    """))
                .andExpect(status().isBadRequest())
                .andReturn();
    }

    @Test
    @DisplayName("Phase 1A: 集成测试 - 批量处理空请求")
    void testBatchIngestionWithEmptyRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/logs/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "logs": []
                    }
                    """))
                .andExpect(status().isBadRequest())
                .andReturn();
    }

    @Test
    @DisplayName("Phase 1A: 集成测试 - 获取解析统计信息")
    void testGetParseStatistics() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/logs/stats"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
    }

    @Test
    @DisplayName("Phase 1A: 集成测试 - 重置解析统计信息")
    void testResetParseStatistics() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/logs/stats/reset"))
                .andExpect(status().isOk())
                .andExpect(content().string("Statistics reset successfully"))
                .andReturn();
    }

    @Test
    @DisplayName("Phase 1A: 集成测试 - 健康检查")
    void testHealthCheck() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/logs/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Log Ingestion Service is healthy"))
                .andReturn();
    }

    @Test
    @DisplayName("Phase 1A: 集成测试 - 单条日志处理")
    void testSingleLogIngestion() throws Exception {
        // Given
        String validLog = "syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1," +
                "attack_mac=00:11:22:33:44:55,attack_ip=192.168.1.100,response_ip=192.168.1.200," +
                "response_port=22,line_id=1,Iface_type=1,Vlan_id=0,log_time=1728465600," +
                "eth_type=2048,ip_type=6";

        // When & Then
        mockMvc.perform(post("/api/v1/logs/ingest")
                .contentType(MediaType.TEXT_PLAIN)
                .content(validLog))
                .andExpect(status().isOk())
                .andReturn();
    }
}