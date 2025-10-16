# 威胁评估系统概述

**服务名称**: Threat Assessment Service  
**服务端口**: 8081  
**基础路径**: `http://localhost:8081/api/v1/assessment`  
**版本**: 1.0  
**更新日期**: 2025-01-16

---

## 目录

1. [系统概述](#系统概述)
2. [架构定位](#架构定位)
3. [核心功能](#核心功能)
4. [威胁评分算法](#威胁评分算法)
5. [数据模型](#数据模型)
6. [API端点总览](#api端点总览)
7. [相关文档](#相关文档)

---

## 系统概述

### 系统职责

**威胁评估服务**是云原生威胁检测系统的核心分析组件，负责：

```
Kafka (threat-alerts) → Threat Assessment Service → PostgreSQL (threat_assessments)
                                ↓
                        风险等级评定、趋势分析、缓解建议
```

### 核心能力

1. **实时威胁评分**: 基于蜜罐机制的多维度威胁评分算法
2. **历史评估查询**: 按时间、客户、威胁等级查询历史评估
3. **趋势分析**: 时间序列威胁趋势分析和可视化
4. **自动缓解**: 根据威胁等级自动执行缓解措施
5. **多租户隔离**: 完整的客户数据隔离和安全保障

---

## 架构定位

### 数据流图

```
┌─────────────────┐
│ Stream Processing│ (Flink 威胁评分)
│    Service       │
└────────┬─────────┘
         │ Kafka: threat-alerts
         ↓
┌─────────────────────────────────────────────────┐
│    Threat Assessment Service (Port 8081)        │
│  ┌─────────────────────────────────────────┐   │
│  │  AssessmentController (REST API)        │   │
│  └──────────────┬──────────────────────────┘   │
│                 ↓                                │
│  ┌─────────────────────────────────────────┐   │
│  │  ThreatAssessmentService (业务逻辑)     │   │
│  │  - 威胁评估计算                         │   │
│  │  - 风险等级判定                         │   │
│  │  - 缓解策略执行                         │   │
│  │  - 趋势分析                             │   │
│  └──────────────┬──────────────────────────┘   │
│                 ↓                                │
│  ┌─────────────────────────────────────────┐   │
│  │  ThreatAssessmentRepository (数据访问) │   │
│  └──────────────┬──────────────────────────┘   │
└─────────────────┼──────────────────────────────┘
                  ↓
         ┌────────────────┐
         │   PostgreSQL   │
         │ threat_assessments
         └────────────────┘
```

### 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| **框架** | Spring Boot | 3.1.5 |
| **API文档** | Swagger/OpenAPI | 3.0 |
| **数据库** | PostgreSQL | 15+ |
| **消息队列** | Apache Kafka | 3.4+ |
| **序列化** | JSON (Jackson) | - |

---

## 核心功能

### 1. 威胁评估计算

**输入**: 
- 聚合攻击数据 (来自Flink流处理)
- 包含: attackCount, uniqueIps, uniquePorts, uniqueDevices
- 蜜罐机制数据: response_ip (诱饵IP), response_port (攻击意图)

**处理**:
1. 应用多维度评分算法
2. 计算时间权重 (深夜异常行为加权)
3. 计算IP权重 (横向移动范围)
4. 计算端口权重 (攻击意图多样性)
5. 计算设备权重 (多设备协同)

**输出**:
- 威胁评分 (0.0 - 无限大)
- 威胁等级 (INFO/LOW/MEDIUM/HIGH/CRITICAL)
- 评估详情和建议

### 2. 历史查询能力

**支持查询维度**:
- 按客户ID查询 (租户隔离)
- 按时间范围过滤
- 按威胁等级筛选
- 按攻击MAC地址搜索
- 分页和排序

### 3. 趋势分析

**时间维度**:
- 小时级趋势
- 日级趋势
- 周级趋势
- 月级趋势

**分析指标**:
- 威胁数量变化
- 平均威胁评分
- 威胁等级分布
- 攻击源IP分布

### 4. 自动缓解

**缓解策略**:

| 威胁等级 | 自动操作 | 手动操作 |
|---------|---------|---------|
| INFO | 仅记录 | - |
| LOW | 记录 + 监控 | 可选加入黑名单 |
| MEDIUM | 记录 + 告警 | 建议隔离 |
| HIGH | 记录 + 告警 + 通知 | 建议立即隔离 |
| CRITICAL | 记录 + 告警 + 通知 + 自动隔离 | 应急响应 |

---

## 威胁评分算法

### 完整公式

```
threatScore = (attackCount × uniqueIps × uniquePorts) 
            × timeWeight 
            × ipWeight 
            × portWeight 
            × deviceWeight
```

### 蜜罐机制理解

**关键**: 本系统基于蜜罐/诱饵防御机制

```
uniqueIps = 攻击者访问的诱饵IP数量 (横向移动范围)
uniquePorts = 攻击者尝试的端口种类 (攻击意图多样性)
attackCount = 对诱饵的探测次数 (所有访问都是恶意的)
```

**示例场景**:
```
攻击者 192.168.75.188 在1分钟内:
- 访问了5个诱饵IP (uniqueIps=5) → 说明在进行横向移动扫描
- 尝试了3个端口 (uniquePorts=3): 3389(RDP), 445(SMB), 22(SSH) → 多种攻击手段
- 发起了150次探测 (attackCount=150) → 高频率扫描
- 被2个蜜罐设备检测到 (uniqueDevices=2) → 影响范围广

威胁评分计算:
基础分 = 150 × 5 × 3 = 2250
时间权重 (凌晨2点) = 1.2
IP权重 (5个) = 1.5
端口权重 (3个) = 1.2
设备权重 (2个) = 1.5

最终评分 = 2250 × 1.2 × 1.5 × 1.2 × 1.5 = 7290 → CRITICAL
```

### 权重计算标准

#### 时间权重 (timeWeight)

| 时间段 | 权重 | 说明 |
|--------|------|------|
| 0:00-6:00 | 1.2 | 深夜异常行为 (APT常见) |
| 6:00-9:00 | 1.1 | 早晨时段 |
| 9:00-17:00 | 1.0 | 工作时间基准 |
| 17:00-21:00 | 0.9 | 傍晚时段 |
| 21:00-24:00 | 0.8 | 夜间时段 |

#### IP权重 (ipWeight)

| 唯一诱饵IP数量 | 权重 | 说明 |
|--------------|------|------|
| 1 | 1.0 | 单一目标攻击 |
| 2-3 | 1.3 | 小范围横向移动 |
| 4-5 | 1.5 | 中等范围扫描 |
| 6-10 | 1.7 | 广泛扫描 |
| 10+ | 2.0 | 大规模横向移动 (蠕虫/勒索软件特征) |

#### 端口权重 (portWeight)

| 唯一端口数量 | 权重 | 说明 |
|-------------|------|------|
| 1 | 1.0 | 单一攻击手段 |
| 2-3 | 1.2 | 小范围探测 |
| 4-5 | 1.4 | 中等多样性 |
| 6-10 | 1.6 | 广泛扫描 |
| 11-20 | 1.8 | 大规模扫描 |
| 20+ | 2.0 | 全端口扫描 |

#### 设备权重 (deviceWeight)

| 唯一蜜罐设备数量 | 权重 | 说明 |
|----------------|------|------|
| 1 | 1.0 | 单一网络段 |
| 2+ | 1.5 | 跨网络段攻击 (更严重) |

### 威胁等级划分

| 等级 | 分数范围 | 颜色 | 响应时间 | 典型场景 |
|------|---------|------|---------|---------|
| **INFO** | < 10 | 🔵 蓝色 | 无需响应 | 单次探测、误触 |
| **LOW** | 10-50 | 🟢 绿色 | 24小时内 | 单目标小规模探测 |
| **MEDIUM** | 50-100 | 🟡 黄色 | 4小时内 | 中等规模扫描 |
| **HIGH** | 100-200 | 🟠 橙色 | 1小时内 | 大规模扫描或深夜攻击 |
| **CRITICAL** | > 200 | 🔴 红色 | **立即响应** | 横向移动、勒索软件、APT |

---

## 数据模型

### AssessmentRequest (评估请求)

```json
{
  "customerId": "customer_a",
  "attackMac": "04:42:1a:8e:e3:65",
  "attackIp": "192.168.75.188",
  "attackCount": 150,
  "uniqueIps": 5,
  "uniquePorts": 3,
  "uniqueDevices": 2,
  "timestamp": "2025-01-15T02:30:00Z"
}
```

**字段说明**:

| 字段 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `customerId` | String | ✅ | 客户ID (租户隔离) |
| `attackMac` | String | ✅ | 被诱捕者MAC地址 |
| `attackIp` | String | ✅ | 被诱捕者IP地址 |
| `attackCount` | Integer | ✅ | 探测次数 |
| `uniqueIps` | Integer | ✅ | 访问的诱饵IP数量 |
| `uniquePorts` | Integer | ✅ | 尝试的端口种类 |
| `uniqueDevices` | Integer | ✅ | 检测到的蜜罐设备数 |
| `timestamp` | String (ISO8601) | ✅ | 评估时间 |

### AssessmentResponse (评估响应)

```json
{
  "assessmentId": "12345",
  "customerId": "customer_a",
  "attackMac": "04:42:1a:8e:e3:65",
  "attackIp": "192.168.75.188",
  "threatScore": 7290.0,
  "threatLevel": "CRITICAL",
  "riskFactors": {
    "attackCount": 150,
    "uniqueIps": 5,
    "uniquePorts": 3,
    "uniqueDevices": 2,
    "timeWeight": 1.2,
    "ipWeight": 1.5,
    "portWeight": 1.2,
    "deviceWeight": 1.5
  },
  "mitigationRecommendations": [
    "立即隔离攻击源 192.168.75.188",
    "检查同网段其他主机是否被攻陷",
    "审计攻击源的网络访问日志",
    "启动应急响应流程"
  ],
  "assessmentTime": "2025-01-15T02:30:00Z",
  "createdAt": "2025-01-15T02:30:05Z"
}
```

**字段说明**:

| 字段 | 类型 | 说明 |
|-----|------|------|
| `assessmentId` | String | 评估记录ID |
| `customerId` | String | 客户ID |
| `attackMac` | String | 攻击者MAC |
| `attackIp` | String | 攻击者IP |
| `threatScore` | Double | 威胁评分 |
| `threatLevel` | String | 威胁等级 (INFO/LOW/MEDIUM/HIGH/CRITICAL) |
| `riskFactors` | Object | 风险因素详情 (各维度权重) |
| `mitigationRecommendations` | String[] | 缓解建议列表 |
| `assessmentTime` | String | 评估时间 |
| `createdAt` | String | 记录创建时间 |

### ThreatTrend (威胁趋势)

```json
{
  "customerId": "customer_a",
  "timeRange": {
    "start": "2025-01-15T00:00:00Z",
    "end": "2025-01-15T23:59:59Z"
  },
  "granularity": "HOURLY",
  "dataPoints": [
    {
      "timestamp": "2025-01-15T00:00:00Z",
      "threatCount": 15,
      "averageScore": 45.2,
      "levelDistribution": {
        "INFO": 5,
        "LOW": 8,
        "MEDIUM": 2,
        "HIGH": 0,
        "CRITICAL": 0
      }
    },
    {
      "timestamp": "2025-01-15T01:00:00Z",
      "threatCount": 23,
      "averageScore": 125.8,
      "levelDistribution": {
        "INFO": 2,
        "LOW": 5,
        "MEDIUM": 10,
        "HIGH": 4,
        "CRITICAL": 2
      }
    }
  ],
  "summary": {
    "totalThreats": 450,
    "averageScore": 85.3,
    "highestScore": 7290.0,
    "criticalCount": 12
  }
}
```

---

## API端点总览

### 完整端点列表

| 方法 | 端点 | 功能 | 文档链接 |
|------|------|------|---------|
| `POST` | `/api/v1/assessment/evaluate` | 执行威胁评估 | [详细文档](./threat_assessment_evaluation_api.md) |
| `GET` | `/api/v1/assessment/{assessmentId}` | 获取评估详情 | [详细文档](./threat_assessment_query_api.md#获取评估详情) |
| `GET` | `/api/v1/assessment/trends` | 威胁趋势分析 | [详细文档](./threat_assessment_query_api.md#威胁趋势分析) |
| `POST` | `/api/v1/assessment/mitigation/{assessmentId}` | 执行缓解措施 | [详细文档](./threat_assessment_evaluation_api.md#执行缓解措施) |
| `GET` | `/api/v1/assessment/health` | 健康检查 | [详细文档](./threat_assessment_query_api.md#健康检查) |

### 按功能分类

#### 核心评估 (2个端点)
- **POST /evaluate** - 执行威胁评估
- **POST /mitigation/{assessmentId}** - 执行缓解措施

→ 详见 [威胁评估API文档](./threat_assessment_evaluation_api.md)

#### 查询分析 (3个端点)
- **GET /{assessmentId}** - 获取评估详情
- **GET /trends** - 威胁趋势分析
- **GET /health** - 健康检查

→ 详见 [查询和趋势API文档](./threat_assessment_query_api.md)

#### 客户端集成
- Java客户端完整实现
- 最佳实践和故障排查

→ 详见 [客户端指南](./threat_assessment_client_guide.md)

---

## 相关文档

### 威胁评估系列文档

1. **[威胁评估API - 评估操作](./threat_assessment_evaluation_api.md)**
   - POST /evaluate - 执行威胁评估
   - POST /mitigation/{assessmentId} - 执行缓解措施

2. **[威胁评估API - 查询分析](./threat_assessment_query_api.md)**
   - GET /{assessmentId} - 获取评估详情
   - GET /trends - 威胁趋势分析
   - GET /health - 健康检查

3. **[威胁评估客户端指南](./threat_assessment_client_guide.md)**
   - Java客户端完整实现
   - 使用场景和最佳实践
   - 故障排查指南

### 系统架构文档

- **[蜜罐威胁评分方案](./honeypot_based_threat_scoring.md)** - 评分算法详解
- **[数据摄取API](./data_ingestion_api.md)** - 日志摄取服务
- **[告警管理概述](./alert_management_overview.md)** - 告警系统架构
- **[邮件通知配置](./email_notification_configuration.md)** - 通知系统

---

**文档结束**

*最后更新: 2025-01-16*
