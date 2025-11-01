package com.threatdetection.ingestion.model;

import java.util.List;

/**
 * 导入结果模型
 *
 * <p>记录批量导入的处理结果，包括：
 * - 总日志数
 * - 成功处理数
 * - 重复事件数
 * - 错误数
 * - 处理时间
 * - 详细结果列表
 */
public class ImportResult {

    private final int totalLogs;
    private final int processedLogs;
    private final int duplicateLogs;
    private final int errorLogs;
    private final long processingTimeMs;
    private final boolean success;
    private final String errorMessage;
    private final List<String> details;

    // 私有构造函数，使用工厂方法创建
    private ImportResult(int totalLogs, int processedLogs, int duplicateLogs,
                        int errorLogs, long processingTimeMs, boolean success,
                        String errorMessage, List<String> details) {
        this.totalLogs = totalLogs;
        this.processedLogs = processedLogs;
        this.duplicateLogs = duplicateLogs;
        this.errorLogs = errorLogs;
        this.processingTimeMs = processingTimeMs;
        this.success = success;
        this.errorMessage = errorMessage;
        this.details = details;
    }

    /**
     * 创建成功的导入结果
     */
    public static ImportResult success(int totalLogs, int processedLogs, int duplicateLogs,
                                     int errorLogs, long processingTimeMs) {
        return new ImportResult(totalLogs, processedLogs, duplicateLogs, errorLogs,
                              processingTimeMs, true, null, null);
    }

    /**
     * 创建失败的导入结果
     */
    public static ImportResult error(int totalLogs, String errorMessage, long processingTimeMs) {
        return new ImportResult(totalLogs, 0, 0, totalLogs, processingTimeMs,
                              false, errorMessage, null);
    }

    // Getters
    public int getTotalLogs() {
        return totalLogs;
    }

    public int getProcessedLogs() {
        return processedLogs;
    }

    public int getDuplicateLogs() {
        return duplicateLogs;
    }

    public int getErrorLogs() {
        return errorLogs;
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public List<String> getDetails() {
        return details;
    }

    /**
     * 获取处理统计信息
     */
    public String getStatistics() {
        return String.format(
            "Total: %d, Processed: %d, Duplicates: %d, Errors: %d, Time: %dms",
            totalLogs, processedLogs, duplicateLogs, errorLogs, processingTimeMs
        );
    }

    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        if (totalLogs == 0) return 0.0;
        return (double) (processedLogs) / totalLogs * 100.0;
    }

    /**
     * 获取去重率
     */
    public double getDeduplicationRate() {
        if (totalLogs == 0) return 0.0;
        return (double) duplicateLogs / totalLogs * 100.0;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("ImportResult{success=true, %s}", getStatistics());
        } else {
            return String.format("ImportResult{success=false, total=%d, error='%s', time=%dms}",
                               totalLogs, errorMessage, processingTimeMs);
        }
    }
}