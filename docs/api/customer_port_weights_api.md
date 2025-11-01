# 端口权重配置API文档

**服务名称**: Threat Assessment Service - Port Weights  
**服务端口**: 8083  
**基础路径**: `/api/port-weights`  
**版本**: 4.0  
**更新日期**: 2025-10-31

---

## 目录

1. [系统概述](#系统概述)
2. [核心功能](#核心功能)
3. [权重计算策略](#权重计算策略)
4. [数据模型](#数据模型)
5. [API端点列表](#api端点列表)
6. [API详细文档](#api详细文档)
   - [6.1 获取客户的所有端口权重配置](#61-获取客户的所有端口权重配置)
   - [6.2 获取客户的所有配置](#62-获取客户的所有配置)
   - [6.3 获取指定端口的权重](#63-获取指定端口的权重)
   - [6.4 批量获取端口权重](#64-批量获取端口权重)
   - [6.5 创建端口权重配置](#65-创建端口权重配置)
   - [6.6 批量导入端口权重配置](#66-批量导入端口权重配置)
   - [6.7 更新端口权重](#67-更新端口权重)
   - [6.8 删除端口权重配置](#68-删除端口权重配置)
   - [6.9 删除客户的所有配置](#69-删除客户的所有配置)
   - [6.10 启用或禁用端口配置](#610-启用或禁用端口配置)
   - [6.11 获取统计信息](#611-获取统计信息)
   - [6.12 获取高优先级配置](#612-获取高优先级配置)
   - [6.13 获取高权重端口](#613-获取高权重端口)
   - [6.14 按风险等级查询](#614-按风险等级查询)
   - [6.15 检查配置是否存在](#615-检查配置是否存在)
7. [使用场景](#使用场景)
8. [Java客户端完整示例](#java客户端完整示例)
9. [最佳实践](#最佳实践)
10. [故障排查](#故障排查)
11. [相关文档](#相关文档)

---

## 系统概述

### 核心职责

端口权重配置服务负责管理威胁检测系统中端口的风险权重配置，支持多租户隔离和动态配置更新。

```
端口权重配置 → 威胁评分引擎 → 动态权重计算
     ↓              ↓              ↓
  多租户存储    实时查询      评分算法输入
```

### 核心特性

✅ **多租户隔离** - 每个客户可以自定义端口权重配置  
✅ **混合权重策略** - 支持配置权重和多样性权重，取最大值  
✅ **动态配置更新** - 支持运行时权重调整，无需重启服务  
✅ **风险等级分类** - 按端口风险等级进行分组管理  
✅ **批量操作支持** - 支持批量导入、查询和更新  
✅ **审计追踪** - 完整的配置变更历史记录  

### 权重计算策略

**混合权重策略**: `portWeight = max(configWeight, diversityWeight)`

- **配置权重**: 客户自定义的固定权重值 (0.5-10.0)
- **多样性权重**: 基于端口多样性的动态权重 (1.0-2.0)
- **最终权重**: 取两者中的较大值，确保风险不被低估

### 工作流程

```
攻击事件 → 端口分析 → 权重查询 → 威胁评分
     ↓         ↓         ↓         ↓
  端口列表  多样性计算  混合策略  评分算法
```

---

## 核心功能

### 1. 端口权重管理

- **自定义配置**: 为每个客户提供独立的端口权重配置
- **全局默认**: 支持全局默认配置，客户未配置时使用
- **优先级策略**: 客户自定义优先于全局默认

### 2. 权重计算引擎

- **实时查询**: 支持毫秒级端口权重查询
- **批量处理**: 支持批量端口权重查询，提高性能
- **缓存优化**: 内置缓存机制，减少数据库查询

### 3. 风险等级管理

- **等级分类**: LOW/MEDIUM/HIGH/CRITICAL 四级风险分类
- **智能分组**: 自动按风险等级对端口进行分组
- **统计分析**: 提供各等级的端口统计信息

### 4. 配置生命周期

- **创建更新**: 支持配置的创建、更新和删除
- **启用禁用**: 支持配置的动态启用和禁用
- **批量操作**: 支持批量导入和批量删除

---

## 权重计算策略

### 混合权重算法

```java
public double calculatePortWeight(String customerId, int portNumber, int uniquePorts) {
    // 1. 获取配置权重 (客户自定义或全局默认)
    double configWeight = getConfigWeight(customerId, portNumber);
    
    // 2. 计算多样性权重 (基于端口数量)
    double diversityWeight = calculateDiversityWeight(uniquePorts);
    
    // 3. 取最大值作为最终权重
    return Math.max(configWeight, diversityWeight);
}
```

### 多样性权重计算

| 端口数量 | 权重 | 说明 |
|---------|------|------|
| 1 | 1.0 | 单端口攻击 |
| 2-3 | 1.2 | 小范围扫描 |
| 4-5 | 1.4 | 中等扫描 |
| 6-10 | 1.6 | 广泛扫描 |
| 11-20 | 1.8 | 大规模扫描 |
| 20+ | 2.0 | 全端口扫描 |

### 配置权重范围

- **最小值**: 0.5 (低风险端口)
- **最大值**: 10.0 (极高风险端口)
- **默认值**: 1.0 (标准风险)

---

## 数据模型

### CustomerPortWeight 实体

```json
{
  "id": 1,
  "customerId": "customer-001",
  "portNumber": 3389,
  "weight": 8.5,
  "riskLevel": "HIGH",
  "description": "RDP远程桌面服务",
  "enabled": true,
  "createdAt": "2025-10-31T10:00:00Z",
  "updatedAt": "2025-10-31T10:00:00Z",
  "createdBy": "admin",
  "updatedBy": "admin"
}
```

### 字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `id` | Long | 自动 | 主键ID |
| `customerId` | String | 是 | 客户ID，多租户隔离 |
| `portNumber` | Integer | 是 | 端口号 (1-65535) |
| `weight` | Double | 是 | 权重值 (0.5-10.0) |
| `riskLevel` | String | 自动 | 风险等级 (LOW/MEDIUM/HIGH/CRITICAL) |
| `description` | String | 否 | 端口描述 |
| `enabled` | Boolean | 自动 | 是否启用 (默认true) |
| `createdAt` | Instant | 自动 | 创建时间 |
| `updatedAt` | Instant | 自动 | 更新时间 |
| `createdBy` | String | 否 | 创建人 |
| `updatedBy` | String | 否 | 更新人 |

### 风险等级映射

| 权重范围 | 风险等级 | 说明 |
|---------|---------|------|
| 0.5-2.0 | LOW | 低风险端口 |
| 2.1-5.0 | MEDIUM | 中等风险端口 |
| 5.1-8.0 | HIGH | 高风险端口 |
| 8.1-10.0 | CRITICAL | 严重风险端口 |

---

## API端点列表

| 方法 | 端点 | 说明 | 响应码 |
|------|------|------|--------|
| GET | `/{customerId}` | 获取客户的所有启用配置 | 200 |
| GET | `/{customerId}/all` | 获取客户的所有配置 | 200 |
| GET | `/{customerId}/port/{portNumber}` | 获取指定端口权重 | 200 |
| POST | `/{customerId}/batch` | 批量获取端口权重 | 200 |
| POST | `/{customerId}` | 创建端口权重配置 | 201 |
| POST | `/{customerId}/import` | 批量导入配置 | 201 |
| PUT | `/{customerId}/port/{portNumber}` | 更新端口权重 | 200 |
| DELETE | `/{customerId}/port/{portNumber}` | 删除端口配置 | 204 |
| DELETE | `/{customerId}` | 删除客户所有配置 | 204 |
| PATCH | `/{customerId}/port/{portNumber}/enabled` | 启用/禁用配置 | 200 |
| GET | `/{customerId}/statistics` | 获取统计信息 | 200 |
| GET | `/{customerId}/high-priority` | 获取高优先级配置 | 200 |
| GET | `/{customerId}/high-weight` | 获取高权重端口 | 200 |
| GET | `/{customerId}/risk-level/{riskLevel}` | 按风险等级查询 | 200 |
| GET | `/{customerId}/port/{portNumber}/exists` | 检查配置是否存在 | 200 |

---

## API详细文档

### 6.1 获取客户的所有端口权重配置

**端点**: `GET /api/port-weights/{customerId}`

**说明**: 返回指定客户的所有启用的端口权重配置

**参数**:
- `customerId` (路径参数): 客户ID

**响应**:
```json
[
  {
    "id": 1,
    "customerId": "customer-001",
    "portNumber": 3389,
    "weight": 8.5,
    "riskLevel": "HIGH",
    "description": "RDP远程桌面服务",
    "enabled": true,
    "createdAt": "2025-10-31T10:00:00Z",
    "updatedAt": "2025-10-31T10:00:00Z"
  }
]
```

**curl示例**:
```bash
curl -X GET "http://localhost:8083/api/port-weights/customer-001" \
  -H "Accept: application/json"
```

**Java示例**:
```java
List<CustomerPortWeight> configs = restTemplate.getForObject(
    "http://localhost:8083/api/port-weights/{customerId}",
    List.class,
    customerId
);
```

---

### 6.2 获取客户的所有配置

**端点**: `GET /api/port-weights/{customerId}/all`

**说明**: 返回指定客户的所有端口权重配置，包括禁用的配置

**参数**:
- `customerId` (路径参数): 客户ID

**响应**: 同上，但包含所有配置

**curl示例**:
```bash
curl -X GET "http://localhost:8083/api/port-weights/customer-001/all"
```

---

### 6.3 获取指定端口的权重

**端点**: `GET /api/port-weights/{customerId}/port/{portNumber}`

**说明**: 返回指定端口的权重值，优先级：客户自定义 > 全局默认 > 默认值

**参数**:
- `customerId` (路径参数): 客户ID
- `portNumber` (路径参数): 端口号 (1-65535)

**响应**:
```json
{
  "customerId": "customer-001",
  "portNumber": 3389,
  "weight": 8.5
}
```

**curl示例**:
```bash
curl -X GET "http://localhost:8083/api/port-weights/customer-001/port/3389"
```

**Java示例**:
```java
Map<String, Object> response = restTemplate.getForObject(
    "http://localhost:8083/api/port-weights/{customerId}/port/{portNumber}",
    Map.class,
    customerId, portNumber
);
double weight = (Double) response.get("weight");
```

---

### 6.4 批量获取端口权重

**端点**: `POST /api/port-weights/{customerId}/batch`

**说明**: 批量查询多个端口的权重值，提高查询性能

**参数**:
- `customerId` (路径参数): 客户ID
- 请求体: 端口号列表 `[22, 80, 443, 3389]`

**响应**:
```json
{
  "22": 1.0,
  "80": 1.2,
  "443": 1.5,
  "3389": 8.5
}
```

**curl示例**:
```bash
curl -X POST "http://localhost:8083/api/port-weights/customer-001/batch" \
  -H "Content-Type: application/json" \
  -d '[22, 80, 443, 3389]'
```

**Java示例**:
```java
List<Integer> ports = Arrays.asList(22, 80, 443, 3389);
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);

HttpEntity<List<Integer>> request = new HttpEntity<>(ports, headers);
ResponseEntity<Map> response = restTemplate.postForEntity(
    "http://localhost:8083/api/port-weights/{customerId}/batch",
    request,
    Map.class,
    customerId
);
```

---

### 6.5 创建端口权重配置

**端点**: `POST /api/port-weights/{customerId}`

**说明**: 为指定客户创建新的端口权重配置

**参数**:
- `customerId` (路径参数): 客户ID
- 请求体: 端口权重配置

**请求体**:
```json
{
  "portNumber": 3389,
  "weight": 8.5,
  "description": "RDP远程桌面服务",
  "createdBy": "admin"
}
```

**响应**: 创建的配置对象 (HTTP 201)

**curl示例**:
```bash
curl -X POST "http://localhost:8083/api/port-weights/customer-001" \
  -H "Content-Type: application/json" \
  -d '{
    "portNumber": 3389,
    "weight": 8.5,
    "description": "RDP远程桌面服务",
    "createdBy": "admin"
  }'
```

**Java示例**:
```java
CustomerPortWeight config = CustomerPortWeight.builder()
    .portNumber(3389)
    .weight(8.5)
    .description("RDP远程桌面服务")
    .createdBy("admin")
    .build();

CustomerPortWeight saved = restTemplate.postForObject(
    "http://localhost:8083/api/port-weights/{customerId}",
    config,
    CustomerPortWeight.class,
    customerId
);
```

---

### 6.6 批量导入端口权重配置

**端点**: `POST /api/port-weights/{customerId}/import`

**说明**: 批量导入多个端口权重配置

**参数**:
- `customerId` (路径参数): 客户ID
- 请求体: 配置列表

**请求体**:
```json
[
  {
    "portNumber": 22,
    "weight": 2.5,
    "description": "SSH服务"
  },
  {
    "portNumber": 3389,
    "weight": 8.5,
    "description": "RDP服务"
  }
]
```

**响应**: 创建的配置列表 (HTTP 201)

**curl示例**:
```bash
curl -X POST "http://localhost:8083/api/port-weights/customer-001/import" \
  -H "Content-Type: application/json" \
  -d '[
    {"portNumber": 22, "weight": 2.5, "description": "SSH服务"},
    {"portNumber": 3389, "weight": 8.5, "description": "RDP服务"}
  ]'
```

---

### 6.7 更新端口权重

**端点**: `PUT /api/port-weights/{customerId}/port/{portNumber}`

**说明**: 更新指定端口的权重值

**参数**:
- `customerId` (路径参数): 客户ID
- `portNumber` (路径参数): 端口号
- `weight` (查询参数): 新权重值 (0.5-10.0)
- `updatedBy` (查询参数, 可选): 更新人 (默认"system")

**响应**: 更新后的配置对象

**curl示例**:
```bash
curl -X PUT "http://localhost:8083/api/port-weights/customer-001/port/3389?weight=9.0&updatedBy=admin"
```

**Java示例**:
```java
String url = UriComponentsBuilder.fromHttpUrl("http://localhost:8083/api/port-weights/{customerId}/port/{portNumber}")
    .queryParam("weight", 9.0)
    .queryParam("updatedBy", "admin")
    .build(customerId, portNumber)
    .toString();

CustomerPortWeight updated = restTemplate.exchange(
    url,
    HttpMethod.PUT,
    null,
    CustomerPortWeight.class
).getBody();
```

---

### 6.8 删除端口权重配置

**端点**: `DELETE /api/port-weights/{customerId}/port/{portNumber}`

**说明**: 删除指定端口的权重配置

**参数**:
- `customerId` (路径参数): 客户ID
- `portNumber` (路径参数): 端口号

**响应**: HTTP 204 (无内容)

**curl示例**:
```bash
curl -X DELETE "http://localhost:8083/api/port-weights/customer-001/port/3389"
```

**Java示例**:
```java
restTemplate.delete(
    "http://localhost:8083/api/port-weights/{customerId}/port/{portNumber}",
    customerId, portNumber
);
```

---

### 6.9 删除客户的所有配置

**端点**: `DELETE /api/port-weights/{customerId}`

**说明**: 删除指定客户的所有端口权重配置

**参数**:
- `customerId` (路径参数): 客户ID

**响应**: HTTP 204 (无内容)

**curl示例**:
```bash
curl -X DELETE "http://localhost:8083/api/port-weights/customer-001"
```

---

### 6.10 启用或禁用端口配置

**端点**: `PATCH /api/port-weights/{customerId}/port/{portNumber}/enabled`

**说明**: 修改端口配置的启用状态

**参数**:
- `customerId` (路径参数): 客户ID
- `portNumber` (路径参数): 端口号
- `enabled` (查询参数): 启用状态 (true/false)

**响应**: 更新后的配置对象

**curl示例**:
```bash
curl -X PATCH "http://localhost:8083/api/port-weights/customer-001/port/3389/enabled?enabled=false"
```

---

### 6.11 获取统计信息

**端点**: `GET /api/port-weights/{customerId}/statistics`

**说明**: 获取客户端口权重配置的统计信息

**参数**:
- `customerId` (路径参数): 客户ID

**响应**:
```json
{
  "totalConfigs": 25,
  "enabledConfigs": 22,
  "disabledConfigs": 3,
  "avgWeight": 3.45,
  "maxWeight": 10.0,
  "minWeight": 0.5,
  "riskLevelDistribution": {
    "LOW": 10,
    "MEDIUM": 8,
    "HIGH": 5,
    "CRITICAL": 2
  }
}
```

**curl示例**:
```bash
curl -X GET "http://localhost:8083/api/port-weights/customer-001/statistics"
```

---

### 6.12 获取高优先级配置

**端点**: `GET /api/port-weights/{customerId}/high-priority`

**说明**: 获取优先级高于指定值的端口配置

**参数**:
- `customerId` (路径参数): 客户ID
- `minPriority` (查询参数, 可选): 最小优先级 (默认50, 0-100)

**响应**: 配置列表

**curl示例**:
```bash
curl -X GET "http://localhost:8083/api/port-weights/customer-001/high-priority?minPriority=70"
```

---

### 6.13 获取高权重端口

**端点**: `GET /api/port-weights/{customerId}/high-weight`

**说明**: 获取权重高于指定值的端口配置

**参数**:
- `customerId` (路径参数): 客户ID
- `minWeight` (查询参数, 可选): 最小权重 (默认5.0, 0.5-10.0)

**响应**: 配置列表

**curl示例**:
```bash
curl -X GET "http://localhost:8083/api/port-weights/customer-001/high-weight?minWeight=7.0"
```

---

### 6.14 按风险等级查询

**端点**: `GET /api/port-weights/{customerId}/risk-level/{riskLevel}`

**说明**: 获取指定风险等级的端口配置

**参数**:
- `customerId` (路径参数): 客户ID
- `riskLevel` (路径参数): 风险等级 (LOW/MEDIUM/HIGH/CRITICAL)

**响应**: 配置列表

**curl示例**:
```bash
curl -X GET "http://localhost:8083/api/port-weights/customer-001/risk-level/CRITICAL"
```

---

### 6.15 检查配置是否存在

**端点**: `GET /api/port-weights/{customerId}/port/{portNumber}/exists`

**说明**: 检查指定端口是否已有配置

**参数**:
- `customerId` (路径参数): 客户ID
- `portNumber` (路径参数): 端口号

**响应**:
```json
{
  "customerId": "customer-001",
  "portNumber": 3389,
  "exists": true
}
```

**curl示例**:
```bash
curl -X GET "http://localhost:8083/api/port-weights/customer-001/port/3389/exists"
```

---

## 使用场景

### 场景1: 初始化客户端口权重配置

**目标**: 为新客户设置基础的端口权重配置

```bash
# 1. 批量导入常见高风险端口
curl -X POST "http://localhost:8083/api/port-weights/customer-001/import" \
  -H "Content-Type: application/json" \
  -d '[
    {"portNumber": 22, "weight": 3.0, "description": "SSH服务"},
    {"portNumber": 3389, "weight": 8.5, "description": "RDP远程桌面"},
    {"portNumber": 3306, "weight": 7.0, "description": "MySQL数据库"},
    {"portNumber": 6379, "weight": 6.5, "description": "Redis缓存"}
  ]'

# 2. 验证配置
curl -X GET "http://localhost:8083/api/port-weights/customer-001/statistics"

# 3. 测试权重查询
curl -X POST "http://localhost:8083/api/port-weights/customer-001/batch" \
  -H "Content-Type: application/json" \
  -d '[22, 3389, 3306, 6379]'
```

### 场景2: 威胁评估中的权重查询

**目标**: 在威胁评估过程中查询端口权重

```java
// 1. 批量查询端口权重
List<Integer> attackPorts = Arrays.asList(22, 80, 443, 3389, 3306);
Map<Integer, Double> portWeights = portWeightService.getPortWeightsBatch(
    customerId, attackPorts);

// 2. 计算端口权重因子
double portWeight = calculatePortWeight(attackPorts.size(), portWeights);

// 3. 代入威胁评分公式
double threatScore = (attackCount * uniqueIps * uniquePorts) 
                   * timeWeight * ipWeight * portWeight * deviceWeight;
```

### 场景3: 动态调整高风险端口权重

**目标**: 根据安全事件动态调整端口权重

```bash
# 1. 发现RDP端口被频繁攻击
curl -X PUT "http://localhost:8083/api/port-weights/customer-001/port/3389?weight=9.5&updatedBy=security-admin"

# 2. 临时禁用低风险端口配置
curl -X PATCH "http://localhost:8083/api/port-weights/customer-001/port/21/enabled?enabled=false"

# 3. 查看调整后的统计
curl -X GET "http://localhost:8083/api/port-weights/customer-001/statistics"
```

### 场景4: 定期审查和清理配置

**目标**: 定期审查端口权重配置的有效性

```bash
# 1. 查看所有配置
curl -X GET "http://localhost:8083/api/port-weights/customer-001/all"

# 2. 识别低权重配置
curl -X GET "http://localhost:8083/api/port-weights/customer-001/high-weight?minWeight=1.0"

# 3. 清理不需要的配置
curl -X DELETE "http://localhost:8083/api/port-weights/customer-001/port/21"
```

---

## Java客户端完整示例

```java
package com.threatdetection.client;

import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 端口权重配置服务完整客户端
 */
public class PortWeightConfigClient {
    
    private final String baseUrl;
    private final RestTemplate restTemplate;
    
    public PortWeightConfigClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * 获取客户的所有端口权重配置
     */
    public List<CustomerPortWeight> getCustomerConfigs(String customerId) {
        String url = baseUrl + "/api/port-weights/" + customerId;
        return Arrays.asList(restTemplate.getForObject(url, CustomerPortWeight[].class));
    }
    
    /**
     * 获取指定端口的权重
     */
    public double getPortWeight(String customerId, int portNumber) {
        String url = String.format("%s/api/port-weights/%s/port/%d", 
            baseUrl, customerId, portNumber);
        
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        return ((Number) response.get("weight")).doubleValue();
    }
    
    /**
     * 批量获取端口权重
     */
    public Map<Integer, Double> getPortWeightsBatch(String customerId, List<Integer> ports) {
        String url = baseUrl + "/api/port-weights/" + customerId + "/batch";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<List<Integer>> request = new HttpEntity<>(ports, headers);
        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        return (Map<Integer, Double>) response.getBody();
    }
    
    /**
     * 创建端口权重配置
     */
    public CustomerPortWeight createConfig(String customerId, CustomerPortWeight config) {
        String url = baseUrl + "/api/port-weights/" + customerId;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<CustomerPortWeight> request = new HttpEntity<>(config, headers);
        
        ResponseEntity<CustomerPortWeight> response = restTemplate.postForEntity(
            url, request, CustomerPortWeight.class);
        
        return response.getBody();
    }
    
    /**
     * 批量导入配置
     */
    public List<CustomerPortWeight> batchImport(String customerId, List<CustomerPortWeight> configs) {
        String url = baseUrl + "/api/port-weights/" + customerId + "/import";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<List<CustomerPortWeight>> request = new HttpEntity<>(configs, headers);
        
        ResponseEntity<CustomerPortWeight[]> response = restTemplate.postForEntity(
            url, request, CustomerPortWeight[].class);
        
        return Arrays.asList(response.getBody());
    }
    
    /**
     * 更新端口权重
     */
    public CustomerPortWeight updateWeight(String customerId, int portNumber, 
            double weight, String updatedBy) {
        
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/port-weights/{customerId}/port/{portNumber}")
            .queryParam("weight", weight)
            .queryParam("updatedBy", updatedBy != null ? updatedBy : "system")
            .build(customerId, portNumber)
            .toString();
        
        HttpEntity<?> request = new HttpEntity<>(new HttpHeaders());
        
        ResponseEntity<CustomerPortWeight> response = restTemplate.exchange(
            url, HttpMethod.PUT, request, CustomerPortWeight.class);
        
        return response.getBody();
    }
    
    /**
     * 删除端口配置
     */
    public void deleteConfig(String customerId, int portNumber) {
        String url = String.format("%s/api/port-weights/%s/port/%d", 
            baseUrl, customerId, portNumber);
        
        restTemplate.delete(url);
    }
    
    /**
     * 启用/禁用配置
     */
    public CustomerPortWeight setEnabled(String customerId, int portNumber, boolean enabled) {
        String url = String.format("%s/api/port-weights/%s/port/%d/enabled?enabled=%s", 
            baseUrl, customerId, portNumber, enabled);
        
        HttpEntity<?> request = new HttpEntity<>(new HttpHeaders());
        
        ResponseEntity<CustomerPortWeight> response = restTemplate.exchange(
            url, HttpMethod.PATCH, request, CustomerPortWeight.class);
        
        return response.getBody();
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics(String customerId) {
        String url = baseUrl + "/api/port-weights/" + customerId + "/statistics";
        return restTemplate.getForObject(url, Map.class);
    }
    
    /**
     * 获取高风险端口
     */
    public List<CustomerPortWeight> getHighWeightPorts(String customerId, double minWeight) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/port-weights/{customerId}/high-weight")
            .queryParam("minWeight", minWeight)
            .build(customerId)
            .toString();
        
        return Arrays.asList(restTemplate.getForObject(url, CustomerPortWeight[].class));
    }
    
    /**
     * 按风险等级查询
     */
    public List<CustomerPortWeight> getByRiskLevel(String customerId, String riskLevel) {
        String url = String.format("%s/api/port-weights/%s/risk-level/%s", 
            baseUrl, customerId, riskLevel.toUpperCase());
        
        return Arrays.asList(restTemplate.getForObject(url, CustomerPortWeight[].class));
    }
    
    /**
     * 检查配置是否存在
     */
    public boolean configExists(String customerId, int portNumber) {
        String url = String.format("%s/api/port-weights/%s/port/%d/exists", 
            baseUrl, customerId, portNumber);
        
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        return (Boolean) response.get("exists");
    }
}

// 使用示例
public class PortWeightExample {
    public static void main(String[] args) {
        PortWeightConfigClient client = new PortWeightConfigClient("http://localhost:8083");
        
        String customerId = "customer-001";
        
        // 1. 创建配置
        CustomerPortWeight config = CustomerPortWeight.builder()
            .portNumber(3389)
            .weight(8.5)
            .description("RDP远程桌面服务")
            .createdBy("admin")
            .build();
        
        CustomerPortWeight saved = client.createConfig(customerId, config);
        System.out.println("Created config: " + saved.getId());
        
        // 2. 查询权重
        double weight = client.getPortWeight(customerId, 3389);
        System.out.println("Port 3389 weight: " + weight);
        
        // 3. 批量查询
        List<Integer> ports = Arrays.asList(22, 80, 443, 3389);
        Map<Integer, Double> weights = client.getPortWeightsBatch(customerId, ports);
        System.out.println("Batch weights: " + weights);
        
        // 4. 获取统计
        Map<String, Object> stats = client.getStatistics(customerId);
        System.out.println("Statistics: " + stats);
    }
}
```

---

## 最佳实践

### 1. 配置管理

**✅ 推荐做法**:
- 为每个客户维护独立的端口权重配置
- 定期审查和更新权重值
- 使用描述性字段记录配置原因
- 启用审计功能追踪配置变更

**❌ 避免做法**:
- 不要使用全局默认配置作为长期方案
- 避免频繁的小幅调整权重
- 不要禁用重要端口的配置

### 2. 性能优化

**✅ 推荐做法**:
- 使用批量查询API减少网络往返
- 合理设置缓存过期时间
- 定期清理未使用的配置
- 监控查询性能和响应时间

**❌ 避免做法**:
- 不要在循环中逐个查询端口权重
- 避免查询大量历史数据
- 不要频繁更新权重配置

### 3. 安全考虑

**✅ 推荐做法**:
- 验证端口号范围 (1-65535)
- 限制权重值范围 (0.5-10.0)
- 记录所有配置变更操作
- 实施最小权限原则

**❌ 避免做法**:
- 不要接受无效的端口号或权重值
- 避免明文存储敏感配置信息
- 不要绕过多租户隔离检查

### 4. 监控告警

**✅ 推荐做法**:
- 监控配置变更频率
- 告警异常的权重值
- 跟踪配置使用统计
- 定期备份配置数据

### 5. 批量操作

**✅ 推荐做法**:
- 使用批量导入初始化配置
- 分批处理大量配置更新
- 验证批量操作的结果
- 提供详细的错误报告

---

## 故障排查

### 问题1: 端口权重查询返回默认值

**现象**: 查询端口权重总是返回1.0

**可能原因**:
1. 客户没有配置该端口
2. 全局默认配置不存在
3. 配置被禁用

**排查步骤**:
```bash
# 1. 检查客户配置
curl -X GET "http://localhost:8083/api/port-weights/customer-001/all"

# 2. 检查全局默认配置
curl -X GET "http://localhost:8083/api/port-weights/global/all"

# 3. 检查配置是否存在
curl -X GET "http://localhost:8083/api/port-weights/customer-001/port/3389/exists"
```

**解决方案**:
- 为客户创建端口配置
- 启用已存在的配置
- 检查配置的权重值是否正确

### 问题2: 批量查询性能慢

**现象**: 批量查询响应时间超过1秒

**可能原因**:
1. 查询的端口数量过多
2. 数据库连接池耗尽
3. 缓存未生效

**排查步骤**:
```bash
# 1. 检查查询数量
# 批量查询建议不超过100个端口

# 2. 检查数据库连接
# 查看应用日志中的数据库连接信息

# 3. 检查缓存状态
# 查看应用指标中的缓存命中率
```

**解决方案**:
- 分批查询大量端口
- 增加数据库连接池大小
- 优化缓存配置

### 问题3: 配置更新不生效

**现象**: 更新端口权重后查询仍返回旧值

**可能原因**:
1. 缓存未更新
2. 数据库事务未提交
3. 并发更新冲突

**排查步骤**:
```bash
# 1. 直接查询数据库
docker exec -it threat-db psql -U threat_user threat_detection \
  -c "SELECT * FROM customer_port_weights WHERE customer_id = 'customer-001' AND port_number = 3389;"

# 2. 检查应用日志
# 查看是否有缓存更新日志

# 3. 重启应用服务
# 强制刷新缓存
```

**解决方案**:
- 等待缓存过期
- 重启应用服务
- 检查并发更新逻辑

### 问题4: 批量导入失败

**现象**: 批量导入返回部分失败

**可能原因**:
1. 数据验证失败
2. 数据库约束冲突
3. 权限不足

**排查步骤**:
```bash
# 1. 检查错误响应
# 查看批量导入的详细错误信息

# 2. 验证数据格式
# 确保所有必需字段都存在

# 3. 检查数据库约束
# 查看是否有唯一键冲突
```

**解决方案**:
- 修复数据验证错误
- 分批导入大量数据
- 检查数据库权限

### 问题5: 统计信息不准确

**现象**: 统计API返回的数据与预期不符

**可能原因**:
1. 缓存数据过期
2. 并发更新导致不一致
3. 统计计算逻辑错误

**排查步骤**:
```bash
# 1. 直接查询数据库统计
docker exec -it threat-db psql -U threat_user threat_detection \
  -c "SELECT COUNT(*) as total, COUNT(CASE WHEN enabled THEN 1 END) as enabled FROM customer_port_weights WHERE customer_id = 'customer-001';"

# 2. 比较API响应
curl -X GET "http://localhost:8083/api/port-weights/customer-001/statistics"

# 3. 检查统计计算逻辑
# 查看应用代码中的统计实现
```

**解决方案**:
- 刷新缓存
- 重启统计服务
- 修复统计计算逻辑

---

## 相关文档

### 系统架构文档
- **[威胁评分算法](../design/honeypot_based_threat_scoring.md)** - 了解端口权重在威胁评分中的作用
- **[数据结构](../design/data_structures.md)** - CustomerPortWeight实体详细说明
- **[数据库设计](../docker/13-customer-port-weights.sql)** - 数据库表结构和索引

### API文档
- **[威胁评估API](./threat_assessment_evaluation_api.md)** - 威胁评估相关API
- **[威胁查询API](./threat_assessment_query_api.md)** - 威胁查询相关API
- **[客户管理API](./customer_management_api.md)** - 客户管理相关API

### 部署和运维
- **[端口权重实现进度](../progress/PORT_WEIGHTS_IMPLEMENTATION_PROGRESS.md)** - 实现详情和部署说明
- **[端口权重部署完成](../progress/PORT_WEIGHTS_DEPLOYMENT_COMPLETE.md)** - 部署验证和测试结果

---

**最后更新**: 2025-10-31  
**版本**: 4.0  
**作者**: GitHub Copilot  
**状态**: ✅ 文档完善