package com.threatdetection.ingestion.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class StatusEvent {
    private String id;
    private String devSerial;
    private int logType;
    private int sentryCount;
    private int realHostCount;
    private long devStartTime;
    private long devEndTime;
    private String time;
    private String status;
    private String description;
    private String rawLog;
    private String schemaVersion = "1.0";

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime timestamp;

    // Constructors
    public StatusEvent() {}
    
    public StatusEvent(String devSerial, int logType, int sentryCount, int realHostCount,
                      long devStartTime, long devEndTime, String time, String rawLog) {
        this.devSerial = devSerial;
        this.logType = logType;
        this.sentryCount = sentryCount;
        this.realHostCount = realHostCount;
        this.devStartTime = devStartTime;
        this.devEndTime = devEndTime;
        this.time = time;
        this.rawLog = rawLog;
        this.timestamp = LocalDateTime.now();
        this.status = "HEALTHY";
        this.description = generateDescription();
        this.id = devSerial + "_" + devStartTime + "_" + devEndTime;
    }

    private String generateDescription() {
        return String.format("Device status update: %d sentries, %d real hosts",
                           sentryCount, realHostCount);
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getDevSerial() { return devSerial; }
    public void setDevSerial(String devSerial) { this.devSerial = devSerial; }
    
    public int getLogType() { return logType; }
    public void setLogType(int logType) { this.logType = logType; }
    
    public int getSentryCount() { return sentryCount; }
    public void setSentryCount(int sentryCount) { this.sentryCount = sentryCount; }
    
    public int getRealHostCount() { return realHostCount; }
    public void setRealHostCount(int realHostCount) { this.realHostCount = realHostCount; }
    
    public long getDevStartTime() { return devStartTime; }
    public void setDevStartTime(long devStartTime) { this.devStartTime = devStartTime; }
    
    public long getDevEndTime() { return devEndTime; }
    public void setDevEndTime(long devEndTime) { this.devEndTime = devEndTime; }
    
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getRawLog() { return rawLog; }
    public void setRawLog(String rawLog) { this.rawLog = rawLog; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
}