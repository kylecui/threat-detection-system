package com.threatdetection.ingestion.model;

import java.util.List;

/**
 * 导入请求模型
 *
 * <p>封装场景感知导入的请求参数
 */
public class ImportRequest {

    private ImportMode mode;
    private String customerId;
    private List<String> logs;

    // 默认构造函数
    public ImportRequest() {}

    // 全参数构造函数
    public ImportRequest(ImportMode mode, String customerId, List<String> logs) {
        this.mode = mode;
        this.customerId = customerId;
        this.logs = logs;
    }

    // Getters and Setters
    public ImportMode getMode() {
        return mode;
    }

    public void setMode(ImportMode mode) {
        this.mode = mode;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
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
        if (mode == null) {
            return false;
        }

        if (logs == null || logs.isEmpty()) {
            return false;
        }

        // COMPLETION模式必须提供customerId
        if (mode == ImportMode.COMPLETION) {
            return customerId != null && !customerId.trim().isEmpty();
        }

        // 其他模式customerId是可选的
        return logs.size() <= 10000; // 限制批量大小
    }

    @Override
    public String toString() {
        return String.format("ImportRequest{mode=%s, customerId='%s', logCount=%d}",
                           mode, customerId, logs != null ? logs.size() : 0);
    }
}