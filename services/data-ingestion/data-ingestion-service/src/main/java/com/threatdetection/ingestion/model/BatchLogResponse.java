package com.threatdetection.ingestion.model;

import java.util.List;

/**
 * Phase 1A: 批量日志处理响应DTO
 * 包含处理结果统计和详细的处理结果
 */
public class BatchLogResponse {

    private int totalCount;
    private int successCount;
    private int errorCount;
    private List<IngestionResult> results;
    private long processingTimeMs;

    public BatchLogResponse() {}

    public BatchLogResponse(int totalCount, int successCount, int errorCount,
                          List<IngestionResult> results, long processingTimeMs) {
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.errorCount = errorCount;
        this.results = results;
        this.processingTimeMs = processingTimeMs;
    }

    // Getters and setters
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }

    public List<IngestionResult> getResults() { return results; }
    public void setResults(List<IngestionResult> results) { this.results = results; }

    public long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }

    @Override
    public String toString() {
        return "BatchLogResponse{" +
                "totalCount=" + totalCount +
                ", successCount=" + successCount +
                ", errorCount=" + errorCount +
                ", processingTimeMs=" + processingTimeMs +
                '}';
    }
}