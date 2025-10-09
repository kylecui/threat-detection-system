package com.threatdetection.ingestion.controller;

import com.threatdetection.ingestion.model.BatchLogRequest;
import com.threatdetection.ingestion.model.BatchLogResponse;
import com.threatdetection.ingestion.service.AsyncBatchLogIngestionService;
import com.threatdetection.ingestion.service.LogParserService;
import com.threatdetection.ingestion.service.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import io.micrometer.core.instrument.Timer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Phase 1A: LogIngestionController 单元测试
 * 测试批量处理接口和监控指标集成
 */
class LogIngestionControllerTest {

    @Mock
    private LogParserService logParserService;

    @Mock
    private AsyncBatchLogIngestionService batchIngestionService;

    @Mock
    private MetricsService metricsService;

    private LogIngestionController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock Timer.Sample to avoid NPE
        Timer.Sample mockSample = mock(Timer.Sample.class);
        when(metricsService.startBatchProcessingTimer()).thenReturn(mockSample);

        controller = new LogIngestionController(
            logParserService,
            null, // kafkaProducerService not needed for these tests
            batchIngestionService,
            metricsService
        );
    }

    @Test
    @DisplayName("Phase 1A: 测试有效的批量请求")
    void testValidBatchRequest() {
        // Given
        List<String> logs = Arrays.asList(
            "valid attack log 1",
            "valid attack log 2",
            "valid status log 1"
        );
        BatchLogRequest request = new BatchLogRequest(logs);

        BatchLogResponse mockResponse = new BatchLogResponse(3, 3, 0, null, 150L);
        when(batchIngestionService.processBatch(anyList())).thenReturn(mockResponse);

        // When
        ResponseEntity<BatchLogResponse> response = controller.ingestBatch(request);

        // Then
        assertEquals(200, response.getStatusCodeValue(), "应该返回200 OK");
        assertNotNull(response.getBody(), "响应体不应该为null");
        assertEquals(3, response.getBody().getTotalCount(), "总数应该正确");
        assertEquals(3, response.getBody().getSuccessCount(), "成功数应该正确");
        assertEquals(0, response.getBody().getErrorCount(), "错误数应该正确");

        verify(metricsService).recordBatchRequest();
        verify(batchIngestionService).processBatch(anyList());
    }

    @Test
    @DisplayName("Phase 1A: 测试无效的批量请求 - 空日志列表")
    void testInvalidBatchRequestEmptyLogs() {
        // Given
        BatchLogRequest request = new BatchLogRequest(Arrays.asList());

        // When
        ResponseEntity<BatchLogResponse> response = controller.ingestBatch(request);

        // Then
        assertEquals(400, response.getStatusCodeValue(), "应该返回400 Bad Request");
        assertNotNull(response.getBody(), "响应体不应该为null");
        // 对于空请求，errorCount 等于 totalCount (0)
        assertEquals(0, response.getBody().getTotalCount(), "总数应该为0");
        assertEquals(0, response.getBody().getErrorCount(), "错误数应该等于总数");
    }

    @Test
    @DisplayName("Phase 1A: 测试无效的批量请求 - 超过限制")
    void testInvalidBatchRequestTooManyLogs() {
        // Given
        List<String> tooManyLogs = Arrays.asList(new String[1001]); // 超过1000限制
        Arrays.fill(tooManyLogs.toArray(new String[0]), "test log");
        BatchLogRequest request = new BatchLogRequest(tooManyLogs);

        // When
        ResponseEntity<BatchLogResponse> response = controller.ingestBatch(request);

        // Then
        assertEquals(400, response.getStatusCodeValue(), "应该返回400 Bad Request");
        assertNotNull(response.getBody(), "响应体不应该为null");
        assertTrue(response.getBody().getResults().get(0).getErrorMessage()
                .contains("batch size must be 1-1000"), "应该返回正确的错误信息");
    }

    @Test
    @DisplayName("Phase 1A: 测试批量处理异常")
    void testBatchProcessingException() {
        // Given
        List<String> logs = Arrays.asList("log1", "log2");
        BatchLogRequest request = new BatchLogRequest(logs);

        when(batchIngestionService.processBatch(anyList()))
            .thenThrow(new RuntimeException("Processing failed"));

        // When
        ResponseEntity<BatchLogResponse> response = controller.ingestBatch(request);

        // Then
        assertEquals(500, response.getStatusCodeValue(), "应该返回500 Internal Server Error");
        assertNotNull(response.getBody(), "响应体不应该为null");
        assertEquals(2, response.getBody().getTotalCount(), "总数应该正确");
        assertEquals(0, response.getBody().getSuccessCount(), "成功数应该为0");
        assertEquals(2, response.getBody().getErrorCount(), "错误数应该等于总数");
    }

    @Test
    @DisplayName("Phase 1A: 测试部分成功的批量处理")
    void testPartialSuccessBatchProcessing() {
        // Given
        List<String> logs = Arrays.asList("log1", "log2", "log3");
        BatchLogRequest request = new BatchLogRequest(logs);

        BatchLogResponse mockResponse = new BatchLogResponse(3, 2, 1, null, 200L);
        when(batchIngestionService.processBatch(anyList())).thenReturn(mockResponse);

        // When
        ResponseEntity<BatchLogResponse> response = controller.ingestBatch(request);

        // Then
        assertEquals(200, response.getStatusCodeValue(), "部分成功应该返回200 OK");
        assertNotNull(response.getBody(), "响应体不应该为null");
        assertEquals(3, response.getBody().getTotalCount(), "总数应该正确");
        assertEquals(2, response.getBody().getSuccessCount(), "成功数应该正确");
        assertEquals(1, response.getBody().getErrorCount(), "错误数应该正确");
    }

    @Test
    @DisplayName("Phase 1A: 测试获取解析统计信息")
    void testGetParseStatistics() {
        // When
        ResponseEntity<?> response = controller.getParseStatistics();

        // Then
        assertEquals(200, response.getStatusCodeValue(), "应该返回200 OK");
        verify(logParserService).getParseStatistics();
    }

    @Test
    @DisplayName("Phase 1A: 测试重置解析统计信息")
    void testResetParseStatistics() {
        // When
        ResponseEntity<String> response = controller.resetParseStatistics();

        // Then
        assertEquals(200, response.getStatusCodeValue(), "应该返回200 OK");
        assertEquals("Statistics reset successfully", response.getBody(), "应该返回成功消息");
        verify(logParserService).resetStatistics();
    }

    @Test
    @DisplayName("Phase 1A: 测试健康检查端点")
    void testHealthEndpoint() {
        // When
        ResponseEntity<String> response = controller.health();

        // Then
        assertEquals(200, response.getStatusCodeValue(), "应该返回200 OK");
        assertEquals("Log Ingestion Service is healthy", response.getBody(), "应该返回健康消息");
    }
}