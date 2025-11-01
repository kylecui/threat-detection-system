# 数据摄取 API 文档

**服务名称**: Data Ingestion Service  
**服务端口**: 8080  
**基础路径**: `http://localhost:8080/api/v1/logs`  
**版本**: 1.0  
**更新日期**: 2025-01-16

---

## 目录

1. [系统概述](#系统概述)
2. [核心功能](#核心功能)
3. [日志格式](#日志格式)
4. [API端点列表](#api端点列表)
5. [API详细文档](#api详细文档)
   - [5.1 单条日志摄取](#51-单条日志摄取)
   - [5.2 批量日志摄取](#52-批量日志摄取)
   - [5.3 获取解析统计](#53-获取解析统计)
   - [5.4 重置统计数据](#54-重置统计数据)
   - [5.5 健康检查](#55-健康检查)
   - [5.6 设备客户映射](#56-设备客户映射)
6. [请求/响应模型](#请求响应模型)
7. [使用场景](#使用场景)
8. [Java客户端示例](#java客户端示例)
9. [最佳实践](#最佳实践)
10. [故障排查](#故障排查)

---

## 系统概述

### 架构定位

**数据摄取服务**是威胁检测系统的入口组件,负责:

```
Syslog源设备 → rsyslog → Data Ingestion Service → Kafka (attack-events/status-events)
                                ↓
                        PostgreSQL (客户映射/统计)
```

### 核心职责

1. **日志接收**: 接收来自rsyslog的原始syslog消息
2. **格式解析**: 解析蜜罐设备的攻击日志和心跳日志
3. **数据验证**: 验证必需字段,过滤无效日志
4. **租户路由**: 根据设备序列号解析customerId
5. **事件发布**: 将解析后的事件发布到Kafka主题
6. **指标统计**: 记录解析成功率、失败率、处理耗时

### 技术栈

- **框架**: Spring Boot 3.1.5
- **消息队列**: Apache Kafka 3.4+
- **数据库**: PostgreSQL 15+ (用于设备映射和统计)
- **序列化**: JSON (Jackson)

---

## 核心功能

### 1. 双类型日志支持

| 日志类型 | log_type | 用途 | Kafka主题 |
|---------|---------|------|-----------|
| **攻击日志** | 1 | 蜜罐诱捕的内网失陷主机访问记录 | attack-events |
| **心跳日志** | 2 | 蜜罐设备健康状态和哨兵统计 | status-events |

### 2. 蜜罐机制日志字段

**攻击日志关键字段**:
- `attack_mac`: 被诱捕的内网失陷主机MAC
- `attack_ip`: 被诱捕的内网失陷主机IP
- `response_ip`: 诱饵IP (不存在的虚拟哨兵)
- `response_port`: 攻击者尝试的端口 (暴露攻击意图)

**心跳日志关键字段**:
- `sentry_count`: 当前活跃的诱饵IP数量
- `real_host_count`: 检测到的真实主机数量
- `dev_start_time`: 设备启动时间戳
- `dev_end_time`: 设备运行截止时间

### 3. 批量处理能力

- 支持单次提交 **1-1000条** 日志
- 自动拆分解析,并行发布到Kafka
- 返回详细的成功/失败统计

### 4. 实时统计监控

- 总解析数量、成功数量、失败数量
- 解析成功率计算
- 可重置统计数据

---

## 日志格式

### 攻击日志格式 (log_type=1)

**完整示例**:
```
syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188,response_ip=192.168.75.67,response_port=65536,line_id=1,Iface_type=1,Vlan_id=0,log_time=1747274685
```

**字段说明**:

| 字段 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `syslog_version` | String | ✅ | 日志协议版本 (如 1.10.0) |
| `dev_serial` | String | ✅ | 设备序列号,用于解析customerId |
| `log_type` | Integer | ✅ | 日志类型: 1=攻击日志, 2=心跳日志 |
| `sub_type` | Integer | ❌ | 攻击子类型 |
| `attack_mac` | String | ✅ | 被诱捕者MAC地址 |
| `attack_ip` | String | ✅ | 被诱捕者IP地址 |
| `response_ip` | String | ✅ | 诱饵IP (虚拟哨兵) |
| `response_port` | Integer | ✅ | 攻击者尝试的端口 |
| `line_id` | Integer | ❌ | 线路ID |
| `Iface_type` | Integer | ❌ | 接口类型 |
| `Vlan_id` | Integer | ❌ | VLAN ID |
| `log_time` | Long | ✅ | Unix时间戳 (秒) |

**蜜罐机制理解**:
```
- response_ip = 192.168.75.67 是诱饵IP (网络中不存在的虚拟哨兵)
- attack_ip = 192.168.75.188 是访问诱饵的内网主机 (已被攻陷)
- response_port = 65536 说明攻击者尝试访问高危端口
- 所有访问诱饵IP的行为都是恶意的 (误报率极低)
```

### 心跳日志格式 (log_type=2)

**完整示例**:
```
syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2,sentry_count=5691,real_host_count=677,dev_start_time=1747274655,dev_end_time=1778688000,time=2025-05-15 10:04:45
```

**字段说明**:

| 字段 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `syslog_version` | String | ✅ | 日志协议版本 |
| `dev_serial` | String | ✅ | 设备序列号 |
| `log_type` | Integer | ✅ | 日志类型: 2=心跳日志 |
| `sentry_count` | Integer | ✅ | 当前活跃诱饵IP数量 |
| `real_host_count` | Integer | ✅ | 检测到的真实主机数量 |
| `dev_start_time` | Long | ✅ | 设备启动时间戳 |
| `dev_end_time` | Long | ✅ | 设备运行截止时间 |
| `time` | String | ✅ | 心跳时间 (格式: yyyy-MM-dd HH:mm:ss) |

---

## API端点列表

| 方法 | 端点 | 功能 | 说明 |
|------|------|------|------|
| `POST` | `/api/v1/logs/ingest` | 单条日志摄取 | 提交单条syslog消息 |
| `POST` | `/api/v1/logs/batch` | 批量日志摄取 | 提交1-1000条日志 |
| `GET` | `/api/v1/logs/stats` | 获取解析统计 | 查看处理成功率 |
| `POST` | `/api/v1/logs/stats/reset` | 重置统计数据 | 清零统计计数器 |
| `GET` | `/api/v1/logs/health` | 健康检查 | 服务状态检查 |
| `GET` | `/api/v1/logs/customer-mapping/{devSerial}` | 设备客户映射 | 根据设备序列号查询customerId |
| `POST` | `/api/v1/import/scenario` | 场景感知导入 | 通用场景日志导入 (迁移/补全/离线) |
| `POST` | `/api/v1/import/migration` | 系统迁移导入 | 批量导入历史迁移日志 |
| `POST` | `/api/v1/import/completion/{customerId}` | 客户补全导入 | 为特定客户补全缺失数据 |
| `POST` | `/api/v1/import/offline` | 离线研究导入 | 导入离线安全研究日志 |
| `GET` | `/api/v1/import/modes` | 获取导入模式 | 获取支持的导入模式列表 |

---

## API详细文档

### 5.1 单条日志摄取

**描述**: 提交单条syslog消息进行解析和发布。适用于实时流式日志处理。

**端点**: `POST /api/v1/logs/ingest`

**请求体**: `Content-Type: text/plain`

**请求示例 (curl)**:

```bash
# 提交攻击日志
curl -X POST http://localhost:8080/api/v1/logs/ingest \
  -H "Content-Type: text/plain" \
  -d "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188,response_ip=192.168.75.67,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=1747274685"

# 提交心跳日志
curl -X POST http://localhost:8080/api/v1/logs/ingest \
  -H "Content-Type: text/plain" \
  -d "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2,sentry_count=5691,real_host_count=677,dev_start_time=1747274655,dev_end_time=1778688000,time=2025-05-15 10:04:45"
```

**请求示例 (Java)**:

```java
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class LogIngestionExample {
    
    private static final String BASE_URL = "http://localhost:8080/api/v1/logs";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 提交单条攻击日志
     */
    public void ingestAttackLog() {
        String attackLog = "syslog_version=1.10.0,dev_serial=GSFB2204200410007425," +
                          "log_type=1,sub_type=1,attack_mac=04:42:1a:8e:e3:65," +
                          "attack_ip=192.168.75.188,response_ip=192.168.75.67," +
                          "response_port=3389,line_id=1,Iface_type=1,Vlan_id=0," +
                          "log_time=1747274685";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        
        HttpEntity<String> request = new HttpEntity<>(attackLog, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(
            BASE_URL + "/ingest", 
            request, 
            String.class
        );
        
        System.out.println("Status: " + response.getStatusCode());
        System.out.println("Response: " + response.getBody());
    }
    
    /**
     * 提交单条心跳日志
     */
    public void ingestHeartbeatLog() {
        String heartbeatLog = "syslog_version=1.10.0,dev_serial=GSFB2204200410007425," +
                             "log_type=2,sentry_count=5691,real_host_count=677," +
                             "dev_start_time=1747274655,dev_end_time=1778688000," +
                             "time=2025-05-15 10:04:45";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        
        HttpEntity<String> request = new HttpEntity<>(heartbeatLog, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(
            BASE_URL + "/ingest", 
            request, 
            String.class
        );
        
        System.out.println("Heartbeat ingested: " + response.getStatusCode());
    }
}
```

**响应示例 (成功)**:

```
HTTP/1.1 200 OK
Content-Type: text/plain

Log ingested successfully
```

**响应示例 (失败)**:

```
HTTP/1.1 400 Bad Request
Content-Type: text/plain

Invalid log format: missing required field 'attack_mac'
```

**错误码**:

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 日志解析并发布成功 |
| 400 | 日志格式错误或缺少必需字段 |
| 500 | 服务器内部错误 (Kafka发布失败等) |

---

### 5.2 批量日志摄取

**描述**: 批量提交1-1000条日志,提高吞吐量。适用于日志文件批量导入或高频率场景。

**端点**: `POST /api/v1/logs/batch`

**请求体**: `Content-Type: application/json`

**请求参数**:

| 字段 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `logs` | String[] | ✅ | 日志数组,每个元素为完整syslog字符串 |

**请求示例 (curl)**:

```bash
curl -X POST http://localhost:8080/api/v1/logs/batch \
  -H "Content-Type: application/json" \
  -d '{
    "logs": [
      "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188,response_ip=192.168.75.67,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=1747274685",
      "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188,response_ip=192.168.75.80,response_port=445,line_id=2,Iface_type=1,Vlan_id=0,log_time=1747274690",
      "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2,sentry_count=5691,real_host_count=677,dev_start_time=1747274655,dev_end_time=1778688000,time=2025-05-15 10:04:45"
    ]
  }'
```

**请求示例 (Java)**:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

public class BatchIngestionExample {
    
    private static final String BASE_URL = "http://localhost:8080/api/v1/logs";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 批量提交混合日志 (攻击日志 + 心跳日志)
     */
    public void ingestBatchLogs() throws Exception {
        // 构建批量请求
        BatchLogRequest request = new BatchLogRequest();
        request.setLogs(Arrays.asList(
            // 攻击日志1: 尝试远程桌面 (3389)
            "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1," +
            "sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188," +
            "response_ip=192.168.75.67,response_port=3389,line_id=1,Iface_type=1," +
            "Vlan_id=0,log_time=1747274685",
            
            // 攻击日志2: 尝试SMB横向移动 (445)
            "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1," +
            "sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188," +
            "response_ip=192.168.75.80,response_port=445,line_id=2,Iface_type=1," +
            "Vlan_id=0,log_time=1747274690",
            
            // 攻击日志3: 尝试SSH暴力破解 (22)
            "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1," +
            "sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188," +
            "response_ip=192.168.75.100,response_port=22,line_id=3,Iface_type=1," +
            "Vlan_id=0,log_time=1747274695",
            
            // 心跳日志
            "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2," +
            "sentry_count=5691,real_host_count=677,dev_start_time=1747274655," +
            "dev_end_time=1778688000,time=2025-05-15 10:04:45"
        ));
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<BatchLogRequest> httpRequest = new HttpEntity<>(request, headers);
        
        ResponseEntity<BatchLogResponse> response = restTemplate.postForEntity(
            BASE_URL + "/batch",
            httpRequest,
            BatchLogResponse.class
        );
        
        // 打印结果
        BatchLogResponse result = response.getBody();
        System.out.println("Total: " + result.getTotalReceived());
        System.out.println("Success: " + result.getSuccessfullyParsed());
        System.out.println("Failed: " + result.getFailed());
        System.out.println("Published to Kafka: " + result.getPublishedToKafka());
        
        // 检查失败的日志
        if (!result.getFailedLogs().isEmpty()) {
            System.out.println("\nFailed logs:");
            result.getFailedLogs().forEach(failure -> {
                System.out.println("- Log: " + failure.getLog());
                System.out.println("  Error: " + failure.getError());
            });
        }
    }
    
    // DTO类
    public static class BatchLogRequest {
        private List<String> logs;
        
        public List<String> getLogs() { return logs; }
        public void setLogs(List<String> logs) { this.logs = logs; }
    }
    
    public static class BatchLogResponse {
        private int totalReceived;
        private int successfullyParsed;
        private int failed;
        private int publishedToKafka;
        private List<FailedLog> failedLogs;
        
        // Getters
        public int getTotalReceived() { return totalReceived; }
        public int getSuccessfullyParsed() { return successfullyParsed; }
        public int getFailed() { return failed; }
        public int getPublishedToKafka() { return publishedToKafka; }
        public List<FailedLog> getFailedLogs() { return failedLogs; }
    }
    
    public static class FailedLog {
        private String log;
        private String error;
        
        public String getLog() { return log; }
        public String getError() { return error; }
    }
}
```

**响应示例 (成功)**:

```json
{
  "totalReceived": 4,
  "successfullyParsed": 4,
  "failed": 0,
  "publishedToKafka": 4,
  "failedLogs": []
}
```

**响应示例 (部分失败)**:

```json
{
  "totalReceived": 4,
  "successfullyParsed": 3,
  "failed": 1,
  "publishedToKafka": 3,
  "failedLogs": [
    {
      "log": "syslog_version=1.10.0,dev_serial=INVALID",
      "error": "Missing required field: attack_mac"
    }
  ]
}
```

**错误码**:

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 批量处理完成 (可能部分失败,查看响应详情) |
| 400 | 请求格式错误 (如logs数组为空或超过1000条) |
| 500 | 服务器内部错误 |

**性能建议**:

| 批量大小 | 推荐场景 | 预期延迟 |
|---------|---------|---------|
| 1-10 | 实时日志流 | < 100ms |
| 10-100 | 小批量导入 | < 500ms |
| 100-500 | 中批量导入 | < 2s |
| 500-1000 | 大批量导入 | < 5s |

---

### 5.3 获取解析统计

**描述**: 查询服务启动以来的日志解析统计信息,用于监控和性能分析。

**端点**: `GET /api/v1/logs/stats`

**请求示例 (curl)**:

```bash
curl -X GET http://localhost:8080/api/v1/logs/stats
```

**请求示例 (Java)**:

```java
import org.springframework.web.client.RestTemplate;

public class StatsExample {
    
    private static final String BASE_URL = "http://localhost:8080/api/v1/logs";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 获取解析统计信息
     */
    public void getParseStats() {
        ParseStats stats = restTemplate.getForObject(
            BASE_URL + "/stats",
            ParseStats.class
        );
        
        System.out.println("Total parsed: " + stats.getTotalParsed());
        System.out.println("Successful: " + stats.getSuccessfulParsed());
        System.out.println("Failed: " + stats.getFailedParsed());
        System.out.println("Success rate: " + stats.getSuccessRate() + "%");
    }
    
    public static class ParseStats {
        private long totalParsed;
        private long successfulParsed;
        private long failedParsed;
        private double successRate;
        
        // Getters
        public long getTotalParsed() { return totalParsed; }
        public long getSuccessfulParsed() { return successfulParsed; }
        public long getFailedParsed() { return failedParsed; }
        public double getSuccessRate() { return successRate; }
    }
}
```

**响应示例**:

```json
{
  "totalParsed": 15847,
  "successfulParsed": 15720,
  "failedParsed": 127,
  "successRate": 99.2
}
```

**字段说明**:

| 字段 | 类型 | 说明 |
|-----|------|------|
| `totalParsed` | Long | 总解析日志数量 |
| `successfulParsed` | Long | 成功解析数量 |
| `failedParsed` | Long | 失败解析数量 |
| `successRate` | Double | 成功率 (0-100) |

---

### 5.4 重置统计数据

**描述**: 清零解析统计计数器,用于测试或定期重置。

**端点**: `POST /api/v1/logs/stats/reset`

**请求示例 (curl)**:

```bash
curl -X POST http://localhost:8080/api/v1/logs/stats/reset
```

**请求示例 (Java)**:

```java
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class ResetStatsExample {
    
    private static final String BASE_URL = "http://localhost:8080/api/v1/logs";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 重置解析统计
     */
    public void resetStats() {
        ResponseEntity<String> response = restTemplate.postForEntity(
            BASE_URL + "/stats/reset",
            null,
            String.class
        );
        
        System.out.println("Reset result: " + response.getBody());
    }
}
```

**响应示例**:

```
HTTP/1.1 200 OK
Content-Type: text/plain

Statistics reset successfully
```

---

### 5.5 健康检查

**描述**: 检查数据摄取服务的健康状态,用于负载均衡器和监控系统。

**端点**: `GET /api/v1/logs/health`

**请求示例 (curl)**:

```bash
curl -X GET http://localhost:8080/api/v1/logs/health
```

**请求示例 (Java)**:

```java
import org.springframework.web.client.RestTemplate;

public class HealthCheckExample {
    
    private static final String BASE_URL = "http://localhost:8080/api/v1/logs";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 健康检查
     */
    public boolean checkHealth() {
        try {
            HealthStatus status = restTemplate.getForObject(
                BASE_URL + "/health",
                HealthStatus.class
            );
            
            boolean isHealthy = "UP".equals(status.getStatus());
            System.out.println("Service health: " + status.getStatus());
            
            return isHealthy;
        } catch (Exception e) {
            System.err.println("Health check failed: " + e.getMessage());
            return false;
        }
    }
    
    public static class HealthStatus {
        private String status;
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
```

**响应示例 (健康)**:

```json
{
  "status": "UP"
}
```

**响应示例 (不健康)**:

```json
{
  "status": "DOWN",
  "details": {
    "kafka": "Connection timeout",
    "database": "OK"
  }
}
```

---

### 5.6 设备客户映射

**描述**: 根据设备序列号查询对应的客户ID,用于调试和验证租户隔离。

**端点**: `GET /api/v1/logs/customer-mapping/{devSerial}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `devSerial` | String | ✅ | 设备序列号 (如 GSFB2204200410007425) |

**请求示例 (curl)**:

```bash
curl -X GET http://localhost:8080/api/v1/logs/customer-mapping/GSFB2204200410007425
```

**请求示例 (Java)**:

```java
import org.springframework.web.client.RestTemplate;

public class CustomerMappingExample {
    
    private static final String BASE_URL = "http://localhost:8080/api/v1/logs";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 查询设备对应的客户ID
     */
    public String getCustomerIdByDevSerial(String devSerial) {
        String url = BASE_URL + "/customer-mapping/" + devSerial;
        
        CustomerMapping mapping = restTemplate.getForObject(
            url,
            CustomerMapping.class
        );
        
        System.out.println("Device: " + mapping.getDevSerial());
        System.out.println("Customer: " + mapping.getCustomerId());
        
        return mapping.getCustomerId();
    }
    
    public static class CustomerMapping {
        private String devSerial;
        private String customerId;
        
        public String getDevSerial() { return devSerial; }
        public String getCustomerId() { return customerId; }
    }
}
```

**响应示例 (成功)**:

```json
{
  "dev_serial": "GSFB2204200410007425",
  "customer_id": "customer_a"
}
```

**响应示例 (未找到)**:

```json
{
  "dev_serial": "UNKNOWN_DEVICE",
  "customer_id": null,
  "error": "No customer mapping found for device"
}
```

**错误码**:

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 查询成功 (customerId可能为null) |
| 404 | 设备序列号不存在 |

---

## 导入功能API

### 6.1 场景感知导入

**描述**: 通用场景日志导入API，支持系统迁移、客户数据补全和离线安全研究等多种场景。自动根据模式选择合适的处理策略，包括去重、合并和发布逻辑。

**端点**: `POST /api/v1/import/scenario`

**请求体**: `Content-Type: application/json`

**请求参数**:

| 字段 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `mode` | String | ✅ | 导入模式: `migration` (系统迁移), `completion` (客户补全), `offline` (离线研究) |
| `customerId` | String | 条件 | completion模式时必需，指定补全的客户ID |
| `logs` | String[] | ✅ | syslog日志数组，每条为完整syslog字符串 |

**请求示例 (系统迁移)**:

```bash
curl -X POST http://localhost:8080/api/v1/import/scenario \
  -H "Content-Type: application/json" \
  -d '{
    "mode": "migration",
    "logs": [
      "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188,response_ip=192.168.75.67,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=1747274685",
      "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2,sentry_count=5691,real_host_count=677,dev_start_time=1747274655,dev_end_time=1778688000,time=2025-05-15 10:04:45"
    ]
  }'
```

**请求示例 (客户补全)**:

```bash
curl -X POST http://localhost:8080/api/v1/import/scenario \
  -H "Content-Type: application/json" \
  -d '{
    "mode": "completion",
    "customerId": "customer-001",
    "logs": [
      "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188,response_ip=192.168.75.67,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=1747274685"
    ]
  }'
```

**请求示例 (Java)**:

```java
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import java.util.Arrays;
import java.util.List;

public class ScenarioImportExample {
    
    private static final String BASE_URL = "http://localhost:8080/api/v1/import";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 系统迁移导入
     */
    public ImportResult importMigrationLogs() {
        ScenarioImportRequest request = new ScenarioImportRequest();
        request.setMode("migration");
        request.setLogs(Arrays.asList(
            "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1," +
            "sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188," +
            "response_ip=192.168.75.67,response_port=3389,line_id=1,Iface_type=1," +
            "Vlan_id=0,log_time=1747274685",
            "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2," +
            "sentry_count=5691,real_host_count=677,dev_start_time=1747274655," +
            "dev_end_time=1778688000,time=2025-05-15 10:04:45"
        ));
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<ScenarioImportRequest> httpRequest = new HttpEntity<>(request, headers);
        
        ResponseEntity<ImportResult> response = restTemplate.postForEntity(
            BASE_URL + "/scenario",
            httpRequest,
            ImportResult.class
        );
        
        return response.getBody();
    }
    
    /**
     * 客户数据补全
     */
    public ImportResult completeCustomerData(String customerId) {
        ScenarioImportRequest request = new ScenarioImportRequest();
        request.setMode("completion");
        request.setCustomerId(customerId);
        request.setLogs(Arrays.asList(
            "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1," +
            "sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188," +
            "response_ip=192.168.75.67,response_port=3389,line_id=1,Iface_type=1," +
            "Vlan_id=0,log_time=1747274685"
        ));
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<ScenarioImportRequest> httpRequest = new HttpEntity<>(request, headers);
        
        ResponseEntity<ImportResult> response = restTemplate.postForEntity(
            BASE_URL + "/scenario",
            httpRequest,
            ImportResult.class
        );
        
        return response.getBody();
    }
    
    // DTO类
    public static class ScenarioImportRequest {
        private String mode;
        private String customerId;
        private List<String> logs;
        
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public List<String> getLogs() { return logs; }
        public void setLogs(List<String> logs) { this.logs = logs; }
    }
    
    public static class ImportResult {
        private int totalReceived;
        private int successfullyProcessed;
        private int duplicatesFiltered;
        private int failed;
        private List<String> errors;
        private String mode;
        private String customerId;
        
        // Getters
        public int getTotalReceived() { return totalReceived; }
        public int getSuccessfullyProcessed() { return successfullyProcessed; }
        public int getDuplicatesFiltered() { return duplicatesFiltered; }
        public int getFailed() { return failed; }
        public List<String> getErrors() { return errors; }
        public String getMode() { return mode; }
        public String getCustomerId() { return customerId; }
    }
}
```

**响应示例 (成功)**:

```json
{
  "totalReceived": 2,
  "successfullyProcessed": 2,
  "duplicatesFiltered": 0,
  "failed": 0,
  "errors": [],
  "mode": "migration",
  "customerId": null
}
```

**响应示例 (部分失败)**:

```json
{
  "totalReceived": 3,
  "successfullyProcessed": 2,
  "duplicatesFiltered": 1,
  "failed": 0,
  "errors": [],
  "mode": "completion",
  "customerId": "customer-001"
}
```

**错误码**:

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 导入完成 (可能部分失败，查看响应详情) |
| 400 | 请求参数错误 (无效的mode、缺少customerId等) |
| 500 | 服务器内部错误 |

---

### 6.2 系统迁移导入

**描述**: 专门用于系统迁移的日志导入API。批量导入历史迁移日志，支持大批量数据处理和去重过滤。

**端点**: `POST /api/v1/import/migration`

**请求体**: `Content-Type: application/json`

**请求参数**:

| 字段 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `logs` | String[] | ✅ | syslog日志数组 |
| `batchSize` | Integer | ❌ | 批处理大小 (默认: 100) |

**请求示例 (curl)**:

```bash
curl -X POST http://localhost:8080/api/v1/import/migration \
  -H "Content-Type: application/json" \
  -d '{
    "logs": [
      "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188,response_ip=192.168.75.67,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=1747274685",
      "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2,sentry_count=5691,real_host_count=677,dev_start_time=1747274655,dev_end_time=1778688000,time=2025-05-15 10:04:45"
    ],
    "batchSize": 200
  }'
```

**响应示例**:

```json
{
  "totalReceived": 2,
  "successfullyProcessed": 2,
  "duplicatesFiltered": 0,
  "failed": 0,
  "errors": [],
  "mode": "migration",
  "customerId": null
}
```

---

### 6.3 客户补全导入

**描述**: 为特定客户补全缺失的历史数据。确保数据隔离，只处理指定客户的日志。

**端点**: `POST /api/v1/import/completion/{customerId}`

**路径参数**:

| 参数 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `customerId` | String | ✅ | 客户ID |

**请求体**: `Content-Type: application/json`

**请求参数**:

| 字段 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `logs` | String[] | ✅ | 该客户的syslog日志数组 |

**请求示例 (curl)**:

```bash
curl -X POST http://localhost:8080/api/v1/import/completion/customer-001 \
  -H "Content-Type: application/json" \
  -d '{
    "logs": [
      "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188,response_ip=192.168.75.67,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=1747274685"
    ]
  }'
```

**响应示例**:

```json
{
  "totalReceived": 1,
  "successfullyProcessed": 1,
  "duplicatesFiltered": 0,
  "failed": 0,
  "errors": [],
  "mode": "completion",
  "customerId": "customer-001"
}
```

---

### 6.4 离线研究导入

**描述**: 导入离线安全研究日志，不进行实时威胁评估，用于数据分析和研究目的。

**端点**: `POST /api/v1/import/offline`

**请求体**: `Content-Type: application/json`

**请求参数**:

| 字段 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `logs` | String[] | ✅ | 研究日志数组 |
| `researchId` | String | ❌ | 研究项目ID (用于跟踪) |

**请求示例 (curl)**:

```bash
curl -X POST http://localhost:8080/api/v1/import/offline \
  -H "Content-Type: application/json" \
  -d '{
    "logs": [
      "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=1,sub_type=1,attack_mac=04:42:1a:8e:e3:65,attack_ip=192.168.75.188,response_ip=192.168.75.67,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=1747274685"
    ],
    "researchId": "apt-research-2025-q1"
  }'
```

**响应示例**:

```json
{
  "totalReceived": 1,
  "successfullyProcessed": 1,
  "duplicatesFiltered": 0,
  "failed": 0,
  "errors": [],
  "mode": "offline",
  "customerId": null,
  "researchId": "apt-research-2025-q1"
}
```

---

### 6.5 获取导入模式

**描述**: 获取系统支持的所有导入模式及其说明。

**端点**: `GET /api/v1/import/modes`

**请求示例 (curl)**:

```bash
curl -X GET http://localhost:8080/api/v1/import/modes
```

**请求示例 (Java)**:

```java
import org.springframework.web.client.RestTemplate;
import java.util.List;

public class ImportModesExample {
    
    private static final String BASE_URL = "http://localhost:8080/api/v1/import";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 获取支持的导入模式
     */
    public List<ImportMode> getImportModes() {
        ImportModesResponse response = restTemplate.getForObject(
            BASE_URL + "/modes",
            ImportModesResponse.class
        );
        
        System.out.println("Available modes:");
        response.getModes().forEach(mode -> {
            System.out.println("- " + mode.getName() + ": " + mode.getDescription());
        });
        
        return response.getModes();
    }
    
    // DTO类
    public static class ImportModesResponse {
        private List<ImportMode> modes;
        
        public List<ImportMode> getModes() { return modes; }
        public void setModes(List<ImportMode> modes) { this.modes = modes; }
    }
    
    public static class ImportMode {
        private String name;
        private String description;
        private boolean requiresCustomerId;
        
        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isRequiresCustomerId() { return requiresCustomerId; }
    }
}
```

**响应示例**:

```json
{
  "modes": [
    {
      "name": "migration",
      "description": "系统迁移历史数据导入",
      "requiresCustomerId": false
    },
    {
      "name": "completion",
      "description": "客户数据补全导入",
      "requiresCustomerId": true
    },
    {
      "name": "offline",
      "description": "离线安全研究数据导入",
      "requiresCustomerId": false
    }
  ]
}
```

---

## 请求/响应模型

### ImportResult

**导入结果模型**:

```java
public class ImportResult {
    private int totalReceived;           // 收到的日志总数
    private int successfullyProcessed;   // 成功处理的日志数量
    private int duplicatesFiltered;      // 去重过滤的数量
    private int failed;                  // 处理失败的数量
    private List<String> errors;         // 错误信息列表
    private String mode;                 // 导入模式
    private String customerId;           // 客户ID (completion模式)
    private String researchId;           // 研究ID (offline模式)
}
```

### ScenarioImportRequest

**场景导入请求模型**:

```java
public class ScenarioImportRequest {
    @NotNull
    @Pattern(regexp = "migration|completion|offline")
    private String mode;
    
    @NotNull
    @Size(min = 1, max = 1000)
    private List<String> logs;
    
    private String customerId;  // completion模式时必需
    
    // Getters and Setters
}
```

---

## 使用场景

### 场景5: 系统迁移数据导入

**需求**: 将传统系统的数据迁移到云原生系统。

**实现 (Python脚本)**:

```python
import requests
import json
from pathlib import Path

class MigrationImporter:
    def __init__(self, base_url="http://localhost:8080"):
        self.base_url = base_url
        self.import_url = f"{base_url}/api/v1/import/migration"
    
    def import_migration_logs(self, log_directory):
        """导入迁移日志文件"""
        log_files = Path(log_directory).glob("*.log")
        
        for log_file in log_files:
            print(f"Importing {log_file.name}...")
            
            with open(log_file, 'r') as f:
                logs = [line.strip() for line in f if line.strip()]
            
            if not logs:
                continue
            
            # 分批导入 (每批100条)
            batch_size = 100
            for i in range(0, len(logs), batch_size):
                batch = logs[i:i + batch_size]
                
                payload = {
                    "logs": batch,
                    "batchSize": batch_size
                }
                
                response = requests.post(self.import_url, json=payload)
                
                if response.status_code == 200:
                    result = response.json()
                    print(f"Batch {i//batch_size + 1}: {result['successfullyProcessed']}/{result['totalReceived']}")
                else:
                    print(f"Failed to import batch: {response.text}")

# 使用示例
importer = MigrationImporter()
importer.import_migration_logs("/path/to/migration/logs")
```

---

### 场景6: 客户数据补全

**需求**: 为新客户补全历史攻击数据。

**实现 (Shell脚本)**:

```bash
#!/bin/bash

BASE_URL="http://localhost:8080"
CUSTOMER_ID="customer-001"
LOG_FILE="/path/to/customer_data.log"

# 检查客户是否存在
echo "Checking customer: $CUSTOMER_ID"
customer_exists=$(curl -s -o /dev/null -w "%{http_code}" \
  "$BASE_URL/api/v1/customers/$CUSTOMER_ID/exists")

if [ "$customer_exists" != "200" ]; then
    echo "Customer $CUSTOMER_ID does not exist"
    exit 1
fi

# 读取日志文件
logs=()
while IFS= read -r line; do
    if [[ -n "$line" ]]; then
        logs+=("$line")
    fi
done < "$LOG_FILE"

# 批量补全 (每批50条)
batch_size=50
total_logs=${#logs[@]}

echo "Importing $total_logs logs for customer $CUSTOMER_ID..."

for ((i=0; i<total_logs; i+=batch_size)); do
    end=$((i + batch_size))
    if [ $end -gt $total_logs ]; then
        end=$total_logs
    fi
    
    # 构建JSON payload
    json_payload="{\"logs\":["
    for ((j=i; j<end; j++)); do
        if [ $j -gt $i ]; then
            json_payload+=","
        fi
        # 转义引号
        escaped_log=$(echo "${logs[j]}" | sed 's/"/\\"/g')
        json_payload+="\"$escaped_log\""
    done
    json_payload+="]}"
    
    # 发送请求
    response=$(curl -s -X POST \
      "$BASE_URL/api/v1/import/completion/$CUSTOMER_ID" \
      -H "Content-Type: application/json" \
      -d "$json_payload")
    
    # 检查结果
    if echo "$response" | jq -e '.successfullyProcessed' > /dev/null 2>&1; then
        processed=$(echo "$response" | jq '.successfullyProcessed')
        total=$(echo "$response" | jq '.totalReceived')
        echo "Batch $((i/batch_size + 1)): $processed/$total logs processed"
    else
        echo "Failed to import batch: $response"
    fi
done

echo "Customer data completion finished"
```

---

### 场景7: 离线安全研究

**需求**: 导入APT研究样本数据进行离线分析。

**实现 (Java)**:

```java
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class OfflineResearchImporter {
    
    private final RestTemplate restTemplate;
    private final String importUrl;
    
    public OfflineResearchImporter(String baseUrl) {
        this.restTemplate = new RestTemplate();
        this.importUrl = baseUrl + "/api/v1/import/offline";
    }
    
    /**
     * 导入离线研究日志
     */
    public void importResearchLogs(String researchDirectory, String researchId) throws Exception {
        List<String> logFiles = Files.list(Paths.get(researchDirectory))
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".log"))
            .map(path -> path.toString())
            .collect(Collectors.toList());
        
        System.out.println("Found " + logFiles.size() + " log files for research: " + researchId);
        
        for (String logFile : logFiles) {
            List<String> logs = Files.readAllLines(Paths.get(logFile))
                .stream()
                .filter(line -> !line.trim().isEmpty())
                .collect(Collectors.toList());
            
            if (logs.isEmpty()) {
                continue;
            }
            
            // 构建请求
            OfflineImportRequest request = new OfflineImportRequest();
            request.setLogs(logs);
            request.setResearchId(researchId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<OfflineImportRequest> httpRequest = new HttpEntity<>(request, headers);
            
            // 发送请求
            ResponseEntity<ImportResult> response = restTemplate.postForEntity(
                importUrl,
                httpRequest,
                ImportResult.class
            );
            
            ImportResult result = response.getBody();
            System.out.println("Imported " + logFile + ": " + 
                             result.getSuccessfullyProcessed() + "/" + 
                             result.getTotalReceived());
        }
    }
    
    public static class OfflineImportRequest {
        private List<String> logs;
        private String researchId;
        
        public List<String> getLogs() { return logs; }
        public void setLogs(List<String> logs) { this.logs = logs; }
        public String getResearchId() { return researchId; }
        public void setResearchId(String researchId) { this.researchId = researchId; }
    }
}
```

---

## 最佳实践

### 导入策略选择

| 场景 | 推荐API | 理由 |
|------|---------|------|
| **系统迁移** | `/migration` | 专门优化，批量处理效率高 |
| **客户补全** | `/completion/{customerId}` | 确保数据隔离和客户关联 |
| **离线研究** | `/offline` | 不触发实时告警，适合分析 |
| **通用场景** | `/scenario` | 灵活配置，支持多种模式 |

### 批量大小优化

**推荐配置**:

```java
public class ImportBatchOptimizer {
    
    /**
     * 根据场景选择最佳批量大小
     */
    public int getOptimalBatchSize(String mode) {
        switch (mode) {
            case "migration":
                return 500;    // 大批量，高效迁移
            case "completion":
                return 100;    // 中批量，平衡性能和隔离
            case "offline":
                return 200;    // 中批量，适合研究数据
            default:
                return 100;    // 默认批量大小
        }
    }
}
```

### 错误处理和重试

**导入重试策略**:

```java
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

public class ResilientImporter {
    
    /**
     * 带重试的导入操作
     */
    @Retryable(
        value = {RestClientException.class, HttpServerErrorException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 1.5)
    )
    public ImportResult importWithRetry(List<String> logs, String mode) {
        ScenarioImportRequest request = new ScenarioImportRequest();
        request.setMode(mode);
        request.setLogs(logs);
        
        // 执行导入
        return executeImport(request);
    }
}
```

### 性能监控

**关键指标**:

| 指标 | 监控方法 | 告警阈值 |
|------|---------|---------|
| **导入成功率** | ImportResult.successfullyProcessed/totalReceived | < 95% |
| **去重率** | ImportResult.duplicatesFiltered/totalReceived | > 50% (警告) |
| **处理延迟** | 请求响应时间 | > 30秒 |
| **失败率** | ImportResult.failed/totalReceived | > 5% |

---

## 故障排查

### 问题6: 导入模式不支持

**症状**:
- API返回400错误: "Unsupported import mode"

**解决方案**:

1. **检查支持的模式**:
```bash
curl -X GET http://localhost:8080/api/v1/import/modes
```

2. **使用正确的模式名称**:
- `migration` (不是 `migrate`)
- `completion` (不是 `complete`)
- `offline` (不是 `research`)

---

### 问题7: 客户补全失败

**症状**:
- completion模式返回403错误: "Customer not found"

**解决方案**:

1. **验证客户存在**:
```bash
curl -X GET http://localhost:8080/api/v1/customers/customer-001/exists
```

2. **检查客户ID格式**:
- 确保customerId格式正确
- 检查是否有特殊字符

---

### 问题8: 去重过滤过多

**症状**:
- `duplicatesFiltered` 占比 > 80%

**排查步骤**:

1. **检查Redis缓存**:
```bash
# 查看去重键数量
docker exec redis redis-cli KEYS "threat:import:*" | wc -l
```

2. **调整去重窗口**:
- 考虑缩短去重时间窗口
- 或使用不同的研究ID区分批次

3. **验证日志内容**:
- 确认不是重复导入相同文件

---

### 问题9: 离线导入触发告警

**症状**:
- offline模式仍产生实时告警

**解决方案**:

1. **确认模式配置**:
- 确保使用 `/offline` 端点
- 检查服务端配置是否正确区分offline模式

2. **查看服务日志**:
```bash
docker logs data-ingestion-service | grep "offline.*alert"
```

---

## 相关文档

- **[蜜罐威胁评分方案](./honeypot_based_threat_scoring.md)** - 威胁评分算法详解
- **[威胁评估API](./threat_assessment_api.md)** - 威胁评估服务API
- **[告警管理API](./alert_management_api.md)** - 告警和通知API
- **[邮件通知配置](./email_notification_configuration.md)** - 邮件通知系统配置

---

**文档结束**

*最后更新: 2025-01-16*

### BatchLogRequest

**批量日志请求模型**:

```java
public class BatchLogRequest {
    @NotNull
    @Size(min = 1, max = 1000, message = "Logs array must contain 1-1000 entries")
    private List<String> logs;
    
    // Getters and Setters
}
```

### BatchLogResponse

**批量日志响应模型**:

```java
public class BatchLogResponse {
    private int totalReceived;        // 收到的日志总数
    private int successfullyParsed;   // 成功解析数量
    private int failed;               // 失败数量
    private int publishedToKafka;     // 成功发布到Kafka的数量
    private List<FailedLog> failedLogs; // 失败日志详情
    
    public static class FailedLog {
        private String log;    // 失败的日志内容
        private String error;  // 错误信息
    }
}
```

### ParseStats

**解析统计模型**:

```java
public class ParseStats {
    private long totalParsed;       // 总解析数量
    private long successfulParsed;  // 成功数量
    private long failedParsed;      // 失败数量
    private double successRate;     // 成功率 (0-100)
}
```

---

## 使用场景

### 场景1: 实时日志流式处理

**需求**: rsyslog将攻击日志实时转发到数据摄取服务。

**方案**:

1. 配置rsyslog转发规则:
```bash
# /etc/rsyslog.d/threat-detection.conf
*.* action(type="omhttp"
    server="localhost"
    serverport="8080"
    restpath="/api/v1/logs/ingest"
    template="RSYSLOG_ForwardFormat")
```

2. rsyslog自动调用 `POST /api/v1/logs/ingest` 提交每条日志

3. 服务解析后发布到Kafka主题 `attack-events` 或 `status-events`

**优势**:
- ✅ 低延迟 (< 100ms)
- ✅ 自动化,无需额外脚本
- ✅ 支持高频率日志流

---

### 场景2: 历史日志批量导入

**需求**: 导入过去30天的历史日志文件进行威胁回溯分析。

**实现 (Java)**:

```java
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class HistoricalLogImporter {
    
    private static final int BATCH_SIZE = 500;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String batchUrl = "http://localhost:8080/api/v1/logs/batch";
    
    /**
     * 批量导入历史日志文件
     */
    public void importHistoricalLogs(String logFilePath) throws Exception {
        List<String> batch = new ArrayList<>();
        int totalProcessed = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(logFilePath))) {
            String line;
            
            while ((line = reader.readLine()) != null) {
                batch.add(line);
                
                // 达到批量大小,提交
                if (batch.size() >= BATCH_SIZE) {
                    submitBatch(batch);
                    totalProcessed += batch.size();
                    System.out.println("Processed: " + totalProcessed + " logs");
                    batch.clear();
                }
            }
            
            // 提交最后一批
            if (!batch.isEmpty()) {
                submitBatch(batch);
                totalProcessed += batch.size();
            }
        }
        
        System.out.println("Import completed. Total: " + totalProcessed);
    }
    
    private void submitBatch(List<String> logs) {
        BatchLogRequest request = new BatchLogRequest();
        request.setLogs(new ArrayList<>(logs));
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<BatchLogRequest> httpRequest = new HttpEntity<>(request, headers);
        
        ResponseEntity<BatchLogResponse> response = restTemplate.postForEntity(
            batchUrl,
            httpRequest,
            BatchLogResponse.class
        );
        
        BatchLogResponse result = response.getBody();
        if (result.getFailed() > 0) {
            System.err.println("Batch had " + result.getFailed() + " failures");
        }
    }
}
```

**性能预估**:
- 批量大小: 500条/批
- 处理速度: ~2秒/批
- 吞吐量: ~250条/秒
- 100万条日志: ~66分钟

---

### 场景3: 监控解析成功率

**需求**: 每5分钟检查日志解析成功率,低于95%时告警。

**实现 (Shell脚本)**:

```bash
#!/bin/bash

STATS_URL="http://localhost:8080/api/v1/logs/stats"
THRESHOLD=95.0

while true; do
    # 获取统计信息
    response=$(curl -s $STATS_URL)
    
    # 提取成功率 (使用jq解析JSON)
    success_rate=$(echo $response | jq -r '.successRate')
    
    echo "[$(date)] Success rate: $success_rate%"
    
    # 检查是否低于阈值
    if (( $(echo "$success_rate < $THRESHOLD" | bc -l) )); then
        echo "⚠️ ALERT: Parse success rate below threshold!"
        echo "$response" | jq '.'
        
        # 发送告警 (可集成到告警系统)
        # send_alert "Log parse success rate: $success_rate%"
    fi
    
    # 等待5分钟
    sleep 300
done
```

**Java版本**:

```java
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ParseRateMonitor {
    
    private static final double THRESHOLD = 95.0;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String statsUrl = "http://localhost:8080/api/v1/logs/stats";
    
    /**
     * 启动监控 (每5分钟检查一次)
     */
    public void startMonitoring() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                ParseStats stats = restTemplate.getForObject(statsUrl, ParseStats.class);
                
                System.out.println("[" + LocalDateTime.now() + "] Success rate: " 
                                   + stats.getSuccessRate() + "%");
                
                if (stats.getSuccessRate() < THRESHOLD) {
                    System.err.println("⚠️ ALERT: Parse success rate below " + THRESHOLD + "%");
                    System.err.println("Total: " + stats.getTotalParsed());
                    System.err.println("Failed: " + stats.getFailedParsed());
                    
                    // 发送告警
                    sendAlert(stats);
                }
            } catch (Exception e) {
                System.err.println("Monitoring check failed: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.MINUTES);
    }
    
    private void sendAlert(ParseStats stats) {
        // 集成到告警系统 (如发送邮件、Slack通知等)
    }
}
```

---

### 场景4: 设备上线自动注册

**需求**: 新蜜罐设备首次发送日志时,自动检查客户映射,未找到则告警。

**实现**:

```java
public class DeviceRegistrationChecker {
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final String mappingUrl = "http://localhost:8080/api/v1/logs/customer-mapping/";
    
    /**
     * 检查设备是否已注册
     */
    public boolean checkDeviceRegistration(String devSerial) {
        try {
            String url = mappingUrl + devSerial;
            CustomerMapping mapping = restTemplate.getForObject(url, CustomerMapping.class);
            
            if (mapping.getCustomerId() == null) {
                System.err.println("⚠️ Unregistered device detected: " + devSerial);
                System.err.println("Action required: Add customer mapping to database");
                
                // 发送告警给管理员
                sendAdminAlert(devSerial);
                
                return false;
            }
            
            System.out.println("✅ Device " + devSerial + " → Customer " + mapping.getCustomerId());
            return true;
            
        } catch (Exception e) {
            System.err.println("Failed to check device registration: " + e.getMessage());
            return false;
        }
    }
    
    private void sendAdminAlert(String devSerial) {
        // 发送邮件通知管理员添加设备映射
    }
}
```

---

## Java客户端示例

### 完整客户端实现

```java
package com.threatdetection.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * 数据摄取服务完整客户端
 */
public class DataIngestionClient {
    
    private final String baseUrl;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public DataIngestionClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 单条日志摄取
     */
    public boolean ingestSingleLog(String syslogMessage) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            
            HttpEntity<String> request = new HttpEntity<>(syslogMessage, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/ingest",
                request,
                String.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            System.err.println("Failed to ingest log: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 批量日志摄取
     */
    public BatchLogResponse ingestBatchLogs(List<String> logs) {
        try {
            BatchLogRequest request = new BatchLogRequest();
            request.setLogs(logs);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<BatchLogRequest> httpRequest = new HttpEntity<>(request, headers);
            
            ResponseEntity<BatchLogResponse> response = restTemplate.postForEntity(
                baseUrl + "/batch",
                httpRequest,
                BatchLogResponse.class
            );
            
            return response.getBody();
            
        } catch (Exception e) {
            System.err.println("Failed to ingest batch: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取解析统计
     */
    public ParseStats getParseStats() {
        try {
            return restTemplate.getForObject(
                baseUrl + "/stats",
                ParseStats.class
            );
        } catch (Exception e) {
            System.err.println("Failed to get stats: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 重置统计
     */
    public boolean resetStats() {
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/stats/reset",
                null,
                String.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            System.err.println("Failed to reset stats: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 健康检查
     */
    public boolean isHealthy() {
        try {
            HealthStatus status = restTemplate.getForObject(
                baseUrl + "/health",
                HealthStatus.class
            );
            return "UP".equals(status.getStatus());
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 查询设备客户映射
     */
    public String getCustomerId(String devSerial) {
        try {
            String url = baseUrl + "/customer-mapping/" + devSerial;
            CustomerMapping mapping = restTemplate.getForObject(url, CustomerMapping.class);
            return mapping != null ? mapping.getCustomerId() : null;
        } catch (Exception e) {
            System.err.println("Failed to get customer mapping: " + e.getMessage());
            return null;
        }
    }
    
    // DTO类定义
    public static class BatchLogRequest {
        private List<String> logs;
        public List<String> getLogs() { return logs; }
        public void setLogs(List<String> logs) { this.logs = logs; }
    }
    
    public static class BatchLogResponse {
        private int totalReceived;
        private int successfullyParsed;
        private int failed;
        private int publishedToKafka;
        private List<FailedLog> failedLogs;
        
        // Getters
        public int getTotalReceived() { return totalReceived; }
        public int getSuccessfullyParsed() { return successfullyParsed; }
        public int getFailed() { return failed; }
        public int getPublishedToKafka() { return publishedToKafka; }
        public List<FailedLog> getFailedLogs() { return failedLogs; }
    }
    
    public static class FailedLog {
        private String log;
        private String error;
        public String getLog() { return log; }
        public String getError() { return error; }
    }
    
    public static class ParseStats {
        private long totalParsed;
        private long successfulParsed;
        private long failedParsed;
        private double successRate;
        
        public long getTotalParsed() { return totalParsed; }
        public long getSuccessfulParsed() { return successfulParsed; }
        public long getFailedParsed() { return failedParsed; }
        public double getSuccessRate() { return successRate; }
    }
    
    public static class HealthStatus {
        private String status;
        public String getStatus() { return status; }
    }
    
    public static class CustomerMapping {
        private String devSerial;
        private String customerId;
        public String getDevSerial() { return devSerial; }
        public String getCustomerId() { return customerId; }
    }
}
```

### 使用示例

```java
public class ClientUsageExample {
    
    public static void main(String[] args) {
        DataIngestionClient client = new DataIngestionClient("http://localhost:8080/api/v1/logs");
        
        // 1. 健康检查
        if (!client.isHealthy()) {
            System.err.println("Service is not healthy!");
            return;
        }
        
        // 2. 提交单条攻击日志
        String attackLog = "syslog_version=1.10.0,dev_serial=GSFB2204200410007425," +
                          "log_type=1,sub_type=1,attack_mac=04:42:1a:8e:e3:65," +
                          "attack_ip=192.168.75.188,response_ip=192.168.75.67," +
                          "response_port=3389,line_id=1,Iface_type=1,Vlan_id=0," +
                          "log_time=1747274685";
        
        boolean success = client.ingestSingleLog(attackLog);
        System.out.println("Single log ingested: " + success);
        
        // 3. 批量提交
        List<String> logs = Arrays.asList(
            attackLog,
            "syslog_version=1.10.0,dev_serial=GSFB2204200410007425,log_type=2," +
            "sentry_count=5691,real_host_count=677,dev_start_time=1747274655," +
            "dev_end_time=1778688000,time=2025-05-15 10:04:45"
        );
        
        DataIngestionClient.BatchLogResponse response = client.ingestBatchLogs(logs);
        System.out.println("Batch result: " + response.getSuccessfullyParsed() + "/" + response.getTotalReceived());
        
        // 4. 查询统计
        DataIngestionClient.ParseStats stats = client.getParseStats();
        System.out.println("Success rate: " + stats.getSuccessRate() + "%");
        
        // 5. 查询设备映射
        String customerId = client.getCustomerId("GSFB2204200410007425");
        System.out.println("Customer ID: " + customerId);
    }
}
```

---

## 最佳实践

### 1. 批量大小选择

**推荐策略**:

```java
public class BatchSizeOptimizer {
    
    /**
     * 根据日志频率动态调整批量大小
     */
    public int calculateOptimalBatchSize(int logsPerSecond) {
        if (logsPerSecond < 10) {
            return 10;      // 低频: 小批量,低延迟
        } else if (logsPerSecond < 100) {
            return 100;     // 中频: 平衡延迟和吞吐量
        } else if (logsPerSecond < 500) {
            return 500;     // 高频: 大批量,高吞吐量
        } else {
            return 1000;    // 极高频: 最大批量
        }
    }
}
```

### 2. 错误处理和重试

**推荐模式**:

```java
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

public class ResilientIngestionClient {
    
    /**
     * 带重试的日志摄取 (最多重试3次,指数退避)
     */
    @Retryable(
        value = {RestClientException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public boolean ingestWithRetry(String log) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        
        HttpEntity<String> request = new HttpEntity<>(log, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl + "/ingest",
            request,
            String.class
        );
        
        return response.getStatusCode().is2xxSuccessful();
    }
}
```

### 3. 性能监控

**关键指标**:

| 指标 | 监控方法 | 告警阈值 |
|------|---------|---------|
| **解析成功率** | GET /stats | < 95% |
| **Kafka发布延迟** | 日志分析 | > 1秒 |
| **批量处理耗时** | 响应时间 | > 5秒 |
| **失败日志比例** | BatchLogResponse | > 5% |

### 4. 日志验证

**客户端验证**:

```java
public class LogValidator {
    
    /**
     * 提交前验证日志格式
     */
    public boolean validateAttackLog(String log) {
        String[] fields = log.split(",");
        Map<String, String> logMap = new HashMap<>();
        
        for (String field : fields) {
            String[] kv = field.split("=");
            if (kv.length == 2) {
                logMap.put(kv[0], kv[1]);
            }
        }
        
        // 检查必需字段
        String[] requiredFields = {
            "syslog_version", "dev_serial", "log_type",
            "attack_mac", "attack_ip", "response_ip", "response_port"
        };
        
        for (String field : requiredFields) {
            if (!logMap.containsKey(field)) {
                System.err.println("Missing required field: " + field);
                return false;
            }
        }
        
        // 验证log_type
        if (!"1".equals(logMap.get("log_type"))) {
            System.err.println("Invalid log_type for attack log");
            return false;
        }
        
        return true;
    }
}
```

### 5. 连接池配置

**优化RestTemplate连接池**:

```java
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

public class OptimizedRestTemplateConfig {
    
    /**
     * 配置高性能RestTemplate
     */
    public RestTemplate createOptimizedRestTemplate() {
        HttpClient httpClient = HttpClientBuilder.create()
            .setMaxConnTotal(200)           // 最大连接数
            .setMaxConnPerRoute(50)         // 每个路由最大连接数
            .build();
        
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(5000);    // 连接超时5秒
        factory.setReadTimeout(10000);      // 读取超时10秒
        
        return new RestTemplate(factory);
    }
}
```

---

## 故障排查

### 问题1: 日志解析失败

**症状**:
- BatchLogResponse中 `failed` > 0
- 统计中 `failedParsed` 持续增长

**排查步骤**:

1. **检查失败日志详情**:
```java
BatchLogResponse response = client.ingestBatchLogs(logs);
if (!response.getFailedLogs().isEmpty()) {
    response.getFailedLogs().forEach(failure -> {
        System.err.println("Failed log: " + failure.getLog());
        System.err.println("Error: " + failure.getError());
    });
}
```

2. **常见错误原因**:

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| `Missing required field: attack_mac` | 日志缺少必需字段 | 检查日志格式,补充缺失字段 |
| `Invalid log_type` | log_type不是1或2 | 验证日志来源 |
| `Customer mapping not found` | 设备未注册 | 添加设备到customer映射表 |
| `Invalid timestamp format` | log_time格式错误 | 确保为Unix时间戳(秒) |

3. **查看服务端日志**:
```bash
docker logs data-ingestion-service | grep ERROR
```

---

### 问题2: Kafka发布失败

**症状**:
- `publishedToKafka` < `successfullyParsed`
- 服务日志中有Kafka连接错误

**排查步骤**:

1. **检查Kafka健康状态**:
```bash
docker-compose ps kafka
docker logs kafka | tail -100
```

2. **验证Kafka主题**:
```bash
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
```

3. **检查网络连接**:
```bash
curl -X GET http://localhost:8080/api/v1/logs/health
```

4. **查看Kafka消费者**:
```bash
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic attack-events \
  --from-beginning \
  --max-messages 10
```

---

### 问题3: 批量处理超时

**症状**:
- 批量请求返回504 Gateway Timeout
- 批量大小为1000时频繁失败

**解决方案**:

1. **减小批量大小**:
```java
// 从1000降到500
List<List<String>> batches = Lists.partition(allLogs, 500);
```

2. **增加客户端超时**:
```java
factory.setReadTimeout(30000); // 30秒
```

3. **检查服务端性能**:
```bash
# 查看CPU和内存使用
docker stats data-ingestion-service
```

---

### 问题4: 设备映射未找到

**症状**:
- 查询 `/customer-mapping/{devSerial}` 返回 `customerId: null`
- 日志中警告 "No customer mapping found"

**解决方案**:

1. **查询数据库映射表**:
```sql
SELECT * FROM device_customer_mapping WHERE dev_serial = 'GSFB2204200410007425';
```

2. **手动添加映射**:
```sql
INSERT INTO device_customer_mapping (dev_serial, customer_id, created_at)
VALUES ('GSFB2204200410007425', 'customer_a', CURRENT_TIMESTAMP);
```

3. **批量导入映射**:
```sql
COPY device_customer_mapping(dev_serial, customer_id)
FROM '/path/to/mapping.csv'
DELIMITER ','
CSV HEADER;
```

---

### 问题5: 统计数据异常

**症状**:
- `successRate` 显示 > 100% 或负数
- 统计数据与实际不符

**解决方案**:

1. **重置统计**:
```bash
curl -X POST http://localhost:8080/api/v1/logs/stats/reset
```

2. **重启服务** (统计在内存中):
```bash
docker-compose restart data-ingestion-service
```

3. **检查并发问题** (如果使用多实例):
- 统计是实例级别的,不是全局的
- 考虑使用外部指标系统 (Prometheus)

---

## 相关文档

- **[蜜罐威胁评分方案](./honeypot_based_threat_scoring.md)** - 威胁评分算法详解
- **[威胁评估API](./threat_assessment_api.md)** - 威胁评估服务API
- **[告警管理API](./alert_management_api.md)** - 告警和通知API
- **[邮件通知配置](./email_notification_configuration.md)** - 邮件通知系统配置

---

**文档结束**

*最后更新: 2025-01-16*
