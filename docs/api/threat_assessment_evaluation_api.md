# 威胁评估API - 评估操作

**服务名称**: Threat Assessment Service  
**服务端口**: 8083  
**基础路径**: `/api/v1/assessment`  
**文档版本**: 2.0  
**更新日期**: 2025-11-01

---

## 目录

1. [系统概述](#1-系统概述)
2. [核心功能](#2-核心功能)
3. [威胁评分算法](#3-威胁评分算法)
4. [数据模型](#4-数据模型)
5. [API端点列表](#5-api端点列表)
6. [API详细文档](#6-api详细文档)
   - 6.1 [执行威胁评估](#61-执行威胁评估)
7. [使用场景](#7-使用场景)
8. [Java客户端完整示例](#8-java客户端完整示例)
9. [最佳实践](#9-最佳实践)
10. [故障排查](#10-故障排查)
11. [相关文档](#11-相关文档)

---

## 1. 系统概述

威胁评估服务负责对蜜罐系统捕获的攻击行为进行综合评估,计算威胁评分并给出缓解建议。

### 核心特性

- ✅ **实时评估**: 接收Flink流处理的聚合数据,实时计算威胁评分
- ✅ **多维度评分**: 基于攻击次数、IP范围、端口多样性、设备数量、时间权重等多个维度
- ✅ **5级威胁分类**: INFO/LOW/MEDIUM/HIGH/CRITICAL
- ✅ **自动缓解建议**: 根据威胁等级自动生成处置建议
- ✅ **批量评估**: 支持批量评估多个攻击事件
- ✅ **租户隔离**: 基于customerId的多租户数据隔离

### 工作流程

```
┌─────────────────┐     聚合数据    ┌──────────────────┐     评估请求    ┌────────────────┐
│ Flink Stream    │  ─────────────→ │ Threat Assessment│  ────────────→  │ 威胁评分计算   │
│ Processing      │                 │    Service       │                 │   引擎         │
└─────────────────┘                 └──────────────────┘                 └────────────────┘
                                            │                                     │
                                            │                                     ↓
                                            │                             ┌────────────────┐
                                            │                             │  威胁等级判定  │
                                            │                             │ (5级分类)      │
                                            │                             └────────────────┘
                                            ↓                                     │
                                    ┌──────────────────┐                        ↓
                                    │  PostgreSQL DB   │              ┌────────────────┐
                                    │  (评估记录)      │ ←─────────── │  缓解建议生成  │
                                    └──────────────────┘              └────────────────┘
                                            │                                     │
                                            ↓                                     ↓
                                    ┌──────────────────┐              ┌────────────────┐
                                    │  Kafka Topic     │              │  Alert Service │
                                    │ (threat-alerts)  │ ←─────────── │  (告警创建)    │
                                    └──────────────────┘              └────────────────┘
```

### 技术栈

- **框架**: Spring Boot 3.1.5
- **数据库**: PostgreSQL 15+
- **消息队列**: Apache Kafka 3.4+
- **评分引擎**: Java 21 (自研算法)
- **API风格**: RESTful
- **数据格式**: JSON

---

## 2. 核心功能

### 2.1 威胁评分计算

基于多维度权重计算威胁评分:

```java
threatScore = (attackCount × uniqueIps × uniquePorts) 
            × timeWeight      // 时间权重 (0.8-1.2)
            × ipWeight        // IP权重 (1.0-2.0)
            × portWeight      // 端口权重 (1.0-2.0)
            × deviceWeight    // 设备权重 (1.0-1.5)
```

### 2.2 威胁等级判定

| 等级 | 评分范围 | 自动操作 |
|------|---------|---------|
| **INFO** | < 10 | 记录日志 |
| **LOW** | 10-50 | 发送通知 |
| **MEDIUM** | 50-100 | 创建告警 |
| **HIGH** | 100-200 | 创建告警 + 阻断端口 |
| **CRITICAL** | > 200 | 创建告警 + 立即隔离 |

### 2.3 缓解建议生成

根据威胁等级和攻击特征自动生成缓解建议:

- **CRITICAL**: 立即隔离、应急响应、深度分析
- **HIGH**: 阻断端口、审计日志、监控同网段
- **MEDIUM**: 创建告警、分配分析师
- **LOW**: 加入监控列表
- **INFO**: 仅记录

### 2.4 评估记录持久化

所有评估结果保存到PostgreSQL数据库,支持:
- 历史查询
- 趋势分析
- 审计追溯

---

## 3. 威胁评分算法

### 核心公式

```java
// 原始评分计算
rawScore = (attackCount × uniqueIps × uniquePorts) 
         × timeWeight 
         × ipWeight 
         × portWeight 
         × deviceWeight

// 标准化到 (0,100) 范围 - 使用对数变换
threatScore = min(99, max(1, log10(rawScore + 1) × 25))
```

**标准化说明**:
- 使用对数变换将大范围的原始评分压缩到 1-99 标准范围
- 小威胁 (原始1-10): 映射到 1-25 左右
- 中等威胁 (原始100-1000): 映射到 25-50 左右
- 高威胁 (原始1000-10000): 映射到 50-75 左右
- 极高威胁 (原始10000+): 映射到 75-99

**评分映射对照表**:

| 原始评分 | 标准化评分 | 威胁等级 | 说明 |
|---------|-----------|---------|------|
| 1 | 7.5 | INFO | 极低威胁 |
| 10 | 26.0 | LOW | 单次探测 |
| 100 | 50.1 | MEDIUM | 小规模扫描 |
| 1,000 | 75.0 | HIGH | 中等规模攻击 |
| 10,000 | 99.0 | CRITICAL | 大规模横向移动 |
| 100,000+ | 99.0 | CRITICAL | 极高威胁 (上限) |

### 权重计算详解

#### 3.1 时间权重 (timeWeight)

| 时间段 | 权重 | 说明 |
|--------|------|------|
| 0:00-6:00 | 1.2 | 深夜异常行为 (APT常见) |
| 6:00-9:00 | 1.1 | 早晨时段 |
| 9:00-17:00 | 1.0 | 工作时间基准 |
| 17:00-21:00 | 0.9 | 傍晚时段 |
| 21:00-24:00 | 0.8 | 夜间时段 |

#### 3.2 IP权重 (ipWeight)

| 唯一IP数量 | 权重 | 攻击特征 |
|-----------|------|---------|
| 1 | 1.0 | 单一目标 |
| 2-3 | 1.3 | 小范围扫描 |
| 4-5 | 1.5 | 中等扫描 |
| 6-10 | 1.7 | 广泛扫描 |
| 10+ | 2.0 | 大规模横向移动 |

#### 3.3 端口权重 (portWeight)

| 唯一端口数量 | 权重 | 攻击特征 |
|-------------|------|---------|
| 1 | 1.0 | 单端口攻击 |
| 2-3 | 1.2 | 小范围探测 |
| 4-5 | 1.4 | 中等扫描 |
| 6-10 | 1.6 | 广泛扫描 |
| 11-20 | 1.8 | 大规模扫描 |
| 20+ | 2.0 | 全端口扫描 |

#### 3.4 设备权重 (deviceWeight)

| 唯一设备数量 | 权重 | 攻击特征 |
|-------------|------|---------|
| 1 | 1.0 | 单一设备检测 |
| 2+ | 1.5 | 多设备检测 (全网扫描) |

### 评分示例

**场景**: 深夜大规模横向移动攻击 (5分钟时间窗口)

```
输入数据:
- attackCount = 150 (5分钟内探测次数)
- uniqueIps = 5 (5分钟内访问的诱饵IP数量)
- uniquePorts = 3 (5分钟内尝试的端口种类)
- uniqueDevices = 2 (5分钟内检测到的蜜罐设备数)
- timestamp = 2025-01-15T02:30:00Z (深夜)
- timeWindowSeconds = 300 (5分钟窗口)

计算过程:
- baseScore = 150 × 5 × 3 = 2250
- timeWeight = 1.2 (深夜)
- ipWeight = 1.5 (5个IP)
- portWeight = 1.2 (3个端口)
- deviceWeight = 1.5 (2个设备)

原始评分 = 2250 × 1.2 × 1.5 × 1.2 × 1.5 = 7290.0
标准化评分 = log10(7290 + 1) × 25 = 97.1
威胁等级 = CRITICAL (80-99)
```

**时间窗口对评分的影响**:

| 时间窗口 | 典型场景 | 评分影响 |
|---------|---------|---------|
| 30秒 | 实时检测 | 更敏感,易检测瞬时爆发 |
| 5分钟 | 标准评估 | 平衡灵敏度和准确性 |
| 15分钟 | 趋势分析 | 更稳定,减少误报 |
| 1小时 | 长期监控 | 适合检测慢速扫描 |

---

## 4. 数据模型

### 数据库表结构

```sql
CREATE TABLE threat_assessments (
    id BIGSERIAL PRIMARY KEY,
    assessment_id VARCHAR(100) UNIQUE NOT NULL,
    customer_id VARCHAR(50) NOT NULL,
    
    -- 攻击源信息
    attack_mac VARCHAR(17) NOT NULL,
    attack_ip VARCHAR(45) NOT NULL,
    
    -- 威胁评分
    threat_score DECIMAL(10,2) NOT NULL,
    threat_level VARCHAR(20) NOT NULL,  -- INFO/LOW/MEDIUM/HIGH/CRITICAL
    
    -- 风险因子
    attack_count INTEGER NOT NULL,
    unique_ips INTEGER NOT NULL,
    unique_ports INTEGER NOT NULL,
    unique_devices INTEGER NOT NULL,
    
    -- 权重因子
    time_weight DECIMAL(5,2) NOT NULL,
    ip_weight DECIMAL(5,2) NOT NULL,
    port_weight DECIMAL(5,2) NOT NULL,
    device_weight DECIMAL(5,2) NOT NULL,
    
    -- 缓解措施
    mitigation_recommendations TEXT[],
    mitigation_status VARCHAR(50),      -- PENDING/IN_PROGRESS/COMPLETED
    
    -- 时间戳
    assessment_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 索引
    INDEX idx_customer_id (customer_id),
    INDEX idx_attack_mac (attack_mac),
    INDEX idx_threat_level (threat_level),
    INDEX idx_assessment_time (assessment_time),
    INDEX idx_composite (customer_id, threat_level, assessment_time)
);
```

### Java DTO

```java
/**
 * 威胁评估响应 (完整版)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentResponse {
    private String assessmentId;
    private String customerId;
    private String attackMac;
    private String attackIp;
    
    // 评分结果
    private Double threatScore;  // 标准化评分 (1-99)
    private ThreatLevel threatLevel;
    
    // 风险因子
    private RiskFactors riskFactors;
    
    // 缓解建议
    private List<String> mitigationRecommendations;
    private String mitigationStatus;
    
    // 时间戳
    private Instant assessmentTime;
    private Instant createdAt;
}

/**
 * 风险因子
 */
@Data
@Builder
public class RiskFactors {
    private Integer attackCount;
    private Integer uniqueIps;
    private Integer uniquePorts;
    private Integer uniqueDevices;
    
    private Double timeWeight;
    private Double ipWeight;
    private Double portWeight;
    private Double deviceWeight;
}

/**
 * 威胁等级枚举
 */
public enum ThreatLevel {
    INFO,        // 1-19
    LOW,         // 20-39
    MEDIUM,      // 40-59
    HIGH,        // 60-79
    CRITICAL     // 80-99
}
```

---

## 5. API端点列表

| 方法 | 端点 | 功能 | 参数 |
|------|------|------|------|
| `POST` | `/api/v1/assessment/evaluate` | 执行威胁评估 | AssessmentRequest (body) |
| `GET` | `/api/v1/assessment/{assessmentId}` | 获取评估详情 | assessmentId (path) |
| `GET` | `/api/v1/assessment/assessments` | 查询评估列表 (分页) | customerId, page, size (query) |
| `GET` | `/api/v1/assessment/statistics` | 获取威胁统计 | customerId (query) |
| `GET` | `/api/v1/assessment/trend` | 获取威胁趋势 | customerId (query) |
| `GET` | `/api/v1/assessment/port-distribution` | 获取端口分布 | customerId (query) |
| `GET` | `/api/v1/assessment/health` | 健康检查 | - |

---

## 6. API详细文档

### 6.1 执行威胁评估

---

## API概述

本文档涵盖威胁评估服务的**核心评估操作**:

| 方法 | 端点 | 功能 |
|------|------|------|
| `POST` | `/api/v1/assessment/evaluate` | 执行威胁评估 |
| `POST` | `/api/v1/assessment/mitigation/{assessmentId}` | 执行缓解措施 |

**相关文档**: [威胁评估概述](./threat_assessment_overview.md) | [查询分析API](./threat_assessment_query_api.md)

---

## 执行威胁评估

### 端点信息

**描述**: 对聚合攻击数据进行综合威胁评估,计算威胁评分和风险等级。

**端点**: `POST /api/v1/assessment/evaluate`

### 请求参数

**Content-Type**: `application/json`

**请求体模型** (AssessmentRequest):

```json
{
  "customer_id": "customer_a",
  "attack_mac": "04:42:1a:8e:e3:65",
  "attack_ip": "192.168.75.188",
  "attack_count": 150,
  "unique_ips": 5,
  "unique_ports": 3,
  "unique_devices": 2,
  "timestamp": "2025-01-15T02:30:00Z",
  "port_list": [3306, 3389, 445],
  "time_window_seconds": 300
}
```

**字段说明**:

| 字段 | 类型 | 必需 | 验证规则 | 说明 |
|-----|------|------|---------|------|
| `customer_id` | String | ✅ | @NotBlank, max=100 | 客户ID (租户隔离) |
| `attack_mac` | String | ✅ | @NotBlank, max=17 | 被诱捕者MAC地址 |
| `attack_ip` | String | ❌ | max=45 | 被诱捕者IP地址 (可选) |
| `attack_count` | Integer | ✅ | @NotNull, @Min(1) | 探测次数 (≥1) |
| `unique_ips` | Integer | ✅ | @NotNull, @Min(1) | 访问的诱饵IP数量 |
| `unique_ports` | Integer | ✅ | @NotNull, @Min(1) | 尝试的端口种类 |
| `unique_devices` | Integer | ✅ | @NotNull, @Min(1) | 检测到的蜜罐设备数 |
| `timestamp` | String | ❌ | ISO8601 | 聚合窗口时间戳 (默认当前时间) |
| `port_list` | Array<Integer> | ❌ | - | 具体端口列表 (用于精确权重计算, V4.0新增) |
| `time_window_seconds` | Integer | ❌ | 30/300/900/3600 | 评估时间窗口(秒) (默认300秒=5分钟) |

### 请求示例 (curl)

#### 场景1: CRITICAL级别威胁 (深夜大规模横向移动)

```bash
curl -X POST http://localhost:8083/api/v1/assessment/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": "customer_a",
    "attack_mac": "04:42:1a:8e:e3:65",
    "attack_ip": "192.168.75.188",
    "attack_count": 150,
    "unique_ips": 5,
    "unique_ports": 3,
    "unique_devices": 2,
    "timestamp": "2025-01-15T02:30:00Z",
    "time_window_seconds": 300
  }'
```

#### 场景2: MEDIUM级别威胁 (工作时间单目标探测)

```bash
curl -X POST http://localhost:8083/api/v1/assessment/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": "customer_a",
    "attack_mac": "aa:bb:cc:dd:ee:ff",
    "attack_ip": "10.0.1.50",
    "attack_count": 30,
    "unique_ips": 2,
    "unique_ports": 1,
    "unique_devices": 1,
    "timestamp": "2025-01-15T14:30:00Z"
  }'
```

### 请求示例 (Java)

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

public class ThreatAssessmentExample {
    
    private static final String BASE_URL = "http://localhost:8083/api/v1/assessment";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());
    
    /**
     * 执行威胁评估
     */
    public AssessmentResponse evaluateThreat(
            String customerId,
            String attackMac,
            String attackIp,
            int attackCount,
            int uniqueIps,
            int uniquePorts,
            int uniqueDevices) {
        
        // 构建请求
        AssessmentRequest request = new AssessmentRequest();
        request.setCustomerId(customerId);
        request.setAttackMac(attackMac);
        request.setAttackIp(attackIp);
        request.setAttackCount(attackCount);
        request.setUniqueIps(uniqueIps);
        request.setUniquePorts(uniquePorts);
        request.setUniqueDevices(uniqueDevices);
        request.setTimestamp(Instant.now().toString());
        request.setTimeWindowSeconds(300);  // 5分钟时间窗口
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<AssessmentRequest> httpRequest = new HttpEntity<>(request, headers);
        
        // 发送请求
        ResponseEntity<AssessmentResponse> response = restTemplate.postForEntity(
            BASE_URL + "/evaluate",
            httpRequest,
            AssessmentResponse.class
        );
        
        return response.getBody();
    }
    
    /**
     * 评估CRITICAL级别威胁 (深夜横向移动)
     */
    public void evaluateCriticalThreat() {
        AssessmentResponse result = evaluateThreat(
            "customer_a",
            "04:42:1a:8e:e3:65",
            "192.168.75.188",
            150,    // 高频探测
            5,      // 5个诱饵IP (横向移动)
            3,      // 3种端口 (多种攻击手段)
            2       // 2个蜜罐设备检测到
        );
        
        System.out.println("Assessment ID: " + result.getAssessmentId());
        System.out.println("Threat Score: " + result.getThreatScore());
        System.out.println("Threat Level: " + result.getThreatLevel());
        System.out.println("\nMitigation Recommendations:");
        result.getMitigationRecommendations().forEach(r -> 
            System.out.println("- " + r)
        );
    }
    
    // DTO类
    public static class AssessmentRequest {
        private String customerId;
        private String attackMac;
        private String attackIp;
        private int attackCount;
        private int uniqueIps;
        private int uniquePorts;
        private int uniqueDevices;
        private String timestamp;
        private Integer timeWindowSeconds;
        
        // Getters and Setters
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        
        public String getAttackMac() { return attackMac; }
        public void setAttackMac(String attackMac) { this.attackMac = attackMac; }
        
        public String getAttackIp() { return attackIp; }
        public void setAttackIp(String attackIp) { this.attackIp = attackIp; }
        
        public int getAttackCount() { return attackCount; }
        public void setAttackCount(int attackCount) { this.attackCount = attackCount; }
        
        public int getUniqueIps() { return uniqueIps; }
        public void setUniqueIps(int uniqueIps) { this.uniqueIps = uniqueIps; }
        
        public int getUniquePorts() { return uniquePorts; }
        public void setUniquePorts(int uniquePorts) { this.uniquePorts = uniquePorts; }
        
        public int getUniqueDevices() { return uniqueDevices; }
        public void setUniqueDevices(int uniqueDevices) { this.uniqueDevices = uniqueDevices; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        
        public Integer getTimeWindowSeconds() { return timeWindowSeconds; }
        public void setTimeWindowSeconds(Integer timeWindowSeconds) { this.timeWindowSeconds = timeWindowSeconds; }
    }
    
    public static class AssessmentResponse {
        private String assessmentId;
        private String customerId;
        private String attackMac;
        private String attackIp;
        private double threatScore;  // 标准化评分 (1-99)
        private String threatLevel;
        private RiskFactors riskFactors;
        private List<String> mitigationRecommendations;
        private String assessmentTime;
        private String createdAt;
        
        // Getters
        public String getAssessmentId() { return assessmentId; }
        public String getCustomerId() { return customerId; }
        public String getAttackMac() { return attackMac; }
        public String getAttackIp() { return attackIp; }
        public double getThreatScore() { return threatScore; }
        public String getThreatLevel() { return threatLevel; }
        public RiskFactors getRiskFactors() { return riskFactors; }
        public List<String> getMitigationRecommendations() { return mitigationRecommendations; }
        public String getAssessmentTime() { return assessmentTime; }
        public String getCreatedAt() { return createdAt; }
    }
    
    public static class RiskFactors {
        private int attackCount;
        private int uniqueIps;
        private int uniquePorts;
        private int uniqueDevices;
        private double timeWeight;
        private double ipWeight;
        private double portWeight;
        private double deviceWeight;
        
        // Getters
        public int getAttackCount() { return attackCount; }
        public int getUniqueIps() { return uniqueIps; }
        public int getUniquePorts() { return uniquePorts; }
        public int getUniqueDevices() { return uniqueDevices; }
        public double getTimeWeight() { return timeWeight; }
        public double getIpWeight() { return ipWeight; }
        public double getPortWeight() { return portWeight; }
        public double getDeviceWeight() { return deviceWeight; }
    }
}
```

### 响应示例 (成功)

**HTTP 200 OK**

```json
{
  "assessmentId": "12345",
  "customer_id": "customer_a",
  "attack_mac": "04:42:1a:8e:e3:65",
  "attack_ip": "192.168.75.188",
  "threat_score": 97.1,
  "threat_level": "CRITICAL",
  "riskFactors": {
    "attack_count": 150,
    "unique_ips": 5,
    "unique_ports": 3,
    "unique_devices": 2,
    "timeWeight": 1.2,
    "ipWeight": 1.5,
    "portWeight": 1.2,
    "deviceWeight": 1.5
  },
  "mitigationRecommendations": [
    "立即隔离攻击源 192.168.75.188",
    "检查同网段其他主机是否被攻陷",
    "审计攻击源的网络访问日志",
    "启动应急响应流程",
    "通知安全团队进行深度分析"
  ],
  "assessmentTime": "2025-01-15T02:30:00Z",
  "created_at": "2025-01-15T02:30:05Z"
}
```

### 响应示例 (验证失败)

**HTTP 400 Bad Request**

```json
{
  "timestamp": "2025-01-15T02:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "attackCount",
      "message": "must be greater than or equal to 1"
    },
    {
      "field": "customer_id",
      "message": "must not be blank"
    }
  ]
}
```

### 错误码

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 评估成功完成 |
| 400 | 请求参数验证失败 |
| 500 | 服务器内部错误 (数据库/计算异常) |

---

## 执行缓解措施

### 端点信息

**描述**: 对已评估的威胁执行缓解措施,如隔离、阻断、告警等。

**端点**: `POST /api/v1/assessment/mitigation/{assessmentId}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `assessmentId` | String | ✅ | 评估记录ID |

### 请求参数

**Content-Type**: `application/json`

**请求体模型** (MitigationRequest):

```json
{
  "mitigationType": "ISOLATE",
  "autoExecute": true,
  "reason": "CRITICAL级别威胁,立即隔离",
  "executedBy": "admin@company.com"
}
```

**字段说明**:

| 字段 | 类型 | 必需 | 可选值 | 说明 |
|-----|------|------|--------|------|
| `mitigationType` | String | ✅ | ISOLATE, BLOCK, ALERT, MONITOR | 缓解类型 |
| `autoExecute` | Boolean | ❌ | true/false | 是否自动执行 (默认false) |
| `reason` | String | ❌ | - | 执行原因 |
| `executedBy` | String | ✅ | - | 执行人 |

**缓解类型说明**:

| 类型 | 操作 | 适用等级 |
|------|------|---------|
| `ISOLATE` | 完全隔离攻击源 (断网) | CRITICAL, HIGH |
| `BLOCK` | 阻断特定端口/协议 | HIGH, MEDIUM |
| `ALERT` | 仅发送告警 | MEDIUM, LOW |
| `MONITOR` | 加入监控列表 | LOW, INFO |

### 请求示例 (curl)

```bash
# 对CRITICAL威胁立即执行隔离
curl -X POST http://localhost:8083/api/v1/assessment/mitigation/12345 \
  -H "Content-Type: application/json" \
  -d '{
    "mitigationType": "ISOLATE",
    "autoExecute": true,
    "reason": "深夜检测到大规模横向移动,立即隔离",
    "executedBy": "security-admin@company.com"
  }'

# 对MEDIUM威胁执行端口阻断
curl -X POST http://localhost:8083/api/v1/assessment/mitigation/12346 \
  -H "Content-Type: application/json" \
  -d '{
    "mitigationType": "BLOCK",
    "autoExecute": false,
    "reason": "阻断RDP和SMB端口访问",
    "executedBy": "admin@company.com"
  }'
```

### 请求示例 (Java)

```java
public class MitigationExample {
    
    private static final String BASE_URL = "http://localhost:8083/api/v1/assessment";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 执行缓解措施
     */
    public MitigationResponse executeMitigation(
            String assessmentId,
            String mitigationType,
            boolean autoExecute,
            String reason,
            String executedBy) {
        
        // 构建请求
        MitigationRequest request = new MitigationRequest();
        request.setMitigationType(mitigationType);
        request.setAutoExecute(autoExecute);
        request.setReason(reason);
        request.setExecutedBy(executedBy);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<MitigationRequest> httpRequest = new HttpEntity<>(request, headers);
        
        String url = BASE_URL + "/mitigation/" + assessmentId;
        
        ResponseEntity<MitigationResponse> response = restTemplate.postForEntity(
            url,
            httpRequest,
            MitigationResponse.class
        );
        
        return response.getBody();
    }
    
    /**
     * 自动隔离CRITICAL威胁
     */
    public void isolateCriticalThreat(String assessmentId) {
        MitigationResponse result = executeMitigation(
            assessmentId,
            "ISOLATE",
            true,
            "CRITICAL级别威胁,自动隔离",
            "system-auto"
        );
        
        System.out.println("Mitigation Status: " + result.getStatus());
        System.out.println("Execution Time: " + result.getExecutionTime());
        System.out.println("Actions Taken:");
        result.getActionsTaken().forEach(action -> 
            System.out.println("- " + action)
        );
    }
    
    // DTO类
    public static class MitigationRequest {
        private String mitigationType;
        private boolean autoExecute;
        private String reason;
        private String executedBy;
        
        // Getters and Setters
        public String getMitigationType() { return mitigationType; }
        public void setMitigationType(String mitigationType) { this.mitigationType = mitigationType; }
        
        public boolean isAutoExecute() { return autoExecute; }
        public void setAutoExecute(boolean autoExecute) { this.autoExecute = autoExecute; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public String getExecutedBy() { return executedBy; }
        public void setExecutedBy(String executedBy) { this.executedBy = executedBy; }
    }
    
    public static class MitigationResponse {
        private String mitigationId;
        private String assessmentId;
        private String status;
        private List<String> actionsTaken;
        private String executionTime;
        
        // Getters
        public String getMitigationId() { return mitigationId; }
        public String getAssessmentId() { return assessmentId; }
        public String getStatus() { return status; }
        public List<String> getActionsTaken() { return actionsTaken; }
        public String getExecutionTime() { return executionTime; }
    }
}
```

### 响应示例 (成功)

**HTTP 200 OK**

```json
{
  "mitigationId": "MIT-67890",
  "assessmentId": "12345",
  "status": "EXECUTED",
  "actionsTaken": [
    "已隔离攻击源 192.168.75.188 (MAC: 04:42:1a:8e:e3:65)",
    "已阻断该MAC地址的所有网络访问",
    "已发送CRITICAL级别告警给安全团队",
    "已记录事件日志到审计系统"
  ],
  "executionTime": "2025-01-15T02:31:00Z"
}
```

### 响应示例 (评估不存在)

**HTTP 404 Not Found**

```json
{
  "timestamp": "2025-01-15T02:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Assessment with ID 99999 not found"
}
```

### 错误码

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 缓解措施执行成功 |
| 400 | 请求参数无效 |
| 404 | 评估记录不存在 |
| 500 | 缓解措施执行失败 |

---

## 执行缓解措施

### 端点信息

**描述**: 对已评估的威胁执行缓解措施,如隔离、阻断、告警等。

**端点**: `POST /api/v1/assessment/mitigation/{assessmentId}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `assessmentId` | String | ✅ | 评估记录ID |

### 请求参数

**Content-Type**: `application/json`

**请求体模型** (MitigationRequest):

```json
{
  "mitigationType": "ISOLATE",
  "autoExecute": true,
  "reason": "CRITICAL级别威胁,立即隔离",
  "executedBy": "admin@company.com"
}
```

**字段说明**:

| 字段 | 类型 | 必需 | 可选值 | 说明 |
|-----|------|------|--------|------|
| `mitigationType` | String | ✅ | ISOLATE, BLOCK, ALERT, MONITOR | 缓解类型 |
| `autoExecute` | Boolean | ❌ | true/false | 是否自动执行 (默认false) |
| `reason` | String | ❌ | - | 执行原因 |
| `executedBy` | String | ✅ | - | 执行人 |

**缓解类型说明**:

| 类型 | 操作 | 适用等级 |
|------|------|---------|
| `ISOLATE` | 完全隔离攻击源 (断网) | CRITICAL, HIGH |
| `BLOCK` | 阻断特定端口/协议 | HIGH, MEDIUM |
| `ALERT` | 仅发送告警 | MEDIUM, LOW |
| `MONITOR` | 加入监控列表 | LOW, INFO |

### 请求示例 (curl)

```bash
# 对CRITICAL威胁立即执行隔离
curl -X POST http://localhost:8083/api/v1/assessment/mitigation/12345 \
  -H "Content-Type: application/json" \
  -d '{
    "mitigationType": "ISOLATE",
    "autoExecute": true,
    "reason": "深夜检测到大规模横向移动,立即隔离",
    "executedBy": "security-admin@company.com"
  }'

# 对MEDIUM威胁执行端口阻断
curl -X POST http://localhost:8083/api/v1/assessment/mitigation/12346 \
  -H "Content-Type: application/json" \
  -d '{
    "mitigationType": "BLOCK",
    "autoExecute": false,
    "reason": "阻断RDP和SMB端口访问",
    "executedBy": "admin@company.com"
  }'
```

### 请求示例 (Java)

```java
public class MitigationExample {
    
    private static final String BASE_URL = "http://localhost:8083/api/v1/assessment";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 执行缓解措施
     */
    public MitigationResponse executeMitigation(
            String assessmentId,
            String mitigationType,
            boolean autoExecute,
            String reason,
            String executedBy) {
        
        // 构建请求
        MitigationRequest request = new MitigationRequest();
        request.setMitigationType(mitigationType);
        request.setAutoExecute(autoExecute);
        request.setReason(reason);
        request.setExecutedBy(executedBy);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<MitigationRequest> httpRequest = new HttpEntity<>(request, headers);
        
        String url = BASE_URL + "/mitigation/" + assessmentId;
        
        ResponseEntity<MitigationResponse> response = restTemplate.postForEntity(
            url,
            httpRequest,
            MitigationResponse.class
        );
        
        return response.getBody();
    }
    
    /**
     * 自动隔离CRITICAL威胁
     */
    public void isolateCriticalThreat(String assessmentId) {
        MitigationResponse result = executeMitigation(
            assessmentId,
            "ISOLATE",
            true,
            "CRITICAL级别威胁,自动隔离",
            "system-auto"
        );
        
        System.out.println("Mitigation Status: " + result.getStatus());
        System.out.println("Execution Time: " + result.getExecutionTime());
        System.out.println("Actions Taken:");
        result.getActionsTaken().forEach(action -> 
            System.out.println("- " + action)
        );
    }
    
    // DTO类
    public static class MitigationRequest {
        private String mitigationType;
        private boolean autoExecute;
        private String reason;
        private String executedBy;
        
        // Getters and Setters
        public String getMitigationType() { return mitigationType; }
        public void setMitigationType(String mitigationType) { this.mitigationType = mitigationType; }
        
        public boolean isAutoExecute() { return autoExecute; }
        public void setAutoExecute(boolean autoExecute) { this.autoExecute = autoExecute; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public String getExecutedBy() { return executedBy; }
        public void setExecutedBy(String executedBy) { this.executedBy = executedBy; }
    }
    
    public static class MitigationResponse {
        private String mitigationId;
        private String assessmentId;
        private String status;
        private List<String> actionsTaken;
        private String executionTime;
        
        // Getters
        public String getMitigationId() { return mitigationId; }
        public String getAssessmentId() { return assessmentId; }
        public String getStatus() { return status; }
        public List<String> getActionsTaken() { return actionsTaken; }
        public String getExecutionTime() { return executionTime; }
    }
}
```

### 响应示例 (成功)

**HTTP 200 OK**

```json
{
  "mitigationId": "MIT-67890",
  "assessmentId": "12345",
  "status": "EXECUTED",
  "actionsTaken": [
    "已隔离攻击源 192.168.75.188 (MAC: 04:42:1a:8e:e3:65)",
    "已阻断该MAC地址的所有网络访问",
    "已发送CRITICAL级别告警给安全团队",
    "已记录事件日志到审计系统"
  ],
  "executionTime": "2025-01-15T02:31:00Z"
}
```

### 响应示例 (评估不存在)

**HTTP 404 Not Found**

```json
{
  "timestamp": "2025-01-15T02:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Assessment with ID 99999 not found"
}
```

### 错误码

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 缓解措施执行成功 |
| 400 | 请求参数无效 |
| 404 | 评估记录不存在 |
| 500 | 缓解措施执行失败 |

---

## 7. 使用场景

### 场景1: 自动威胁评估和缓解

**需求**: 检测到CRITICAL威胁时,自动评估并隔离攻击源。

**实现**:

```java
public class AutoMitigationService {
    
    private final ThreatAssessmentClient assessmentClient;
    
    /**
     * 自动评估和缓解流程
     */
    public void handleThreatAlert(
            String customerId,
            String attackMac,
            String attackIp,
            int attackCount,
            int uniqueIps,
            int uniquePorts,
            int uniqueDevices,
            int timeWindowSeconds) {
        
        // 1. 执行威胁评估 (指定时间窗口)
        AssessmentResponse assessment = assessmentClient.evaluateThreat(
            customerId, attackMac, attackIp,
            attackCount, uniqueIps, uniquePorts, uniqueDevices,
            timeWindowSeconds  // 指定时间窗口
        );
        
        System.out.println("Threat assessed: " + assessment.getThreatLevel() 
                          + " (Score: " + assessment.getThreatScore() 
                          + ", Window: " + timeWindowSeconds + "s)");
        
        // 2. 根据威胁等级和时间窗口调整响应策略
        if ("CRITICAL".equals(assessment.getThreatLevel())) {
            if (timeWindowSeconds <= 60) {
                // 短时间窗口内的CRITICAL威胁 - 立即隔离
                System.out.println("⚠️ IMMEDIATE: Short-window CRITICAL threat detected");
                executeImmediateIsolation(assessment);
            } else {
                // 长时间窗口内的CRITICAL威胁 - 评估持续性
                System.out.println("⚠️ SUSTAINED: Long-window CRITICAL threat detected");
                executeSustainedResponse(assessment);
            }
        }
    }
    
    private void executeImmediateIsolation(AssessmentResponse assessment) {
        // 立即隔离逻辑
        System.out.println("Executing immediate isolation for: " + assessment.getAttackIp());
    }
    
    private void executeSustainedResponse(AssessmentResponse assessment) {
        // 持续性响应逻辑
        System.out.println("Executing sustained response for: " + assessment.getAttackIp());
    }
}
```

### 场景2: 基于时间窗口的动态评估

**需求**: 根据不同的监控场景使用不同的时间窗口进行评估。

**实现**:

```java
public class DynamicAssessmentService {
    
    /**
     * 根据场景选择合适的时间窗口
     */
    public AssessmentResponse assessByScenario(
            String customerId,
            String attackMac,
            String attackIp,
            int attackCount,
            int uniqueIps,
            int uniquePorts,
            int uniqueDevices,
            AssessmentScenario scenario) {
        
        int timeWindowSeconds = getTimeWindowForScenario(scenario);
        
        return assessmentClient.evaluateThreat(
            customerId, attackMac, attackIp,
            attackCount, uniqueIps, uniquePorts, uniqueDevices,
            timeWindowSeconds
        );
    }
    
    /**
     * 根据场景确定时间窗口
     */
    private int getTimeWindowForScenario(AssessmentScenario scenario) {
        switch (scenario) {
            case REAL_TIME_MONITORING:
                return 30;    // 30秒 - 实时检测
            case STANDARD_ASSESSMENT:
                return 300;   // 5分钟 - 标准评估
            case TREND_ANALYSIS:
                return 900;   // 15分钟 - 趋势分析
            case LONG_TERM_MONITORING:
                return 3600;  // 1小时 - 长期监控
            default:
                return 300;   // 默认5分钟
        }
    }
}

enum AssessmentScenario {
    REAL_TIME_MONITORING,    // 实时监控
    STANDARD_ASSESSMENT,     // 标准评估
    TREND_ANALYSIS,         // 趋势分析
    LONG_TERM_MONITORING     // 长期监控
}
```

### 场景2: 批量威胁评估

**需求**: 对过去1小时的所有攻击事件进行批量评估。

**实现**:

```java
public class BatchAssessmentService {
    
    /**
     * 批量评估威胁
     */
    public List<AssessmentResponse> batchEvaluate(List<AttackEvent> events) {
        List<AssessmentResponse> results = new ArrayList<>();
        
        for (AttackEvent event : events) {
            try {
                AssessmentResponse assessment = assessmentClient.evaluateThreat(
                    event.getCustomerId(),
                    event.getAttackMac(),
                    event.getAttackIp(),
                    event.getAttackCount(),
                    event.getUniqueIps(),
                    event.getUniquePorts(),
                    event.getUniqueDevices()
                );
                
                results.add(assessment);
                
                // 记录高危威胁
                if ("HIGH".equals(assessment.getThreatLevel()) || 
                    "CRITICAL".equals(assessment.getThreatLevel())) {
                    System.err.println("⚠️ High-risk threat detected: " 
                                      + assessment.getAttackIp());
                }
                
            } catch (Exception e) {
                System.err.println("Failed to assess: " + event.getAttackIp() 
                                  + " - " + e.getMessage());
            }
        }
        
        // 统计
        long criticalCount = results.stream()
            .filter(r -> "CRITICAL".equals(r.getThreatLevel()))
            .count();
        
        System.out.println("Batch assessment completed:");
        System.out.println("- Total: " + results.size());
        System.out.println("- CRITICAL: " + criticalCount);
        
        return results;
    }
}
```

---

## 8. Java客户端完整示例

```java
package com.threatdetection.client;

import lombok.Data;
import lombok.Builder;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

/**
 * 威胁评估客户端 - 完整实现
 * 
 * <p>提供威胁评估的核心操作:
 * - 单个威胁评估
 * - 评估结果查询
 * 
 * @author Security Team
 * @version 2.0
 */
public class ThreatAssessmentClient {
    
    private static final String BASE_URL = "http://localhost:8083/api/v1/assessment";
    private final RestTemplate restTemplate;
    
    public ThreatAssessmentClient() {
        this.restTemplate = new RestTemplate();
    }
    
    public ThreatAssessmentClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    // ==================== 评估操作 ====================
    
    /**
     * 执行威胁评估
     */
    public AssessmentResponse evaluate(AssessmentRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<AssessmentRequest> httpRequest = new HttpEntity<>(request, headers);
        
        ResponseEntity<AssessmentResponse> response = restTemplate.postForEntity(
            BASE_URL + "/evaluate",
            httpRequest,
            AssessmentResponse.class
        );
        
        return response.getBody();
    }
    
    /**
     * 评估CRITICAL级别威胁 (便捷方法)
     */
    public AssessmentResponse evaluateCriticalThreat(
            String customerId,
            String attackMac,
            String attackIp,
            int attackCount,
            int uniqueIps,
            int uniquePorts,
            int uniqueDevices) {
        
        return evaluateCriticalThreat(customerId, attackMac, attackIp, 
                                    attackCount, uniqueIps, uniquePorts, uniqueDevices, 300);
    }
    
    /**
     * 评估CRITICAL级别威胁 (指定时间窗口)
     */
    public AssessmentResponse evaluateCriticalThreat(
            String customerId,
            String attackMac,
            String attackIp,
            int attackCount,
            int uniqueIps,
            int uniquePorts,
            int uniqueDevices,
            int timeWindowSeconds) {
        
        AssessmentRequest request = AssessmentRequest.builder()
            .customerId(customerId)
            .attackMac(attackMac)
            .attackIp(attackIp)
            .attackCount(attackCount)
            .uniqueIps(uniqueIps)
            .uniquePorts(uniquePorts)
            .uniqueDevices(uniqueDevices)
            .timestamp(Instant.now().toString())
            .timeWindowSeconds(timeWindowSeconds)
            .build();
        
        return evaluate(request);
    }
    
    // ==================== 查询操作 ====================
    
    /**
     * 获取评估详情
     */
    public AssessmentResponse getAssessmentDetails(String assessmentId) {
        String url = BASE_URL + "/" + assessmentId;
        return restTemplate.getForObject(url, AssessmentResponse.class);
    }
    
    // ==================== DTO类 ====================
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssessmentRequest {
        private String customerId;
        private String attackMac;
        private String attackIp;
        private Integer attackCount;
        private Integer uniqueIps;
        private Integer uniquePorts;
        private Integer uniqueDevices;
        private String timestamp;
        private Integer timeWindowSeconds;
    }
    
    @Data
    public static class AssessmentResponse {
        private String assessmentId;
        private String customerId;
        private String attackMac;
        private String attackIp;
        private Double threatScore;  // 标准化评分 (1-99)
        private String threatLevel;
        private RiskFactors riskFactors;
        private List<String> mitigationRecommendations;
        private String assessmentTime;
        private String createdAt;
    }
    
    @Data
    public static class RiskFactors {
        private Integer attackCount;
        private Integer uniqueIps;
        private Integer uniquePorts;
        private Integer uniqueDevices;
        private Double timeWeight;
        private Double ipWeight;
        private Double portWeight;
        private Double deviceWeight;
    }
}
```

**使用示例**:

```java
public class ThreatAssessmentExample {
    
    public static void main(String[] args) {
        ThreatAssessmentClient client = new ThreatAssessmentClient();
        
        // 1. 使用默认5分钟时间窗口评估威胁
        AssessmentResponse assessment1 = client.evaluateCriticalThreat(
            "customer_a",
            "04:42:1a:8e:e3:65",
            "192.168.75.188",
            150, 5, 3, 2
        );
        
        // 2. 使用30秒时间窗口进行实时检测
        AssessmentResponse assessment2 = client.evaluateCriticalThreat(
            "customer_a",
            "04:42:1a:8e:e3:65",
            "192.168.75.188",
            50, 3, 2, 1, 30  // 30秒窗口
        );
        
        // 3. 使用15分钟时间窗口进行趋势分析
        AssessmentResponse assessment3 = client.evaluateCriticalThreat(
            "customer_a",
            "04:42:1a:8e:e3:65",
            "192.168.75.188",
            300, 8, 5, 3, 900  // 15分钟窗口
        );
        
        System.out.println("Default window (5min): " + assessment1.getThreatScore());
        System.out.println("Real-time window (30s): " + assessment2.getThreatScore());
        System.out.println("Trend window (15min): " + assessment3.getThreatScore());
    }
}
```

---

## 9. 最佳实践

### ✅ 推荐做法

#### 1. 异步处理高并发评估

```java
// ✅ 推荐: 使用异步处理
@Async
public CompletableFuture<AssessmentResponse> evaluateAsync(AssessmentRequest request) {
    return CompletableFuture.supplyAsync(() -> 
        client.evaluate(request)
    );
}
```

```java
// ❌ 不推荐: 同步阻塞处理大量评估
for (AssessmentRequest request : requests) {
    client.evaluate(request);  // 阻塞
}
```

#### 2. 循环处理多个评估请求

```java
// ✅ 推荐: 循环评估多个请求
List<AssessmentRequest> requests = prepareRequests(100);
List<AssessmentResponse> results = new ArrayList<>();

for (AssessmentRequest request : requests) {
    try {
        AssessmentResponse response = client.evaluate(request);
        results.add(response);
        
        // 记录高危威胁
        if ("HIGH".equals(response.getThreatLevel()) || 
            "CRITICAL".equals(response.getThreatLevel())) {
            System.err.println("⚠️ High-risk threat detected: " + response.getAttackIp());
        }
    } catch (Exception e) {
        System.err.println("Failed to assess: " + request.getAttackIp() + " - " + e.getMessage());
    }
}
```

```java
// ❌ 不推荐: 同步阻塞处理大量评估
for (AssessmentRequest request : requests) {
    client.evaluate(request);  // 阻塞，效率低
}
```

#### 3. 异常处理和重试

```java
// ✅ 推荐: 完善的异常处理
public AssessmentResponse evaluateWithRetry(AssessmentRequest request) {
    int maxRetries = 3;
    int retryCount = 0;
    
    while (retryCount < maxRetries) {
        try {
            return client.evaluate(request);
        } catch (HttpServerErrorException e) {
            retryCount++;
            if (retryCount >= maxRetries) {
                throw e;
            }
            Thread.sleep(1000 * retryCount);  // 指数退避
        }
    }
    
    throw new RuntimeException("Max retries exceeded");
}
```

#### 4. 缓存评估结果

```java
// ✅ 推荐: 缓存评估结果
@Cacheable(value = "assessments", key = "#assessmentId")
public AssessmentResponse getAssessmentDetails(String assessmentId) {
    return client.getAssessmentDetails(assessmentId);
}
```

---

## 10. 故障排查

### 问题1: 评估请求验证失败 (HTTP 400)

**症状**:
```
org.springframework.web.client.HttpClientErrorException$BadRequest: 400 Bad Request
{
  "errors": [
    {"field": "attackCount", "message": "must be greater than or equal to 1"}
  ]
}
```

**排查步骤**:

```bash
# 检查请求体
curl -X POST http://localhost:8083/api/v1/assessment/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": "customer_a",
    "attack_mac": "04:42:1a:8e:e3:65",
    "attack_ip": "192.168.75.188",
    "attack_count": 0,
    "unique_ips": 5,
    "unique_ports": 3,
    "unique_devices": 2,
    "timestamp": "2025-01-15T02:30:00Z"
  }' -v
```

**常见原因**:

| 原因 | 说明 | 解决方案 |
|-----|------|---------|
| attackCount < 1 | 必须 ≥ 1 | 确保 attackCount >= 1 |
| customerId为空 | 必需字段 | 确保 customerId 不为空 |
| timestamp格式错误 | 必须是ISO8601 | 使用 Instant.now().toString() |

---

### 问题2: 评估性能慢 (> 1秒)

**症状**:
```
评估耗时: 1500ms (预期: < 500ms)
```

**排查步骤**:

```bash
# 检查数据库连接池
curl http://localhost:8083/actuator/metrics/hikaricp.connections.active

# 检查评估API响应时间
curl -X POST http://localhost:8083/api/v1/assessment/evaluate \
  -H "Content-Type: application/json" \
  -d '...' \
  -w "\nTime: %{time_total}s\n"
```

**优化建议**:

1. **增加数据库连接池大小**:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # 增加连接池
      minimum-idle: 10
```

2. **添加数据库索引**:
```sql
CREATE INDEX idx_assessments_composite 
ON threat_assessments(customer_id, threat_level, assessment_time);
```

3. **使用异步评估**:
```java
@Async
public CompletableFuture<AssessmentResponse> evaluateAsync(AssessmentRequest request) {
    return CompletableFuture.supplyAsync(() -> evaluate(request));
}
```

---

### 问题3: 缓解措施执行失败 (HTTP 500)

**症状**:
```
Mitigation execution failed: Network isolation command failed
```

**排查步骤**:

```bash
# 检查评估记录是否存在
curl http://localhost:8083/api/v1/assessment/12345

# 检查服务日志
kubectl logs -f deployment/threat-assessment -n threat-detection
```

**常见原因**:
- 评估记录不存在
- 网络隔离命令执行权限不足
- 目标设备不可达

---

## 11. 相关文档

- [威胁评估概述](./threat_assessment_overview.md) - 系统架构和评分算法
- [查询和趋势API](./threat_assessment_query_api.md) - 查询评估历史和趋势分析
- [告警CRUD API](./alert_crud_api.md) - 告警管理API
- [蜜罐评分方案](../../docs/honeypot_based_threat_scoring.md) - 威胁评分算法详解
- [数据结构文档](../../docs/data_structures.md) - 完整数据结构定义

---

**文档结束**

*最后更新: 2025-11-04*  
*版本: 2.1*  
*维护团队: Security Platform Team*
