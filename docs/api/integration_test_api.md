# 集成测试API文档

**服务**: Alert Management Service  
**端口**: 8082  
**基础路径**: `/api/v1/integration-test`  
**认证**: Bearer Token (测试环境可选)  
**版本**: v1

---

## 目录

1. [系统概述](#1-系统概述)
2. [核心功能](#2-核心功能)
3. [测试配置](#3-测试配置)
4. [数据模型](#4-数据模型)
5. [API端点列表](#5-api端点列表)
6. [API详细文档](#6-api详细文档)
7. [使用场景](#7-使用场景)
8. [Java客户端完整示例](#8-java客户端完整示例)
9. [最佳实践](#9-最佳实践)
10. [故障排查](#10-故障排查)
11. [相关文档](#11-相关文档)

---

## 1. 系统概述

### 1.1 核心功能

集成测试API提供测试环境的监控和配额管理,支持:

- **配额监控**: 实时查看邮件发送配额和使用情况
- **测试状态**: 查询集成测试服务运行状态
- **限流保护**: 防止测试环境邮件轰炸
- **窗口管理**: 滚动时间窗口自动重置配额
- **测试验证**: 验证邮件通知功能是否正常

### 1.2 测试流程

```
告警触发 → 严重性过滤 → 配额检查 → 邮件发送 → 统计更新
   ↓           ↓           ↓          ↓          ↓
CRITICAL   仅CRITICAL   5封/10分钟  SMTP发送   窗口计数
```

### 1.3 适用场景

| 场景 | 说明 |
|------|------|
| **开发测试** | 本地开发时验证邮件功能 |
| **集成测试** | CI/CD流程中的自动化测试 |
| **功能验证** | 验证告警通知链路完整性 |
| **性能测试** | 测试限流和配额管理机制 |

---

## 2. 核心功能

### 2.1 邮件配额管理

**限流机制**:
- 时间窗口: 10分钟滚动窗口
- 配额限制: 每个窗口最多5封邮件
- 触发条件: 仅CRITICAL级别告警
- 自动重置: 窗口结束后配额自动恢复

**配额计算**:
```java
remainingEmails = maxEmailsPerWindow - emailsSentInCurrentWindow
canSendEmail = (remainingEmails > 0)
```

### 2.2 测试状态监控

**监控指标**:
- 服务启用状态 (enabled/disabled)
- 配置的收件人邮箱
- 当前通知规则
- 窗口使用情况

### 2.3 自动化测试支持

**测试能力**:
- 邮件发送前配额预检
- 测试环境配置验证
- 邮件发送结果追踪
- 批量测试用例执行

---

## 3. 测试配置

### 3.1 当前配置

```yaml
# application-test.yml
integration-test:
  enabled: true
  email:
    recipient: kylecui@outlook.com
    max-per-window: 5
    window-minutes: 10
  alert-filter:
    severities:
      - CRITICAL
```

### 3.2 配置说明

| 配置项 | 值 | 说明 |
|--------|-----|------|
| **enabled** | true | 启用集成测试服务 |
| **recipient** | kylecui@outlook.com | 测试邮箱地址 |
| **max-per-window** | 5 | 窗口内最大邮件数 |
| **window-minutes** | 10 | 时间窗口长度(分钟) |
| **severities** | [CRITICAL] | 触发通知的告警级别 |

### 3.3 环境变量

```bash
# 启用/禁用测试服务
INTEGRATION_TEST_ENABLED=true

# 测试收件人
INTEGRATION_TEST_EMAIL=kylecui@outlook.com

# 邮件限制
INTEGRATION_TEST_MAX_EMAILS=5
INTEGRATION_TEST_WINDOW_MINUTES=10
```

---

## 4. 数据模型

### 4.1 响应实体类

#### NotificationStats (通知统计)

```java
@Data
@Builder
public class NotificationStats {
    private Integer emailsSentInCurrentWindow;  // 当前窗口已发送数
    private Integer maxEmailsPerWindow;         // 窗口最大限制
    private Integer remainingEmails;            // 剩余配额
    private Integer windowMinutes;              // 窗口长度(分钟)
    private String windowStart;                 // 窗口开始时间
    private String currentTime;                 // 当前时间
    private Double usagePercentage;             // 使用率 (%)
    private String nextResetTime;               // 下次重置时间
}
```

#### TestStatus (测试状态)

```java
@Data
@Builder
public class TestStatus {
    private Boolean enabled;                    // 是否启用
    private String emailRecipient;              // 收件人
    private String notificationRules;           // 通知规则
    private String description;                 // 服务描述
    private List<String> allowedSeverities;     // 允许的严重等级
    private LocalDateTime serviceStartTime;     // 服务启动时间
    private Long totalEmailsSent;               // 总发送数
}
```

#### TestResult (测试结果)

```java
@Data
@Builder
public class TestResult {
    private Boolean success;                    // 测试是否成功
    private String testName;                    // 测试名称
    private Integer emailsSent;                 // 发送邮件数
    private Long durationMs;                    // 耗时(毫秒)
    private String message;                     // 结果消息
    private Map<String, Object> details;        // 详细信息
}
```

---

## 5. API端点列表

| 方法 | 端点 | 功能 | 认证 |
|------|------|------|------|
| `GET` | `/api/v1/integration-test/stats` | 获取通知统计 | ❌ |
| `GET` | `/api/v1/integration-test/status` | 获取测试状态 | ❌ |
| `POST` | `/api/v1/integration-test/send-test-email` | 发送测试邮件 | ❌ |
| `POST` | `/api/v1/integration-test/reset-quota` | 重置邮件配额 | ✅ |
| `GET` | `/api/v1/integration-test/history` | 查询发送历史 | ❌ |

---

---

## 6. API详细文档

### 6.1 获取通知统计

**端点**: `GET /api/v1/integration-test/stats`

**功能说明**: 获取当前时间窗口的邮件发送统计和配额信息

**请求示例**:

```bash
curl -X GET http://localhost:8082/api/v1/integration-test/stats
```

```java
public NotificationStats getNotificationStats() {
    String url = baseUrl + "/integration-test/stats";
    return restTemplate.getForObject(url, NotificationStats.class);
}
```

**响应示例**:

```json
{
  "emailsSentInCurrentWindow": 3,
  "maxEmailsPerWindow": 5,
  "remainingEmails": 2,
  "windowMinutes": 10,
  "windowStart": "2025-01-15T10:00:00Z",
  "currentTime": "2025-01-15T10:05:30Z",
  "usagePercentage": 60.0,
  "nextResetTime": "2025-01-15T10:10:00Z"
}
```

**字段说明**:

| 字段 | 类型 | 说明 |
|-----|------|------|
| `emailsSentInCurrentWindow` | Integer | 当前窗口已发送邮件数 |
| `maxEmailsPerWindow` | Integer | 窗口内最大邮件数限制 |
| `remainingEmails` | Integer | 剩余可发送邮件数 |
| `windowMinutes` | Integer | 时间窗口长度 (分钟) |
| `windowStart` | String | 当前窗口开始时间 (ISO 8601) |
| `currentTime` | String | 当前服务器时间 (ISO 8601) |
| `usagePercentage` | Double | 配额使用率 (0-100%) |
| `nextResetTime` | String | 下次配额重置时间 |

---

### 6.2 获取测试状态

**端点**: `GET /api/v1/integration-test/status`

**功能说明**: 获取集成测试服务的运行状态和配置信息

**请求示例**:

```bash
curl -X GET http://localhost:8082/api/v1/integration-test/status
```

```java
public TestStatus getTestStatus() {
    String url = baseUrl + "/integration-test/status";
    return restTemplate.getForObject(url, TestStatus.class);
}
```

**响应示例**:

```json
{
  "enabled": true,
  "emailRecipient": "kylecui@outlook.com",
  "notificationRules": "仅CRITICAL等级告警，每10分钟最多5封邮件",
  "description": "集成测试服务正在运行，监听威胁事件并发送邮件通知",
  "allowedSeverities": ["CRITICAL"],
  "serviceStartTime": "2025-01-15T08:00:00Z",
  "totalEmailsSent": 127
}
```

---

### 6.3 发送测试邮件

**端点**: `POST /api/v1/integration-test/send-test-email`

**功能说明**: 手动发送一封测试邮件,验证邮件功能

**请求体**:

```json
{
  "subject": "集成测试 - 测试邮件",
  "content": "这是一封来自集成测试API的测试邮件",
  "testName": "manual-test"
}
```

**请求示例**:

```bash
curl -X POST http://localhost:8082/api/v1/integration-test/send-test-email \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "集成测试 - 测试邮件",
    "content": "这是一封测试邮件",
    "testName": "manual-test"
  }'
```

```java
public TestResult sendTestEmail(String subject, String content) {
    TestEmailRequest request = TestEmailRequest.builder()
        .subject(subject)
        .content(content)
        .testName("api-test")
        .build();
    
    return restTemplate.postForObject(
        baseUrl + "/integration-test/send-test-email",
        request,
        TestResult.class
    );
}
```

**响应示例**:

```json
{
  "success": true,
  "testName": "manual-test",
  "emailsSent": 1,
  "durationMs": 1250,
  "message": "测试邮件发送成功",
  "details": {
    "recipient": "kylecui@outlook.com",
    "subject": "集成测试 - 测试邮件",
    "sentAt": "2025-01-15T10:06:00Z",
    "remainingQuota": 1
  }
}
```

**状态码**:
- `200 OK`: 发送成功
- `429 Too Many Requests`: 配额已用完
- `500 Internal Server Error`: 发送失败

---

### 6.4 重置邮件配额

**端点**: `POST /api/v1/integration-test/reset-quota`

**功能说明**: 手动重置邮件配额(需要管理员权限)

**请求示例**:

```bash
curl -X POST http://localhost:8082/api/v1/integration-test/reset-quota \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

```java
public void resetQuota() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(adminToken);
    
    HttpEntity<Void> request = new HttpEntity<>(headers);
    
    restTemplate.postForEntity(
        baseUrl + "/integration-test/reset-quota",
        request,
        Map.class
    );
}
```

**响应示例**:

```json
{
  "success": true,
  "message": "邮件配额已重置",
  "previousCount": 5,
  "newCount": 0,
  "resetTime": "2025-01-15T10:07:00Z"
}
```

---

### 6.5 查询发送历史

**端点**: `GET /api/v1/integration-test/history`

**查询参数**:
- `limit` (可选): 返回数量,默认10
- `since` (可选): 起始时间,ISO 8601格式

**请求示例**:

```bash
# 查询最近10条
curl -X GET http://localhost:8082/api/v1/integration-test/history

# 查询最近1小时的记录
curl -X GET "http://localhost:8082/api/v1/integration-test/history?since=2025-01-15T09:00:00Z&limit=20"
```

```java
public List<EmailHistory> getEmailHistory(int limit) {
    String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/integration-test/history")
        .queryParam("limit", limit)
        .toUriString();
    
    ResponseEntity<List<EmailHistory>> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        new ParameterizedTypeReference<List<EmailHistory>>() {}
    );
    
    return response.getBody();
}
```

**响应示例**:

```json
[
  {
    "id": 1234,
    "recipient": "kylecui@outlook.com",
    "subject": "CRITICAL告警 - 攻击检测",
    "sentAt": "2025-01-15T10:05:00Z",
    "success": true,
    "alertId": 5678,
    "severity": "CRITICAL"
  },
  {
    "id": 1233,
    "recipient": "kylecui@outlook.com",
    "subject": "CRITICAL告警 - 异常流量",
    "sentAt": "2025-01-15T10:03:00Z",
    "success": true,
    "alertId": 5677,
    "severity": "CRITICAL"
  }
]
```

---

## 7. 使用场景

### 场景1: 监控邮件配额

```java
@Service
@Slf4j
public class EmailQuotaMonitor {
    
    private final IntegrationTestClient testClient;
    
    /**
     * 在发送邮件前检查配额
     */
    public boolean checkQuotaBeforeSending() {
        NotificationStats stats = testClient.getNotificationStats();
        
        log.info("邮件配额状态: 已用{}/{}, 剩余{}",
            stats.getEmailsSentInCurrentWindow(),
            stats.getMaxEmailsPerWindow(),
            stats.getRemainingEmails()
        );
        
        if (stats.getRemainingEmails() <= 0) {
            log.warn("⚠️ 邮件配额已用完, 下次重置时间: {}",
                stats.getNextResetTime()
            );
            return false;
        }
        
        if (stats.getUsagePercentage() >= 80.0) {
            log.warn("⚠️ 邮件配额使用率{}%, 即将用完",
                stats.getUsagePercentage()
            );
        }
        
        return true;
    }
    
    /**
     * 等待配额重置
     */
    public void waitForQuotaReset() throws InterruptedException {
        NotificationStats stats = testClient.getNotificationStats();
        
        if (stats.getRemainingEmails() > 0) {
            return;  // 还有配额,无需等待
        }
        
        Instant resetTime = Instant.parse(stats.getNextResetTime());
        Instant now = Instant.now();
        
        long waitSeconds = ChronoUnit.SECONDS.between(now, resetTime);
        
        if (waitSeconds > 0) {
            log.info("等待配额重置, 剩余{}秒", waitSeconds);
            Thread.sleep(waitSeconds * 1000);
        }
    }
}
```

---

### 场景2: 自动化测试脚本

```java
@SpringBootTest
@Slf4j
public class IntegrationTestRunner {
    
    @Autowired
    private IntegrationTestClient testClient;
    
    @Autowired
    private AlertClient alertClient;
    
    @Test
    public void testEmailNotificationWorkflow() {
        log.info("=== 开始邮件通知集成测试 ===");
        
        // 1. 验证测试环境
        assertTrue(validateEnvironment(), "测试环境验证失败");
        
        // 2. 记录初始状态
        NotificationStats initialStats = testClient.getNotificationStats();
        int initialCount = initialStats.getEmailsSentInCurrentWindow();
        
        // 3. 创建CRITICAL告警触发邮件
        Alert criticalAlert = createCriticalAlert();
        
        // 4. 等待邮件发送
        sleep(5000);
        
        // 5. 验证邮件已发送
        NotificationStats finalStats = testClient.getNotificationStats();
        int finalCount = finalStats.getEmailsSentInCurrentWindow();
        
        assertEquals(initialCount + 1, finalCount, "邮件数量应增加1");
        
        // 6. 验证发送历史
        List<EmailHistory> history = testClient.getEmailHistory(1);
        assertFalse(history.isEmpty(), "应有邮件发送记录");
        assertEquals(criticalAlert.getId(), history.get(0).getAlertId());
        
        log.info("✅ 集成测试通过");
    }
    
    private boolean validateEnvironment() {
        TestStatus status = testClient.getTestStatus();
        
        if (!status.getEnabled()) {
            log.error("集成测试服务未启用");
            return false;
        }
        
        if (status.getAllowedSeverities() == null 
            || !status.getAllowedSeverities().contains("CRITICAL")) {
            log.error("未配置CRITICAL告警通知");
            return false;
        }
        
        log.info("✅ 测试环境验证通过");
        return true;
    }
    
    private Alert createCriticalAlert() {
        Alert alert = Alert.builder()
            .customerId("test-customer")
            .severity("CRITICAL")
            .attackMac("00:11:22:33:44:55")
            .attackIp("192.168.1.100")
            .threatScore(250.0)
            .build();
        
        return alertClient.createAlert(alert);
    }
}
```

---

### 场景3: 配额压力测试

```java
@Service
@Slf4j
public class QuotaStressTest {
    
    private final IntegrationTestClient testClient;
    
    /**
     * 测试配额限制机制
     */
    public void testQuotaLimit() {
        log.info("=== 开始配额压力测试 ===");
        
        // 1. 重置配额
        testClient.resetQuota();
        
        NotificationStats stats = testClient.getNotificationStats();
        int maxEmails = stats.getMaxEmailsPerWindow();
        
        log.info("配额限制: {} 封/{} 分钟", maxEmails, stats.getWindowMinutes());
        
        // 2. 连续发送直到配额用完
        int successCount = 0;
        int rejectCount = 0;
        
        for (int i = 0; i < maxEmails + 3; i++) {
            TestResult result = testClient.sendTestEmail(
                "压力测试 #" + (i + 1),
                "测试邮件内容"
            );
            
            if (result.getSuccess()) {
                successCount++;
                log.info("第{}封发送成功", i + 1);
            } else {
                rejectCount++;
                log.warn("第{}封被拒绝: {}", i + 1, result.getMessage());
            }
        }
        
        // 3. 验证结果
        assertEquals(maxEmails, successCount, "成功发送数应等于配额");
        assertEquals(3, rejectCount, "超出配额的请求应被拒绝");
        
        NotificationStats finalStats = testClient.getNotificationStats();
        assertEquals(0, finalStats.getRemainingEmails(), "配额应已用完");
        
        log.info("✅ 配额限制测试通过: 成功{}, 拒绝{}", successCount, rejectCount);
    }
    
    /**
     * 测试窗口重置机制
     */
    public void testWindowReset() throws InterruptedException {
        log.info("=== 开始窗口重置测试 ===");
        
        // 1. 用完当前窗口配额
        testQuotaLimit();
        
        NotificationStats stats = testClient.getNotificationStats();
        Instant resetTime = Instant.parse(stats.getNextResetTime());
        Instant now = Instant.now();
        
        long waitSeconds = ChronoUnit.SECONDS.between(now, resetTime) + 5;
        
        log.info("等待{}秒直到窗口重置", waitSeconds);
        Thread.sleep(waitSeconds * 1000);
        
        // 2. 验证配额已重置
        NotificationStats newStats = testClient.getNotificationStats();
        assertEquals(stats.getMaxEmailsPerWindow(), 
                    newStats.getRemainingEmails(),
                    "配额应已重置");
        
        // 3. 验证可以继续发送
        TestResult result = testClient.sendTestEmail(
            "重置后测试",
            "验证窗口重置"
        );
        
        assertTrue(result.getSuccess(), "重置后应可发送邮件");
        
        log.info("✅ 窗口重置测试通过");
    }
}
```

---

---

## 获取测试状态

**端点**: `GET /api/v1/integration-test/status`

### 功能说明

获取集成测试服务的运行状态和配置信息。

### 请求示例 (curl)

```bash
curl -X GET http://localhost:8082/api/v1/integration-test/status
```

### 请求示例 (Java)

```java
public class IntegrationTestClient {
    
    /**
     * 获取测试状态
     */
    public TestStatus getTestStatus() {
        ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(
            BASE_URL + "/status",
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        
        return mapToTestStatus(response.getBody());
    }
    
    /**
     * 检查集成测试是否已启用
     */
    public boolean isTestingEnabled() {
        TestStatus status = getTestStatus();
        return status.isEnabled();
    }
    
    // DTO类
    public static class TestStatus {
        private boolean enabled;
        private String emailRecipient;
        private String notificationRules;
        private String description;
        
        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getEmailRecipient() { return emailRecipient; }
        public void setEmailRecipient(String emailRecipient) { 
            this.emailRecipient = emailRecipient; 
        }
        
        public String getNotificationRules() { return notificationRules; }
        public void setNotificationRules(String notificationRules) { 
            this.notificationRules = notificationRules; 
        }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { 
            this.description = description; 
        }
    }
}
```

### 响应示例

**HTTP 200 OK**

```json
{
  "enabled": true,
  "emailRecipient": "kylecui@outlook.com",
  "notificationRules": "仅CRITICAL等级告警，每10分钟最多5封邮件",
  "description": "集成测试服务正在运行，监听威胁事件并发送邮件通知"
}
```

---

## 使用场景

### 场景1: 监控邮件配额

```java
public class EmailQuotaMonitor {
    
    private final IntegrationTestClient testClient;
    
    /**
     * 在发送邮件前检查配额
     */
    public boolean checkQuotaBeforeSending() {
        NotificationStats stats = testClient.getNotificationStats();
        
        System.out.println("邮件配额状态:");
        System.out.println("- 已发送: " + stats.getEmailsSentInCurrentWindow());
        System.out.println("- 限制: " + stats.getMaxEmailsPerWindow());
        System.out.println("- 剩余: " + stats.getRemainingEmails());
        
        if (stats.getRemainingEmails() <= 0) {
            System.err.println("⚠️ 邮件配额已用完,请等待下一个窗口");
            System.err.println("下次可用时间: " + calculateNextWindow(stats));
            return false;
        }
        
        if (stats.getRemainingEmails() <= 1) {
            System.out.println("⚠️ 邮件配额即将用完,仅剩 " + stats.getRemainingEmails() + " 封");
        }
        
        return true;
    }
    
    private String calculateNextWindow(NotificationStats stats) {
        // 计算下一个窗口开始时间
        Instant windowStart = Instant.parse(stats.getWindowStart());
        Instant nextWindow = windowStart.plus(stats.getWindowMinutes(), ChronoUnit.MINUTES);
        return nextWindow.toString();
    }
}
```

### 场景2: 测试环境验证

```java
public class TestEnvironmentValidator {
    
    /**
     * 验证测试环境配置
     */
    public boolean validateTestEnvironment() {
        try {
            // 1. 检查测试服务状态
            TestStatus status = testClient.getTestStatus();
            
            if (!status.isEnabled()) {
                System.err.println("❌ 集成测试服务未启用");
                return false;
            }
            
            System.out.println("✅ 测试服务已启用");
            System.out.println("收件人: " + status.getEmailRecipient());
            System.out.println("规则: " + status.getNotificationRules());
            
            // 2. 检查邮件配额
            NotificationStats stats = testClient.getNotificationStats();
            
            if (stats.getMaxEmailsPerWindow() <= 0) {
                System.err.println("❌ 邮件配额配置错误");
                return false;
            }
            
            System.out.println("✅ 邮件配额: " + stats.getMaxEmailsPerWindow() 
                              + " 封/" + stats.getWindowMinutes() + " 分钟");
            
            // 3. 发送测试邮件
            if (testClient.canSendEmail()) {
                System.out.println("✅ 可以发送测试邮件");
                return true;
            } else {
                System.err.println("⚠️ 当前窗口邮件配额已用完");
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("❌ 测试环境验证失败: " + e.getMessage());
            return false;
        }
    }
}
```

### 场景3: 自动化测试脚本

```java
public class AutomatedTestRunner {
    
    /**
     * 运行完整的集成测试
     */
    public void runIntegrationTests() {
        System.out.println("=== 开始集成测试 ===");
        
        // 1. 验证环境
        if (!validateTestEnvironment()) {
            System.err.println("测试环境验证失败,终止测试");
            return;
        }
        
        // 2. 获取初始统计
        NotificationStats initialStats = testClient.getNotificationStats();
        int initialCount = initialStats.getEmailsSentInCurrentWindow();
        
        // 3. 执行测试用例
        runTestCase1();
        runTestCase2();
        runTestCase3();
        
        // 4. 验证结果
        NotificationStats finalStats = testClient.getNotificationStats();
        int finalCount = finalStats.getEmailsSentInCurrentWindow();
        int emailsSent = finalCount - initialCount;
        
        System.out.println("=== 测试完成 ===");
        System.out.println("发送邮件数: " + emailsSent);
        System.out.println("剩余配额: " + finalStats.getRemainingEmails());
        
        if (emailsSent > 0) {
            System.out.println("✅ 集成测试通过");
        } else {
            System.err.println("⚠️ 未发送任何邮件,请检查配置");
        }
    }
}
```

---

## 测试规则说明

### 当前配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| **触发条件** | CRITICAL级别告警 | 仅最高等级触发通知 |
| **邮件限制** | 5封/10分钟 | 防止邮件轰炸 |
| **收件人** | kylecui@outlook.com | 测试邮箱 |
| **窗口重置** | 滚动窗口 | 每10分钟重置一次 |

### 配额计算逻辑

```
窗口开始时间: 2025-01-15 10:00:00
窗口长度: 10分钟
窗口结束时间: 2025-01-15 10:10:00

10:01 - 发送第1封 (剩余4封)
10:03 - 发送第2封 (剩余3封)
10:05 - 发送第3封 (剩余2封)
10:07 - 发送第4封 (剩余1封)
10:09 - 发送第5封 (剩余0封) ← 配额用完
10:10 - 窗口重置,配额恢复到5封
```

---

## 相关文档

- **[邮件通知配置](./email_notification_configuration.md)** - 邮件系统配置
- **[告警通知API](./alert_notification_api.md)** - 手动发送邮件
- **[告警概述](./alert_management_overview.md)** - 系统架构

---

**文档结束**

*最后更新: 2025-01-16*

## 8. Java客户端完整示例

### IntegrationTestClient

```java
package com.threatdetection.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 集成测试API客户端
 */
@Slf4j
@Component
public class IntegrationTestClient {
    
    private static final String BASE_URL = "http://localhost:8082/api/v1/integration-test";
    private final RestTemplate restTemplate;
    
    public IntegrationTestClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * 获取通知统计
     */
    public NotificationStats getNotificationStats() {
        String url = BASE_URL + "/stats";
        
        NotificationStats stats = restTemplate.getForObject(url, NotificationStats.class);
        
        log.info("Notification stats: sent={}/{}, remaining={}",
            stats.getEmailsSentInCurrentWindow(),
            stats.getMaxEmailsPerWindow(),
            stats.getRemainingEmails()
        );
        
        return stats;
    }
    
    /**
     * 检查是否还能发送邮件
     */
    public boolean canSendEmail() {
        NotificationStats stats = getNotificationStats();
        return stats.getRemainingEmails() > 0;
    }
    
    /**
     * 获取配额使用率
     */
    public double getQuotaUsage() {
        NotificationStats stats = getNotificationStats();
        return (double) stats.getEmailsSentInCurrentWindow() 
             / stats.getMaxEmailsPerWindow() * 100;
    }
    
    /**
     * 获取测试状态
     */
    public TestStatus getTestStatus() {
        String url = BASE_URL + "/status";
        
        TestStatus status = restTemplate.getForObject(url, TestStatus.class);
        
        log.info("Test status: enabled={}, recipient={}",
            status.getEnabled(),
            status.getEmailRecipient()
        );
        
        return status;
    }
    
    /**
     * 检查集成测试是否已启用
     */
    public boolean isTestingEnabled() {
        TestStatus status = getTestStatus();
        return status.getEnabled();
    }
    
    /**
     * 发送测试邮件
     */
    public TestResult sendTestEmail(String subject, String content) {
        TestEmailRequest request = TestEmailRequest.builder()
            .subject(subject)
            .content(content)
            .testName("api-test")
            .build();
        
        HttpEntity<TestEmailRequest> httpRequest = new HttpEntity<>(request);
        
        ResponseEntity<TestResult> response = restTemplate.postForEntity(
            BASE_URL + "/send-test-email",
            httpRequest,
            TestResult.class
        );
        
        TestResult result = response.getBody();
        
        log.info("Test email sent: success={}, duration={}ms",
            result.getSuccess(),
            result.getDurationMs()
        );
        
        return result;
    }
    
    /**
     * 重置邮件配额 (需要管理员权限)
     */
    public void resetQuota() {
        String url = BASE_URL + "/reset-quota";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAdminToken());
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        restTemplate.postForEntity(url, request, Map.class);
        
        log.info("Email quota reset successfully");
    }
    
    /**
     * 查询发送历史
     */
    public List<EmailHistory> getEmailHistory(int limit) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/history")
            .queryParam("limit", limit)
            .toUriString();
        
        ResponseEntity<List<EmailHistory>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<EmailHistory>>() {}
        );
        
        List<EmailHistory> history = response.getBody();
        
        log.info("Retrieved {} email history records", history.size());
        
        return history;
    }
    
    /**
     * 等待配额重置
     */
    public void waitForQuotaReset() throws InterruptedException {
        NotificationStats stats = getNotificationStats();
        
        if (stats.getRemainingEmails() > 0) {
            log.info("Quota available, no need to wait");
            return;
        }
        
        Instant resetTime = Instant.parse(stats.getNextResetTime());
        Instant now = Instant.now();
        
        long waitSeconds = ChronoUnit.SECONDS.between(now, resetTime);
        
        if (waitSeconds > 0) {
            log.info("Waiting {} seconds for quota reset", waitSeconds);
            Thread.sleep(waitSeconds * 1000 + 1000);  // +1秒缓冲
        }
    }
    
    private String getAdminToken() {
        // 从配置或环境变量获取管理员token
        return System.getenv("ADMIN_TOKEN");
    }
}
```

---

## 9. 最佳实践

### ✅ 推荐做法

#### 9.1 发送前检查配额

```java
// ✅ 正确: 发送前检查配额
public void sendEmailSafely() {
    if (!testClient.canSendEmail()) {
        log.warn("Email quota exhausted, skipping");
        return;
    }
    
    testClient.sendTestEmail("测试", "内容");
}
```

#### 9.2 处理配额耗尽情况

```java
// ✅ 正确: 优雅处理配额耗尽
public void sendWithFallback() {
    if (testClient.canSendEmail()) {
        testClient.sendTestEmail("CRITICAL Alert", "...");
    } else {
        log.warn("Email quota exhausted, logging instead");
        logCriticalAlert();  // 降级为日志记录
    }
}
```

#### 9.3 监控配额使用率

```java
// ✅ 正确: 监控配额使用率
@Scheduled(fixedRate = 60000)  // 每分钟检查
public void monitorQuota() {
    double usage = testClient.getQuotaUsage();
    
    if (usage >= 80.0) {
        log.warn("Email quota usage: {}%, approaching limit", usage);
    }
}
```

---

### ❌ 避免的做法

#### 9.4 避免在循环中发送邮件

```java
// ❌ 错误: 循环发送导致配额快速耗尽
for (Alert alert : alerts) {
    testClient.sendTestEmail(
        "Alert " + alert.getId(),
        alert.getDescription()
    );
}

// ✅ 正确: 批量汇总后发送一封邮件
String summary = alerts.stream()
    .map(Alert::getDescription)
    .collect(Collectors.joining("\n"));

testClient.sendTestEmail("Alert Summary", summary);
```

#### 9.5 避免忽略配额状态

```java
// ❌ 错误: 不检查配额直接发送
testClient.sendTestEmail("Alert", "Content");  // 可能失败

// ✅ 正确: 检查配额并处理失败
TestResult result = testClient.sendTestEmail("Alert", "Content");

if (!result.getSuccess()) {
    log.error("Failed to send email: {}", result.getMessage());
    // 重试或降级处理
}
```

---

## 10. 故障排查

### 10.1 配额耗尽无法发送

**症状**: 返回429错误,提示"Email quota exhausted"

**诊断步骤**:

```bash
# 检查当前配额状态
curl http://localhost:8082/api/v1/integration-test/stats
```

**解决方案**:

```java
// 方案1: 等待窗口重置
testClient.waitForQuotaReset();

// 方案2: 管理员手动重置
testClient.resetQuota();

// 方案3: 降级到日志记录
log.error("Critical alert: {}", alertDetails);
```

---

### 10.2 测试服务未启用

**症状**: 调用API返回404或服务不可用

**诊断步骤**:

```bash
# 检查服务状态
curl http://localhost:8082/api/v1/integration-test/status
```

**解决方案**:

```yaml
# 启用集成测试服务
integration-test:
  enabled: true
```

```bash
# 重启服务
./mvnw spring-boot:run
```

---

### 10.3 邮件发送失败

**症状**: sendTestEmail返回success=false

**诊断步骤**:

```java
TestResult result = testClient.sendTestEmail("Test", "Content");

if (!result.getSuccess()) {
    log.error("Error details: {}", result.getDetails());
}
```

**解决方案**:

```yaml
# 检查SMTP配置
spring:
  mail:
    host: smtp.example.com
    port: 587
    username: your-email@example.com
    password: ${SMTP_PASSWORD}
```

```bash
# 测试SMTP连接
telnet smtp.example.com 587
```

---

## 11. 相关文档

| 文档 | 说明 |
|------|------|
| [邮件通知配置](./email_notification_configuration.md) | SMTP配置详情 |
| [告警通知API](./alert_notification_api.md) | 生产环境通知API |
| [告警CRUD API](./alert_crud_api.md) | 告警基本操作 |
| [告警管理概述](./alert_management_overview.md) | 系统架构概览 |

---

**文档结束**

*最后更新: 2025-10-16*
