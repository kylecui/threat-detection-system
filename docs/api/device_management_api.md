# 设备管理API文档

**服务名称**: Threat Assessment Service - Device Management  
**服务端口**: 8083  
**基础路径**: `/api/v1/devices`  
**版本**: 1.0  
**更新日期**: 2025-10-31

---

## 目录

1. [系统概述](#系统概述)
2. [核心功能](#核心功能)
3. [数据模型](#数据模型)
4. [API端点列表](#api端点列表)
5. [API详细文档](#api详细文档)
   - [5.1 绑定设备到客户](#51-绑定设备到客户)
   - [5.2 解绑设备](#52-解绑设备)
   - [5.3 查询设备客户映射](#53-查询设备客户映射)
   - [5.4 查询设备映射历史](#54-查询设备映射历史)
   - [5.5 查询活跃映射](#55-查询活跃映射)
   - [5.6 转移设备](#56-转移设备)
6. [使用场景](#使用场景)
7. [Java客户端完整示例](#java客户端完整示例)
8. [最佳实践](#最佳实践)
9. [故障排查](#故障排查)
10. [相关文档](#相关文档)

---

## 系统概述

### 核心职责

设备管理服务负责管理设备与客户的时效性映射关系，支持设备的动态绑定、解绑和转移操作，为威胁检测系统提供准确的设备归属信息。

```
设备注册 → 客户绑定 → 时效性映射 → 威胁检测
     ↓         ↓         ↓         ↓
  序列号生成  多租户隔离  时间点查询  客户上下文
```

### 核心特性

✅ **时效性映射** - 支持设备在不同时间段属于不同客户  
✅ **多租户隔离** - 设备映射严格按客户隔离  
✅ **审计追踪** - 完整的设备操作历史记录  
✅ **动态转移** - 支持设备在客户间的无缝转移  
✅ **时间点查询** - 可查询设备在任意时间点的归属关系  

### 工作流程

```
设备上线 → 绑定到客户 → 定期同步 → 威胁检测
     ↓         ↓         ↓         ↓
  自动注册  手动/自动绑定  状态更新  客户上下文
```

---

## 核心功能

### 1. 设备绑定管理

- **动态绑定**: 支持设备到客户的实时绑定
- **时效性支持**: 记录绑定时间，支持历史追溯
- **原因记录**: 支持绑定原因的详细记录
- **状态管理**: 维护设备的当前绑定状态

### 2. 设备转移机制

- **无缝转移**: 支持设备在客户间的平滑转移
- **历史连续**: 保持设备映射历史的连续性
- **原子操作**: 确保转移操作的原子性和一致性
- **审计记录**: 完整的转移操作审计日志

### 3. 历史查询功能

- **时间点查询**: 查询设备在指定时间点的客户归属
- **历史追溯**: 查看设备的完整映射历史
- **状态分析**: 分析设备的使用模式和转移频率
- **合规审计**: 支持安全审计和合规检查

### 4. 多租户隔离

- **客户隔离**: 设备映射按客户严格隔离
- **权限控制**: 只有授权客户能管理其设备
- **数据安全**: 防止跨客户的数据泄露
- **访问控制**: 基于角色的访问控制机制

---

## 数据模型

### DeviceCustomerMapping 实体

```json
{
  "id": 1,
  "deviceSerial": "DEV-001",
  "customerId": "customer-001",
  "bindTime": "2025-10-31T10:00:00Z",
  "unbindTime": null,
  "bindReason": "Initial deployment",
  "unbindReason": null,
  "createdAt": "2025-10-31T10:00:00Z",
  "updatedAt": "2025-10-31T10:00:00Z"
}
```

### 字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `id` | Long | 自动 | 主键ID |
| `deviceSerial` | String | 是 | 设备序列号，唯一标识 |
| `customerId` | String | 是 | 客户ID，多租户隔离 |
| `bindTime` | Instant | 是 | 绑定时间 |
| `unbindTime` | Instant | 否 | 解绑时间，为null表示当前活跃 |
| `bindReason` | String | 否 | 绑定原因描述 |
| `unbindReason` | String | 否 | 解绑原因描述 |
| `createdAt` | Instant | 自动 | 记录创建时间 |
| `updatedAt` | Instant | 自动 | 记录更新时间 |

---

## API端点列表

| 方法 | 端点 | 说明 | 响应码 |
|------|------|------|--------|
| POST | `/bind` | 绑定设备到客户 | 200 |
| POST | `/unbind` | 解绑设备 | 200 |
| GET | `/customer` | 查询设备客户映射 | 200 |
| GET | `/history/{deviceSerial}` | 查询设备映射历史 | 200 |
| GET | `/active` | 查询活跃映射 | 200 |
| POST | `/transfer` | 转移设备到新客户 | 200 |

---

## API详细文档

### 5.1 绑定设备到客户

**端点**: `POST /api/v1/devices/bind`

**说明**: 将设备绑定到指定客户，支持时效性映射

**参数**:
- `deviceSerial` (查询参数): 设备序列号
- `customerId` (查询参数): 客户ID
- `bindReason` (查询参数, 可选): 绑定原因
- `bindTime` (查询参数, 可选): 绑定时间，ISO格式，默认当前时间

**响应**:
```json
{
  "id": 1,
  "deviceSerial": "DEV-001",
  "customerId": "customer-001",
  "bindTime": "2025-10-31T10:00:00Z",
  "unbindTime": null,
  "bindReason": "Initial deployment",
  "unbindReason": null,
  "createdAt": "2025-10-31T10:00:00Z",
  "updatedAt": "2025-10-31T10:00:00Z"
}
```

**curl示例**:
```bash
curl -X POST "http://localhost:8083/api/v1/devices/bind?deviceSerial=DEV-001&customerId=customer-001&bindReason=Initial%20deployment"
```

**Java示例**:
```java
// 使用RestTemplate
String url = "http://localhost:8083/api/v1/devices/bind";
UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
    .queryParam("deviceSerial", "DEV-001")
    .queryParam("customerId", "customer-001")
    .queryParam("bindReason", "Initial deployment");

DeviceCustomerMapping result = restTemplate.postForObject(builder.toUriString(), null, DeviceCustomerMapping.class);
```

---

### 5.2 解绑设备

**端点**: `POST /api/v1/devices/unbind`

**说明**: 从当前客户解绑设备

**参数**:
- `deviceSerial` (查询参数): 设备序列号
- `unbindReason` (查询参数, 可选): 解绑原因
- `unbindTime` (查询参数, 可选): 解绑时间，ISO格式，默认当前时间

**响应**: 更新后的映射记录

**curl示例**:
```bash
curl -X POST "http://localhost:8083/api/v1/devices/unbind?deviceSerial=DEV-001&unbindReason=Device%20retirement"
```

---

### 5.3 查询设备客户映射

**端点**: `GET /api/v1/devices/customer`

**说明**: 查询设备在指定时间点属于哪个客户

**参数**:
- `deviceSerial` (查询参数): 设备序列号
- `timestamp` (查询参数, 可选): 查询时间点，ISO格式，默认当前时间

**响应**:
```json
{
  "deviceSerial": "DEV-001",
  "customerId": "customer-001",
  "timestamp": "2025-10-31T10:00:00Z",
  "found": true
}
```

**curl示例**:
```bash
curl -X GET "http://localhost:8083/api/v1/devices/customer?deviceSerial=DEV-001"
```

**Java示例**:
```java
String url = "http://localhost:8083/api/v1/devices/customer";
UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
    .queryParam("deviceSerial", "DEV-001");

Map<String, Object> result = restTemplate.getForObject(builder.toUriString(), Map.class);
String customerId = (String) result.get("customerId");
```

---

### 5.4 查询设备映射历史

**端点**: `GET /api/v1/devices/history/{deviceSerial}`

**说明**: 查询设备的完整客户映射历史

**参数**:
- `deviceSerial` (路径参数): 设备序列号

**响应**: 映射历史记录列表，按时间倒序排列

**curl示例**:
```bash
curl -X GET "http://localhost:8083/api/v1/devices/history/DEV-001"
```

**Java示例**:
```java
String url = "http://localhost:8083/api/v1/devices/history/{deviceSerial}";
List<DeviceCustomerMapping> history = restTemplate.getForObject(url, List.class, "DEV-001");
```

---

### 5.5 查询活跃映射

**端点**: `GET /api/v1/devices/active`

**说明**: 查询所有当前活跃的设备客户映射

**参数**: 无

**响应**: 当前活跃的映射记录列表

**curl示例**:
```bash
curl -X GET "http://localhost:8083/api/v1/devices/active"
```

---

### 5.6 转移设备

**端点**: `POST /api/v1/devices/transfer`

**说明**: 将设备从当前客户转移到新客户

**参数**:
- `deviceSerial` (查询参数): 设备序列号
- `newCustomerId` (查询参数): 新客户ID
- `transferReason` (查询参数, 可选): 转移原因
- `transferTime` (查询参数, 可选): 转移时间，ISO格式，默认当前时间

**响应**:
```json
{
  "deviceSerial": "DEV-001",
  "newCustomerId": "customer-002",
  "transferTime": "2025-10-31T10:00:00Z",
  "previousMapping": {
    "id": 1,
    "deviceSerial": "DEV-001",
    "customerId": "customer-001",
    "bindTime": "2025-10-01T10:00:00Z",
    "unbindTime": "2025-10-31T10:00:00Z",
    "bindReason": "Initial deployment",
    "unbindReason": "Transfer to new customer",
    "createdAt": "2025-10-01T10:00:00Z",
    "updatedAt": "2025-10-31T10:00:00Z"
  }
}
```

**curl示例**:
```bash
curl -X POST "http://localhost:8083/api/v1/devices/transfer?deviceSerial=DEV-001&newCustomerId=customer-002&transferReason=Customer%20migration"
```

---

## 使用场景

### 场景1: 新设备部署

**目标**: 将新部署的设备绑定到客户

```bash
# 1. 绑定设备到客户
curl -X POST "http://localhost:8083/api/v1/devices/bind?deviceSerial=DEV-001&customerId=customer-001&bindReason=New%20deployment"

# 2. 验证绑定结果
curl -X GET "http://localhost:8083/api/v1/devices/customer?deviceSerial=DEV-001"

# 3. 查看设备历史
curl -X GET "http://localhost:8083/api/v1/devices/history/DEV-001"
```

### 场景2: 设备转移

**目标**: 将设备从一个客户转移到另一个客户

```bash
# 1. 执行转移操作
curl -X POST "http://localhost:8083/api/v1/devices/transfer?deviceSerial=DEV-001&newCustomerId=customer-002&transferReason=Contract%20transfer"

# 2. 验证新绑定
curl -X GET "http://localhost:8083/api/v1/devices/customer?deviceSerial=DEV-001"

# 3. 检查历史记录
curl -X GET "http://localhost:8083/api/v1/devices/history/DEV-001"
```

### 场景3: 历史审计

**目标**: 审计设备的历史归属关系

```bash
# 1. 查看设备完整历史
curl -X GET "http://localhost:8083/api/v1/devices/history/DEV-001"

# 2. 查询特定时间点的归属
curl -X GET "http://localhost:8083/api/v1/devices/customer?deviceSerial=DEV-001&timestamp=2025-06-01T00:00:00Z"

# 3. 查看所有活跃映射
curl -X GET "http://localhost:8083/api/v1/devices/active"
```

### 场景4: 设备退役

**目标**: 退役设备并记录原因

```bash
# 1. 解绑设备
curl -X POST "http://localhost:8083/api/v1/devices/unbind?deviceSerial=DEV-001&unbindReason=Device%20retirement"

# 2. 确认解绑
curl -X GET "http://localhost:8083/api/v1/devices/customer?deviceSerial=DEV-001"

# 3. 查看最终历史
curl -X GET "http://localhost:8083/api/v1/devices/history/DEV-001"
```

---

## Java客户端完整示例

```java
package com.threatdetection.client;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 设备管理服务完整客户端
 */
public class DeviceManagementClient {
    
    private final String baseUrl;
    private final RestTemplate restTemplate;
    
    public DeviceManagementClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * 绑定设备到客户
     */
    public DeviceCustomerMapping bindDevice(String deviceSerial, String customerId, 
            String bindReason, Instant bindTime) {
        
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/v1/devices/bind")
            .queryParam("deviceSerial", deviceSerial)
            .queryParam("customerId", customerId)
            .queryParam("bindReason", bindReason)
            .queryParam("bindTime", bindTime)
            .build()
            .toString();
        
        ResponseEntity<DeviceCustomerMapping> response = restTemplate.postForEntity(
            url, null, DeviceCustomerMapping.class);
        
        return response.getBody();
    }
    
    /**
     * 解绑设备
     */
    public DeviceCustomerMapping unbindDevice(String deviceSerial, String unbindReason, Instant unbindTime) {
        
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/v1/devices/unbind")
            .queryParam("deviceSerial", deviceSerial)
            .queryParam("unbindReason", unbindReason)
            .queryParam("unbindTime", unbindTime)
            .build()
            .toString();
        
        ResponseEntity<DeviceCustomerMapping> response = restTemplate.postForEntity(
            url, null, DeviceCustomerMapping.class);
        
        return response.getBody();
    }
    
    /**
     * 查询设备客户映射
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDeviceCustomer(String deviceSerial, Instant timestamp) {
        
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/v1/devices/customer")
            .queryParam("deviceSerial", deviceSerial)
            .queryParam("timestamp", timestamp)
            .build()
            .toString();
        
        return restTemplate.getForObject(url, Map.class);
    }
    
    /**
     * 查询设备映射历史
     */
    @SuppressWarnings("unchecked")
    public List<DeviceCustomerMapping> getDeviceHistory(String deviceSerial) {
        
        String url = baseUrl + "/api/v1/devices/history/" + deviceSerial;
        
        return restTemplate.getForObject(url, List.class);
    }
    
    /**
     * 查询活跃映射
     */
    @SuppressWarnings("unchecked")
    public List<DeviceCustomerMapping> getActiveMappings() {
        
        String url = baseUrl + "/api/v1/devices/active";
        
        return restTemplate.getForObject(url, List.class);
    }
    
    /**
     * 转移设备
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> transferDevice(String deviceSerial, String newCustomerId, 
            String transferReason, Instant transferTime) {
        
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/v1/devices/transfer")
            .queryParam("deviceSerial", deviceSerial)
            .queryParam("newCustomerId", newCustomerId)
            .queryParam("transferReason", transferReason)
            .queryParam("transferTime", transferTime)
            .build()
            .toString();
        
        ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
        
        return response.getBody();
    }
}

// 使用示例
public class DeviceManagementExample {
    public static void main(String[] args) {
        DeviceManagementClient client = new DeviceManagementClient("http://localhost:8083");
        
        String deviceSerial = "DEV-001";
        
        // 1. 绑定设备
        DeviceCustomerMapping binding = client.bindDevice(
            deviceSerial, 
            "customer-001", 
            "Initial deployment", 
            Instant.now()
        );
        System.out.println("Device bound: " + binding.getId());
        
        // 2. 查询当前客户
        Map<String, Object> customerInfo = client.getDeviceCustomer(deviceSerial, null);
        System.out.println("Current customer: " + customerInfo.get("customerId"));
        
        // 3. 转移设备
        Map<String, Object> transfer = client.transferDevice(
            deviceSerial,
            "customer-002",
            "Contract transfer",
            Instant.now()
        );
        System.out.println("Device transferred to: " + transfer.get("newCustomerId"));
        
        // 4. 查看历史
        List<DeviceCustomerMapping> history = client.getDeviceHistory(deviceSerial);
        System.out.println("History records: " + history.size());
    }
}
```

---

## 最佳实践

### 1. 绑定管理

**✅ 推荐做法**:
- 为每次绑定操作提供详细的原因说明
- 使用标准的设备序列号格式
- 定期验证设备绑定状态
- 记录操作人员的身份信息

**❌ 避免做法**:
- 不要频繁绑定和解绑同一设备
- 避免使用不规范的设备序列号
- 不要遗漏绑定原因的记录

### 2. 时间管理

**✅ 推荐做法**:
- 使用协调世界时(UTC)记录所有时间戳
- 明确指定时间点查询的时间参数
- 定期同步设备时钟
- 考虑时区转换对时间点查询的影响

**❌ 避免做法**:
- 不要使用本地时间记录时间戳
- 避免模糊的时间点查询
- 不要忽略夏令时对时间的影响

### 3. 转移操作

**✅ 推荐做法**:
- 在业务低峰期执行设备转移
- 提前通知相关客户
- 验证转移后的设备状态
- 保留完整的转移审计记录

**❌ 避免做法**:
- 不要在高峰期执行大量设备转移
- 避免无故的设备转移操作
- 不要跳过转移验证步骤

### 4. 性能优化

**✅ 推荐做法**:
- 使用批量查询获取多个设备状态
- 合理设置历史记录的保留期限
- 定期清理过期的映射记录
- 监控查询性能和响应时间

**❌ 避免做法**:
- 不要在循环中逐个查询设备状态
- 避免查询过长的历史记录
- 不要保留无限期的历史数据

### 5. 安全考虑

**✅ 推荐做法**:
- 验证操作人员的权限
- 记录所有设备操作的审计日志
- 实施最小权限原则
- 定期审查设备映射关系

### 6. 监控告警

**✅ 推荐做法**:
- 监控设备绑定状态的变化
- 告警异常的转移操作
- 跟踪设备使用模式的统计
- 定期生成设备管理报告

---

## 故障排查

### 问题1: 设备绑定失败

**现象**: 绑定操作返回错误

**可能原因**:
1. 设备已被其他客户绑定
2. 客户ID不存在
3. 数据库连接问题

**排查步骤**:
```bash
# 1. 检查设备当前状态
curl -X GET "http://localhost:8083/api/v1/devices/customer?deviceSerial=DEV-001"

# 2. 验证客户ID存在
# 检查客户管理服务

# 3. 查看应用日志
# 检查是否有数据库错误
```

**解决方案**:
- 如果设备已被绑定，先解绑再绑定
- 验证客户ID的正确性
- 检查数据库连接状态

### 问题2: 时间点查询结果不正确

**现象**: 查询特定时间点的客户归属不准确

**可能原因**:
1. 时间戳格式错误
2. 时区转换问题
3. 映射记录的时间范围错误

**排查步骤**:
```bash
# 1. 检查时间戳格式
# 确保使用ISO 8601格式

# 2. 验证时间范围
curl -X GET "http://localhost:8083/api/v1/devices/history/DEV-001"

# 3. 检查时区设置
# 确认服务器和客户端时区一致
```

**解决方案**:
- 使用标准的ISO时间格式
- 明确指定时区信息
- 验证映射记录的时间范围

### 问题3: 转移操作失败

**现象**: 设备转移操作无法完成

**可能原因**:
1. 设备当前未被绑定
2. 新客户ID不存在
3. 并发操作冲突

**排查步骤**:
```bash
# 1. 检查设备当前状态
curl -X GET "http://localhost:8083/api/v1/devices/customer?deviceSerial=DEV-001"

# 2. 验证新客户ID
# 检查客户管理服务

# 3. 查看错误日志
# 检查是否有并发冲突
```

**解决方案**:
- 确保设备处于活跃绑定状态
- 验证新客户ID的正确性
- 在低并发期重试操作

### 问题4: 历史记录不完整

**现象**: 设备历史记录缺失或不完整

**可能原因**:
1. 历史记录被清理
2. 数据库存储问题
3. 操作日志记录失败

**排查步骤**:
```bash
# 1. 检查数据库记录
docker exec -it threat-db psql -U threat_user threat_detection \
  -c "SELECT * FROM device_customer_mappings WHERE device_serial = 'DEV-001' ORDER BY created_at;"

# 2. 查看应用日志
# 检查是否有记录插入错误

# 3. 验证清理策略
# 检查历史记录保留策略
```

**解决方案**:
- 调整历史记录保留期限
- 修复数据库存储问题
- 补充缺失的操作日志

---

## 相关文档

### 系统架构文档
- **[威胁评估API](./threat_assessment_evaluation_api.md)** - 威胁评估相关API
- **[客户管理API](./customer_management_api.md)** - 客户管理相关API
- **[数据结构](../design/data_structures.md)** - DeviceCustomerMapping实体详细说明

### 部署和运维
- **[设备管理实现进度](../progress/DEVICE_MANAGEMENT_IMPLEMENTATION.md)** - 实现详情和部署说明
- **[数据库设计](../docker/16-temporal-device-mapping.sql)** - 数据库表结构和索引

---

**最后更新**: 2025-10-31  
**版本**: 1.0  
**作者**: GitHub Copilot  
**状态**: ✅ 文档完善