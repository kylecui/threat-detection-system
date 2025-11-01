package com.threatdetection.ingestion.model;

import java.util.List;

/**
 * Phase 1A: 批量日志处理请求DTO
 * 支持批量提交多个日志进行处理
 */
public class BatchLogRequest {

    private List<String> logs;

    public BatchLogRequest() {}

    public BatchLogRequest(List<String> logs) {
        this.logs = logs;
    }

    public List<String> getLogs() {
        return logs;
    }

    public void setLogs(List<String> logs) {
        this.logs = logs;
    }

    /**
     * 验证请求的有效性
     */
    public boolean isValid() {
        return logs != null && !logs.isEmpty() && logs.size() <= 1000; // 限制批量大小
    }

    @Override
    public String toString() {
        return "BatchLogRequest{" +
                "logsCount=" + (logs != null ? logs.size() : 0) +
                '}';
    }
}