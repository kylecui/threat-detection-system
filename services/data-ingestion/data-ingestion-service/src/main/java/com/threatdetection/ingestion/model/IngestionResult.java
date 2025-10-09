package com.threatdetection.ingestion.model;

/**
 * Phase 1A: 单个日志处理结果DTO
 * 表示批量处理中每个日志的处理结果
 */
public class IngestionResult {

    private String logId;        // 日志唯一标识 (可选)
    private boolean success;     // 处理是否成功
    private String errorMessage; // 错误信息 (失败时)
    private String eventType;    // 事件类型 (成功时: "ATTACK" 或 "STATUS")

    public IngestionResult() {}

    // 成功结果的构造函数
    public static IngestionResult success(String logId) {
        IngestionResult result = new IngestionResult();
        result.logId = logId;
        result.success = true;
        result.eventType = "UNKNOWN"; // 将在后续处理中设置
        return result;
    }

    // 失败结果的构造函数
    public static IngestionResult error(String errorMessage) {
        IngestionResult result = new IngestionResult();
        result.success = false;
        result.errorMessage = errorMessage;
        return result;
    }

    // Getters and setters
    public String getLogId() { return logId; }
    public void setLogId(String logId) { this.logId = logId; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    @Override
    public String toString() {
        if (success) {
            return "IngestionResult{success=true, logId='" + logId + "', eventType='" + eventType + "'}";
        } else {
            return "IngestionResult{success=false, error='" + errorMessage + "'}";
        }
    }
}