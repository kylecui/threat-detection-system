# 权重管理API文档

**服务名称**: Threat Assessment Service - Weight Management  
**服务端口**: 8083  
**基础路径**: `/api/v1/weights`  
**版本**: 5.0  
**更新日期**: 2025-11-01

---

## 目录

1. [系统概述](#系统概述)
2. [核心功能](#核心功能)
3. [权重体系说明](#权重体系说明)
4. [数据模型](#数据模型)
5. [API端点列表](#api端点列表)
6. [API详细文档](#api详细文档)
   - [6.1 攻击源权重管理](#61-攻击源权重管理)
   - [6.2 蜜罐敏感度权重管理](#62-蜜罐敏感度权重管理)
   - [6.3 攻击阶段端口配置管理](#63-攻击阶段端口配置管理)
   - [6.4 APT时序累积管理](#64-apt时序累积管理)
   - [6.5 统计信息查询](#65-统计信息查询)
7. [使用场景](#使用场景)
8. [Java客户端完整示例](#java客户端完整示例)
9. [最佳实践](#最佳实践)
10. [故障排查](#故障排查)
11. [相关文档](#相关文档)

---

## 系统概述

### 核心职责

权重管理服务是威胁检测系统的配置中枢，负责管理多维度威胁评分的权重参数，支持多租户隔离和动态配置更新。

```
权重配置管理 → 威胁评分引擎 → 动态权重计算
     ↓              ↓              ↓
  多租户存储    实时查询      评分算法输入
```

### 架构定位

权重管理服务作为威胁评估服务的子模块，提供以下四类权重配置：

1. **攻击源权重**: 基于IP段的攻击源风险评估
2. **蜜罐敏感度权重**: 蜜罐设备对不同IP段的敏感度配置
3. **攻击阶段端口配置**: 不同攻击阶段的端口风险分类
4. **APT时序累积**: APT攻击的时间序列累积评分

### 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| **框架** | Spring Boot | 3.1.5 |
| **API文档** | Swagger/OpenAPI | 3.0 |
| **数据库** | PostgreSQL | 15+ |
| **缓存** | Redis | 7.0+ |
| **序列化** | JSON (Jackson) | - |

---

## 核心功能

### 1. 多租户权重配置

**支持四种权重类型**:
- ✅ **攻击源权重**: IP段级别风险评估
- ✅ **蜜罐敏感度**: 设备敏感度配置
- ✅ **攻击阶段端口**: 端口风险分类
- ✅ **APT时序累积**: 时间序列威胁累积

**多租户特性**:
- 客户级隔离: 每个客户独立配置
- 继承机制: 支持全局默认 + 客户自定义
- 动态更新: 配置变更实时生效

### 2. 配置管理能力

**CRUD操作**:
- 创建/更新配置 (Upsert模式)
- 批量查询配置
- 条件过滤查询
- 启用/禁用控制
- 统计信息查询

**数据一致性**:
- 事务保证
- 并发控制
- 数据验证
- 审计日志

### 3. 缓存优化

**多级缓存策略**:
- JVM本地缓存: 高频查询
- Redis分布式缓存: 跨实例共享
- 数据库直查: 兜底保证

---

## 权重体系说明

### 攻击源权重 (Attack Source Weight)

基于IP段的攻击源风险评估权重：

| 权重范围 | 风险等级 | 说明 | 示例IP段 |
|---------|---------|------|---------|
| 0.1-0.5 | 极低风险 | 可信内部网络 | 192.168.0.0/16 |
| 0.5-0.8 | 低风险 | 企业内网 | 10.0.0.0/8 |
| 0.8-1.0 | 中等风险 | 普通公网 | 正常公网IP |
| 1.0-1.5 | 高风险 | 可疑来源 | 已知扫描源 |
| 1.5-2.0 | 极高风险 | 恶意来源 | 已知攻击源 |

### 蜜罐敏感度权重 (Honeypot Sensitivity Weight)

蜜罐设备对不同IP段的响应敏感度：

| 权重范围 | 敏感度 | 说明 |
|---------|-------|------|
| 0.1-0.3 | 极低敏感 | 完全忽略 |
| 0.3-0.7 | 低敏感 | 基础监控 |
| 0.7-1.0 | 中等敏感 | 标准响应 |
| 1.0-1.5 | 高敏感 | 增强监控 |
| 1.5-2.0 | 极高敏感 | 全力响应 |

### 攻击阶段端口配置 (Attack Phase Port Config)

不同攻击阶段的端口风险分类：

| 阶段 | 说明 | 端口示例 | 风险权重 |
|------|------|---------|---------|
| RECON | 侦察阶段 | 21, 22, 23, 25, 53, 80, 443 | 1.0-1.2 |
| INITIAL_ACCESS | 初始访问 | 3389, 5900, 5938 | 1.5-2.0 |
| EXECUTION | 执行阶段 | 135, 139, 445 | 1.8-2.5 |
| PERSISTENCE | 持久化 | 3389, 5938 | 2.0-3.0 |
| PRIVILEGE_ESCALATION | 权限提升 | 445, 3389 | 2.5-3.5 |
| DEFENSE_EVASION | 防御规避 | 各种端口 | 2.0-2.5 |
| CREDENTIAL_ACCESS | 凭证访问 | 445, 3389, 5938 | 2.5-3.5 |
| DISCOVERY | 发现阶段 | 135, 137, 139, 445 | 1.5-2.0 |
| LATERAL_MOVEMENT | 横向移动 | 445, 3389, 5938 | 3.0-4.0 |
| COLLECTION | 收集阶段 | 445, 3389 | 2.0-2.5 |
| COMMAND_AND_CONTROL | 命令控制 | 80, 443, 53 | 2.5-3.5 |
| EXFILTRATION | 外泄阶段 | 21, 22, 80, 443 | 2.0-3.0 |
| IMPACT | 影响阶段 | 445, 3389 | 3.5-5.0 |

### APT时序累积 (APT Temporal Accumulation)

APT攻击的时间序列累积评分：

| 时间窗口 | 累积策略 | 衰减因子 | 说明 |
|---------|---------|---------|------|
| 1小时 | 线性累积 | 0.9 | 短期活跃 |
| 6小时 | 加权累积 | 0.8 | 中期趋势 |
| 24小时 | 指数衰减 | 0.7 | 长期威胁 |
| 7天 | 对数衰减 | 0.5 | 战略威胁 |

---

## 数据模型

### AttackSourceWeightDto

```json
{
  "id": 1,
  "customerId": "customer-001",
  "ipSegment": "192.168.1.0/24",
  "weight": 0.8,
  "description": "企业内网",
  "isActive": true,
  "createdAt": "2025-10-31T10:00:00Z",
  "updatedAt": "2025-10-31T10:00:00Z"
}
```

### HoneypotSensitivityWeightDto

```json
{
  "id": 1,
  "customerId": "customer-001",
  "ipSegment": "10.0.0.0/8",
  "weight": 1.2,
  "description": "高敏感企业网段",
  "isActive": true,
  "createdAt": "2025-10-31T10:00:00Z",
  "updatedAt": "2025-10-31T10:00:00Z"
}
```

### AttackPhasePortConfigDto

```json
{
  "id": 1,
  "customerId": "customer-001",
  "phase": "LATERAL_MOVEMENT",
  "port": 445,
  "weight": 3.5,
  "description": "SMB横向移动",
  "isActive": true,
  "createdAt": "2025-10-31T10:00:00Z",
  "updatedAt": "2025-10-31T10:00:00Z"
}
```

### AptTemporalAccumulationDto

```json
{
  "id": 1,
  "customerId": "customer-001",
  "attackMac": "00:11:22:33:44:55",
  "windowStart": "2025-10-31T10:00:00Z",
  "windowEnd": "2025-10-31T11:00:00Z",
  "accumulatedScore": 150.5,
  "decayAccumulatedScore": 135.45,
  "eventCount": 25,
  "lastUpdated": "2025-10-31T10:30:00Z"
}
```

---

## API端点列表

### 攻击源权重管理 (6个端点)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/attack-source/{customerId}` | 获取客户的所有攻击源权重配置 |
| GET | `/attack-source/{customerId}/active` | 获取客户的活跃配置 |
| POST | `/attack-source` | 创建或更新配置 |
| DELETE | `/attack-source/{customerId}` | 删除指定IP段配置 |
| PATCH | `/attack-source/{customerId}/enable` | 启用配置 |
| PATCH | `/attack-source/{customerId}/disable` | 禁用配置 |

### 蜜罐敏感度权重管理 (3个端点)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/honeypot-sensitivity/{customerId}` | 获取客户的所有配置 |
| POST | `/honeypot-sensitivity` | 创建或更新配置 |
| DELETE | `/honeypot-sensitivity/{customerId}` | 删除指定IP段配置 |

### 攻击阶段端口配置管理 (5个端点)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/attack-phase/{customerId}` | 获取客户的所有配置 |
| GET | `/attack-phase/{customerId}/{phase}` | 获取指定阶段配置 |
| GET | `/attack-phase/{customerId}/{phase}/effective` | 获取有效配置 |
| POST | `/attack-phase` | 创建或更新配置 |
| DELETE | `/attack-phase/{customerId}/{phase}/{port}` | 删除指定配置 |

### APT时序累积管理 (7个端点)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/apt-temporal/{customerId}` | 获取客户的所有累积数据 |
| GET | `/apt-temporal/{customerId}/{attackMac}` | 获取指定MAC的累积数据 |
| GET | `/apt-temporal/{customerId}/range` | 获取时间范围内的数据 |
| POST | `/apt-temporal` | 创建或更新累积数据 |
| PUT | `/apt-temporal/{customerId}/{attackMac}` | 更新累积分数 |
| DELETE | `/apt-temporal/{customerId}/{attackMac}` | 删除累积数据 |
| GET | `/apt-temporal/{customerId}/{attackMac}/threat-score` | 获取当前威胁分数 |

### 统计信息查询 (4个端点)

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/attack-source/{customerId}/statistics` | 攻击源权重统计 |
| GET | `/honeypot-sensitivity/{customerId}/statistics` | 蜜罐敏感度统计 |
| GET | `/attack-phase/{customerId}/statistics` | 攻击阶段端口统计 |
| GET | `/apt-temporal/{customerId}/statistics` | APT时序累积统计 |

---

## API详细文档

### 6.1 攻击源权重管理

#### 获取客户的所有攻击源权重配置

**端点**: `GET /api/v1/weights/attack-source/{customerId}`

**功能**: 返回指定客户的所有攻击源权重配置

**参数**:
- `customerId` (路径参数): 客户ID

**响应**:
```json
[
  {
    "id": 1,
    "customerId": "customer-001",
    "ipSegment": "192.168.1.0/24",
    "weight": 0.8,
    "description": "企业内网",
    "isActive": true,
    "createdAt": "2025-10-31T10:00:00Z",
    "updatedAt": "2025-10-31T10:00:00Z"
  }
]
```

**curl示例**:
```bash
curl -X GET "http://localhost:8081/api/v1/weights/attack-source/customer-001" \
  -H "Accept: application/json"
```

#### 创建或更新攻击源权重配置

**端点**: `POST /api/v1/weights/attack-source`

**功能**: 为指定客户创建或更新攻击源权重配置

**请求体**:
```json
{
  "customerId": "customer-001",
  "ipSegment": "192.168.1.0/24",
  "weight": 0.8,
  "description": "企业内网",
  "isActive": true
}
```

**响应**:
```json
{
  "id": 1,
  "customerId": "customer-001",
  "ipSegment": "192.168.1.0/24",
  "weight": 0.8,
  "description": "企业内网",
  "isActive": true,
  "createdAt": "2025-10-31T10:00:00Z",
  "updatedAt": "2025-10-31T10:00:00Z"
}
```

**curl示例**:
```bash
curl -X POST "http://localhost:8081/api/v1/weights/attack-source" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "customer-001",
    "ipSegment": "192.168.1.0/24",
    "weight": 0.8,
    "description": "企业内网",
    "isActive": true
  }'
```

#### 删除攻击源权重配置

**端点**: `DELETE /api/v1/weights/attack-source/{customerId}`

**功能**: 删除指定客户的指定IP段权重配置

**参数**:
- `customerId` (路径参数): 客户ID
- `ipSegment` (查询参数): IP段标识

**curl示例**:
```bash
curl -X DELETE "http://localhost:8081/api/v1/weights/attack-source/customer-001?ipSegment=192.168.1.0/24"
```

### 6.2 蜜罐敏感度权重管理

#### 获取客户的所有蜜罐敏感度权重配置

**端点**: `GET /api/v1/weights/honeypot-sensitivity/{customerId}`

**功能**: 返回指定客户的所有蜜罐敏感度权重配置

**参数**:
- `customerId` (路径参数): 客户ID

**响应**:
```json
[
  {
    "id": 1,
    "customerId": "customer-001",
    "ipSegment": "10.0.0.0/8",
    "weight": 1.2,
    "description": "高敏感企业网段",
    "isActive": true,
    "createdAt": "2025-10-31T10:00:00Z",
    "updatedAt": "2025-10-31T10:00:00Z"
  }
]
```

**curl示例**:
```bash
curl -X GET "http://localhost:8081/api/v1/weights/honeypot-sensitivity/customer-001" \
  -H "Accept: application/json"
```

### 6.3 攻击阶段端口配置管理

#### 获取客户的所有攻击阶段端口配置

**端点**: `GET /api/v1/weights/attack-phase/{customerId}`

**功能**: 返回指定客户的所有攻击阶段端口配置

**参数**:
- `customerId` (路径参数): 客户ID

**响应**:
```json
[
  {
    "id": 1,
    "customerId": "customer-001",
    "phase": "LATERAL_MOVEMENT",
    "port": 445,
    "weight": 3.5,
    "description": "SMB横向移动",
    "isActive": true,
    "createdAt": "2025-10-31T10:00:00Z",
    "updatedAt": "2025-10-31T10:00:00Z"
  }
]
```

**curl示例**:
```bash
curl -X GET "http://localhost:8081/api/v1/weights/attack-phase/customer-001" \
  -H "Accept: application/json"
```

#### 获取有效的端口配置

**端点**: `GET /api/v1/weights/attack-phase/{customerId}/{phase}/effective`

**功能**: 返回客户自定义配置，不存在时返回全局默认配置

**参数**:
- `customerId` (路径参数): 客户ID
- `phase` (路径参数): 攻击阶段

**curl示例**:
```bash
curl -X GET "http://localhost:8081/api/v1/weights/attack-phase/customer-001/LATERAL_MOVEMENT/effective" \
  -H "Accept: application/json"
```

### 6.4 APT时序累积管理

#### 获取客户的所有APT时序累积数据

**端点**: `GET /api/v1/weights/apt-temporal/{customerId}`

**功能**: 返回指定客户的所有APT时序累积数据

**参数**:
- `customerId` (路径参数): 客户ID

**响应**:
```json
[
  {
    "id": 1,
    "customerId": "customer-001",
    "attackMac": "00:11:22:33:44:55",
    "windowStart": "2025-10-31T10:00:00Z",
    "windowEnd": "2025-10-31T11:00:00Z",
    "accumulatedScore": 150.5,
    "decayAccumulatedScore": 135.45,
    "eventCount": 25,
    "lastUpdated": "2025-10-31T10:30:00Z"
  }
]
```

**curl示例**:
```bash
curl -X GET "http://localhost:8081/api/v1/weights/apt-temporal/customer-001" \
  -H "Accept: application/json"
```

#### 获取当前威胁分数

**端点**: `GET /api/v1/weights/apt-temporal/{customerId}/{attackMac}/threat-score`

**功能**: 计算指定MAC地址的当前累积威胁分数

**参数**:
- `customerId` (路径参数): 客户ID
- `attackMac` (路径参数): 攻击者MAC地址

**响应**:
```json
{
  "customerId": "customer-001",
  "attackMac": "00:11:22:33:44:55",
  "threatScore": 245.67,
  "calculatedAt": "2025-10-31T10:35:00Z"
}
```

**curl示例**:
```bash
curl -X GET "http://localhost:8081/api/v1/weights/apt-temporal/customer-001/00:11:22:33:44:55/threat-score" \
  -H "Accept: application/json"
```

### 6.5 统计信息查询

#### 获取攻击源权重统计信息

**端点**: `GET /api/v1/weights/attack-source/{customerId}/statistics`

**功能**: 返回指定客户的攻击源权重配置统计信息

**参数**:
- `customerId` (路径参数): 客户ID

**响应**:
```json
[
  "total_configs",
  15,
  "active_configs",
  12,
  "inactive_configs",
  3,
  "avg_weight",
  1.25
]
```

**curl示例**:
```bash
curl -X GET "http://localhost:8081/api/v1/weights/attack-source/customer-001/statistics" \
  -H "Accept: application/json"
```

---

## 使用场景

### 场景1: 配置企业内网低权重

**业务需求**: 企业内网IP段应降低威胁评分，避免误报

**API调用**:
```bash
# 配置内网IP段权重
curl -X POST "http://localhost:8081/api/v1/weights/attack-source" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "enterprise-001",
    "ipSegment": "192.168.0.0/16",
    "weight": 0.6,
    "description": "企业内网 - 降低误报",
    "isActive": true
  }'

# 配置DMZ区中等权重
curl -X POST "http://localhost:8081/api/v1/weights/attack-source" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "enterprise-001",
    "ipSegment": "172.16.0.0/12",
    "weight": 0.9,
    "description": "DMZ区 - 标准监控",
    "isActive": true
  }'
```

### 场景2: 配置高危端口监控

**业务需求**: 对横向移动相关端口提高监控敏感度

**API调用**:
```bash
# 配置SMB端口高权重
curl -X POST "http://localhost:8081/api/v1/weights/attack-phase" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "security-001",
    "phase": "LATERAL_MOVEMENT",
    "port": 445,
    "weight": 4.0,
    "description": "SMB横向移动 - 最高优先级",
    "isActive": true
  }'

# 配置RDP端口高权重
curl -X POST "http://localhost:8081/api/v1/weights/attack-phase" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "security-001",
    "phase": "LATERAL_MOVEMENT",
    "port": 3389,
    "weight": 3.8,
    "description": "RDP远程控制 - 高优先级",
    "isActive": true
  }'
```

### 场景3: 监控APT攻击累积

**业务需求**: 跟踪长期潜伏的APT攻击模式

**API调用**:
```bash
# 查询特定攻击者的累积威胁
curl -X GET "http://localhost:8081/api/v1/weights/apt-temporal/security-001/00:11:22:33:44:55/threat-score"

# 查询时间范围内的累积数据
curl -X GET "http://localhost:8081/api/v1/weights/apt-temporal/security-001/range?startTime=2025-10-30T00:00:00Z&endTime=2025-10-31T00:00:00Z"
```

### 场景4: 蜜罐敏感度调优

**业务需求**: 对已知恶意IP段提高蜜罐响应敏感度

**API调用**:
```bash
# 配置已知恶意IP段高敏感度
curl -X POST "http://localhost:8081/api/v1/weights/honeypot-sensitivity" \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "honeypot-001",
    "ipSegment": "203.0.113.0/24",
    "weight": 1.8,
    "description": "已知恶意扫描源 - 最高敏感度",
    "isActive": true
  }'
```

---

## Java客户端完整示例

### 完整客户端实现

```java
package com.threatdetection.client;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 权重管理服务完整客户端
 * 
 * 提供四类权重配置的完整管理功能：
 * 1. 攻击源权重配置
 * 2. 蜜罐敏感度权重配置
 * 3. 攻击阶段端口配置
 * 4. APT时序累积数据管理
 */
@Slf4j
public class WeightManagementClient {
    
    private final String baseUrl;
    private final RestTemplate restTemplate;
    
    public WeightManagementClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
    }
    
    // ==================== 攻击源权重管理 ====================
    
    /**
     * 获取客户的攻击源权重配置
     */
    public List<AttackSourceWeightDto> getAttackSourceWeights(String customerId) {
        String url = baseUrl + "/api/v1/weights/attack-source/" + customerId;
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }
    
    /**
     * 获取客户的活跃攻击源权重配置
     */
    public List<AttackSourceWeightDto> getActiveAttackSourceWeights(String customerId) {
        String url = baseUrl + "/api/v1/weights/attack-source/" + customerId + "/active";
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }
    
    /**
     * 创建或更新攻击源权重配置
     */
    public AttackSourceWeightDto saveAttackSourceWeight(AttackSourceWeightDto dto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<AttackSourceWeightDto> httpRequest = new HttpEntity<>(dto, headers);
        
        ResponseEntity<AttackSourceWeightDto> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/weights/attack-source",
            httpRequest,
            AttackSourceWeightDto.class
        );
        
        return response.getBody();
    }
    
    /**
     * 删除攻击源权重配置
     */
    public boolean deleteAttackSourceWeight(String customerId, String ipSegment) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/v1/weights/attack-source/" + customerId)
            .queryParam("ipSegment", ipSegment)
            .toUriString();
            
        ResponseEntity<Void> response = restTemplate.exchange(
            url,
            HttpMethod.DELETE,
            null,
            Void.class
        );
        
        return response.getStatusCode() == HttpStatus.NO_CONTENT;
    }
    
    /**
     * 启用攻击源权重配置
     */
    public boolean enableAttackSourceWeight(String customerId, String ipSegment) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/v1/weights/attack-source/" + customerId + "/enable")
            .queryParam("ipSegment", ipSegment)
            .toUriString();
            
        ResponseEntity<Void> response = restTemplate.exchange(
            url,
            HttpMethod.PATCH,
            null,
            Void.class
        );
        
        return response.getStatusCode() == HttpStatus.OK;
    }
    
    // ==================== 蜜罐敏感度权重管理 ====================
    
    /**
     * 获取客户的蜜罐敏感度权重配置
     */
    public List<HoneypotSensitivityWeightDto> getHoneypotSensitivityWeights(String customerId) {
        String url = baseUrl + "/api/v1/weights/honeypot-sensitivity/" + customerId;
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }
    
    /**
     * 创建或更新蜜罐敏感度权重配置
     */
    public HoneypotSensitivityWeightDto saveHoneypotSensitivityWeight(HoneypotSensitivityWeightDto dto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<HoneypotSensitivityWeightDto> httpRequest = new HttpEntity<>(dto, headers);
        
        ResponseEntity<HoneypotSensitivityWeightDto> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/weights/honeypot-sensitivity",
            httpRequest,
            HoneypotSensitivityWeightDto.class
        );
        
        return response.getBody();
    }
    
    // ==================== 攻击阶段端口配置管理 ====================
    
    /**
     * 获取客户的攻击阶段端口配置
     */
    public List<AttackPhasePortConfigDto> getAttackPhasePortConfigs(String customerId) {
        String url = baseUrl + "/api/v1/weights/attack-phase/" + customerId;
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }
    
    /**
     * 获取指定阶段的端口配置
     */
    public List<AttackPhasePortConfigDto> getAttackPhasePortConfigsByPhase(String customerId, String phase) {
        String url = baseUrl + "/api/v1/weights/attack-phase/" + customerId + "/" + phase;
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }
    
    /**
     * 获取有效的端口配置
     */
    public List<AttackPhasePortConfigDto> getEffectiveAttackPhasePortConfigs(String customerId, String phase) {
        String url = baseUrl + "/api/v1/weights/attack-phase/" + customerId + "/" + phase + "/effective";
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }
    
    /**
     * 创建或更新攻击阶段端口配置
     */
    public AttackPhasePortConfigDto saveAttackPhasePortConfig(AttackPhasePortConfigDto dto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<AttackPhasePortConfigDto> httpRequest = new HttpEntity<>(dto, headers);
        
        ResponseEntity<AttackPhasePortConfigDto> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/weights/attack-phase",
            httpRequest,
            AttackPhasePortConfigDto.class
        );
        
        return response.getBody();
    }
    
    // ==================== APT时序累积管理 ====================
    
    /**
     * 获取客户的APT时序累积数据
     */
    public List<AptTemporalAccumulationDto> getAptTemporalAccumulations(String customerId) {
        String url = baseUrl + "/api/v1/weights/apt-temporal/" + customerId;
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }
    
    /**
     * 获取指定MAC的时序累积数据
     */
    public List<AptTemporalAccumulationDto> getAptTemporalAccumulationsByMac(String customerId, String attackMac) {
        String url = baseUrl + "/api/v1/weights/apt-temporal/" + customerId + "/" + attackMac;
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }
    
    /**
     * 获取时间范围内的时序累积数据
     */
    public List<AptTemporalAccumulationDto> getAptTemporalAccumulationsByTimeRange(
            String customerId, Instant startTime, Instant endTime) {
        
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/v1/weights/apt-temporal/" + customerId + "/range")
            .queryParam("startTime", startTime)
            .queryParam("endTime", endTime)
            .toUriString();
            
        ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
        return response.getBody();
    }
    
    /**
     * 创建或更新APT时序累积数据
     */
    public AptTemporalAccumulationDto saveAptTemporalAccumulation(AptTemporalAccumulationDto dto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<AptTemporalAccumulationDto> httpRequest = new HttpEntity<>(dto, headers);
        
        ResponseEntity<AptTemporalAccumulationDto> response = restTemplate.postForEntity(
            baseUrl + "/api/v1/weights/apt-temporal",
            httpRequest,
            AptTemporalAccumulationDto.class
        );
        
        return response.getBody();
    }
    
    /**
     * 获取当前威胁分数
     */
    public Map<String, Object> getCurrentThreatScore(String customerId, String attackMac) {
        String url = baseUrl + "/api/v1/weights/apt-temporal/" + customerId + "/" + attackMac + "/threat-score";
        return restTemplate.getForObject(url, Map.class);
    }
    
    // ==================== 统计信息查询 ====================
    
    /**
     * 获取攻击源权重统计信息
     */
    public Object[] getAttackSourceWeightStatistics(String customerId) {
        String url = baseUrl + "/api/v1/weights/attack-source/" + customerId + "/statistics";
        return restTemplate.getForObject(url, Object[].class);
    }
    
    /**
     * 获取攻击阶段端口配置统计信息
     */
    public Object[] getAttackPhasePortConfigStatistics(String customerId) {
        String url = baseUrl + "/api/v1/weights/attack-phase/" + customerId + "/statistics";
        return restTemplate.getForObject(url, Object[].class);
    }
}

// ===== 数据模型 =====

/**
 * 攻击源权重配置DTO
 */
class AttackSourceWeightDto {
    private Long id;
    private String customerId;
    private String ipSegment;
    private BigDecimal weight;
    private String description;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    
    // getters and setters
}

/**
 * 蜜罐敏感度权重配置DTO
 */
class HoneypotSensitivityWeightDto {
    private Long id;
    private String customerId;
    private String ipSegment;
    private BigDecimal weight;
    private String description;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    
    // getters and setters
}

/**
 * 攻击阶段端口配置DTO
 */
class AttackPhasePortConfigDto {
    private Long id;
    private String customerId;
    private String phase;
    private Integer port;
    private BigDecimal weight;
    private String description;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
    
    // getters and setters
}

/**
 * APT时序累积数据DTO
 */
class AptTemporalAccumulationDto {
    private Long id;
    private String customerId;
    private String attackMac;
    private Instant windowStart;
    private Instant windowEnd;
    private BigDecimal accumulatedScore;
    private BigDecimal decayAccumulatedScore;
    private Integer eventCount;
    private Instant lastUpdated;
    
    // getters and setters
}
```

### 使用示例

```java
public class WeightManagementExample {
    
    public static void main(String[] args) {
        WeightManagementClient client = new WeightManagementClient("http://localhost:8081");
        
        try {
            // 1. 配置攻击源权重
            AttackSourceWeightDto attackSource = new AttackSourceWeightDto();
            attackSource.setCustomerId("enterprise-001");
            attackSource.setIpSegment("192.168.1.0/24");
            attackSource.setWeight(BigDecimal.valueOf(0.7));
            attackSource.setDescription("企业内网");
            attackSource.setIsActive(true);
            
            AttackSourceWeightDto savedAttackSource = client.saveAttackSourceWeight(attackSource);
            System.out.println("Saved attack source weight: " + savedAttackSource.getId());
            
            // 2. 配置攻击阶段端口权重
            AttackPhasePortConfigDto portConfig = new AttackPhasePortConfigDto();
            portConfig.setCustomerId("security-001");
            portConfig.setPhase("LATERAL_MOVEMENT");
            portConfig.setPort(445);
            portConfig.setWeight(BigDecimal.valueOf(3.5));
            portConfig.setDescription("SMB横向移动");
            portConfig.setIsActive(true);
            
            AttackPhasePortConfigDto savedPortConfig = client.saveAttackPhasePortConfig(portConfig);
            System.out.println("Saved port config: " + savedPortConfig.getId());
            
            // 3. 查询APT威胁分数
            Map<String, Object> threatScore = client.getCurrentThreatScore("security-001", "00:11:22:33:44:55");
            System.out.println("Current threat score: " + threatScore.get("threatScore"));
            
            // 4. 获取统计信息
            Object[] stats = client.getAttackSourceWeightStatistics("enterprise-001");
            System.out.println("Total configs: " + stats[1]);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
```

---

## 最佳实践

### 1. 配置管理

**权重值选择**:
- 攻击源权重: 0.1-2.0，根据风险等级选择
- 蜜罐敏感度: 0.1-2.0，根据监控需求选择
- 端口权重: 1.0-5.0，根据攻击阶段重要性选择
- APT累积: 自动计算，通常不需要手动配置

**配置更新策略**:
- 小心调整权重值，避免误报或漏报
- 先在测试环境验证配置效果
- 记录配置变更历史，便于回滚
- 定期review和优化配置

### 2. 性能优化

**查询优化**:
- 使用分页查询大量数据
- 合理使用缓存机制
- 避免频繁的实时统计查询

**批量操作**:
- 支持批量创建/更新配置
- 使用异步处理大量数据
- 监控系统资源使用情况

### 3. 监控告警

**关键指标监控**:
- 配置变更频率
- API响应时间
- 缓存命中率
- 数据库连接池状态

**告警规则**:
- 配置服务不可用
- 响应时间超过阈值
- 数据库连接池耗尽

---

## 故障排查

### 1. 配置无法保存

**症状**: POST请求返回400错误

**可能原因**:
- 权重值超出有效范围
- IP段格式不正确
- 客户ID不存在

**解决方法**:
```bash
# 检查权重值范围
# 攻击源权重: 0.1-2.0
# 蜜罐敏感度: 0.1-2.0
# 端口权重: 1.0-5.0

# 验证IP段格式
# 正确: 192.168.1.0/24
# 错误: 192.168.1.0/33 (前缀长度无效)
```

### 2. 查询返回空结果

**症状**: GET请求返回空数组

**可能原因**:
- 客户ID不存在
- 配置被禁用
- 数据库连接问题

**检查步骤**:
```bash
# 1. 验证客户ID
curl -X GET "http://localhost:8081/api/v1/customers/{customerId}"

# 2. 检查活跃配置
curl -X GET "http://localhost:8081/api/v1/weights/attack-source/{customerId}/active"

# 3. 查看服务日志
docker-compose logs threat-assessment
```

### 3. 性能问题

**症状**: API响应缓慢

**优化措施**:
```bash
# 1. 检查缓存状态
curl -X GET "http://localhost:8081/actuator/metrics/cache.*"

# 2. 监控数据库查询
# 在应用日志中查看慢查询

# 3. 调整连接池
# application.yml 中增加连接池配置
```

---

## 相关文档

- **[威胁评估系统概述](../threat_assessment_overview.md)** - 了解整体架构
- **[客户端口权重API](../customer_port_weights_api.md)** - 端口权重配置
- **[蜜罐评分算法](../../design/honeypot_based_threat_scoring.md)** - 评分算法详解
- **[数据结构](../../design/data_structures.md)** - 数据模型定义

---

**文档版本**: 5.0  
**最后更新**: 2025-10-31  
**维护者**: ThreatDetection Team</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/docs/api/weight_management_api.md