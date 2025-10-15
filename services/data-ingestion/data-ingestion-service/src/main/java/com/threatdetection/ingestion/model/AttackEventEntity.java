package com.threatdetection.ingestion.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/**
 * 原始攻击事件持久化实体
 * 
 * <p>存储所有蜜罐检测到的攻击事件,用于审计和追溯
 */
@Entity
@Table(name = "attack_events", indexes = {
    @Index(name = "idx_attack_events_customer", columnList = "customer_id"),
    @Index(name = "idx_attack_events_attack_mac", columnList = "attack_mac"),
    @Index(name = "idx_attack_events_timestamp", columnList = "event_timestamp"),
    @Index(name = "idx_attack_events_customer_mac", columnList = "customer_id,attack_mac")
})
public class AttackEventEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;
    
    @Column(name = "dev_serial", nullable = false, length = 50)
    private String devSerial;
    
    @Column(name = "attack_mac", nullable = false, length = 17)
    private String attackMac;
    
    @Column(name = "attack_ip", length = 45)
    private String attackIp;
    
    @Column(name = "response_ip", nullable = false, length = 45)
    private String responseIp;
    
    @Column(name = "response_port", nullable = false)
    private Integer responsePort;
    
    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;
    
    @Column(name = "log_time")
    private Long logTime;
    
    @Column(name = "received_at")
    private Instant receivedAt;
    
    @Column(name = "raw_log_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawLogData;  // JSON string - will be properly converted to JSONB
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    
    public String getDevSerial() { return devSerial; }
    public void setDevSerial(String devSerial) { this.devSerial = devSerial; }
    
    public String getAttackMac() { return attackMac; }
    public void setAttackMac(String attackMac) { this.attackMac = attackMac; }
    
    public String getAttackIp() { return attackIp; }
    public void setAttackIp(String attackIp) { this.attackIp = attackIp; }
    
    public String getResponseIp() { return responseIp; }
    public void setResponseIp(String responseIp) { this.responseIp = responseIp; }
    
    public Integer getResponsePort() { return responsePort; }
    public void setResponsePort(Integer responsePort) { this.responsePort = responsePort; }
    
    public Instant getEventTimestamp() { return eventTimestamp; }
    public void setEventTimestamp(Instant eventTimestamp) { this.eventTimestamp = eventTimestamp; }
    
    public Long getLogTime() { return logTime; }
    public void setLogTime(Long logTime) { this.logTime = logTime; }
    
    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
    
    public String getRawLogData() { return rawLogData; }
    public void setRawLogData(String rawLogData) { this.rawLogData = rawLogData; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
