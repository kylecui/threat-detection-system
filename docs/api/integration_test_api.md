# 集成测试API文档

**服务**: Alert Management Service (8082)  
**基础路径**: `/api/v1/integration-test`

---

## API列表

| 方法 | 端点 | 功能 |
|------|------|------|
| `GET` | `/api/v1/integration-test/stats` | 获取通知统计 |
| `GET` | `/api/v1/integration-test/status` | 获取测试状态 |

---

## 获取通知统计

**端点**: `GET /api/v1/integration-test/stats`

### 功能说明

获取集成测试环境的通知发送统计,包括:
- 当前窗口已发送邮件数
- 最大邮件限制
- 剩余配额
- 窗口时间信息

### 请求示例 (curl)

```bash
curl -X GET http://localhost:8082/api/v1/integration-test/stats
```

### 请求示例 (Java)

```java
public class IntegrationTestClient {
    
    private static final String BASE_URL = "http://localhost:8082/api/v1/integration-test";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 获取通知统计
     */
    public NotificationStats getNotificationStats() {
        ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(
            BASE_URL + "/stats",
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        
        Map<String, Object> data = response.getBody();
        
        NotificationStats stats = new NotificationStats();
        stats.setEmailsSentInCurrentWindow((Integer) data.get("emailsSentInCurrentWindow"));
        stats.setMaxEmailsPerWindow((Integer) data.get("maxEmailsPerWindow"));
        stats.setRemainingEmails((Integer) data.get("remainingEmails"));
        stats.setWindowMinutes((Integer) data.get("windowMinutes"));
        stats.setWindowStart((String) data.get("windowStart"));
        stats.setCurrentTime((String) data.get("currentTime"));
        
        return stats;
    }
    
    /**
     * 检查是否还能发送邮件
     */
    public boolean canSendEmail() {
        NotificationStats stats = getNotificationStats();
        return stats.getRemainingEmails() > 0;
    }
    
    // DTO类
    public static class NotificationStats {
        private int emailsSentInCurrentWindow;
        private int maxEmailsPerWindow;
        private int remainingEmails;
        private int windowMinutes;
        private String windowStart;
        private String currentTime;
        
        // Getters and Setters
        public int getEmailsSentInCurrentWindow() { return emailsSentInCurrentWindow; }
        public void setEmailsSentInCurrentWindow(int emailsSentInCurrentWindow) { 
            this.emailsSentInCurrentWindow = emailsSentInCurrentWindow; 
        }
        
        public int getMaxEmailsPerWindow() { return maxEmailsPerWindow; }
        public void setMaxEmailsPerWindow(int maxEmailsPerWindow) { 
            this.maxEmailsPerWindow = maxEmailsPerWindow; 
        }
        
        public int getRemainingEmails() { return remainingEmails; }
        public void setRemainingEmails(int remainingEmails) { 
            this.remainingEmails = remainingEmails; 
        }
        
        public int getWindowMinutes() { return windowMinutes; }
        public void setWindowMinutes(int windowMinutes) { 
            this.windowMinutes = windowMinutes; 
        }
        
        public String getWindowStart() { return windowStart; }
        public void setWindowStart(String windowStart) { 
            this.windowStart = windowStart; 
        }
        
        public String getCurrentTime() { return currentTime; }
        public void setCurrentTime(String currentTime) { 
            this.currentTime = currentTime; 
        }
    }
}
```

### 响应示例

**HTTP 200 OK**

```json
{
  "emailsSentInCurrentWindow": 3,
  "maxEmailsPerWindow": 5,
  "remainingEmails": 2,
  "windowMinutes": 10,
  "windowStart": "2025-01-15T10:00:00Z",
  "currentTime": "2025-01-15T10:05:30Z"
}
```

**字段说明**:

| 字段 | 类型 | 说明 |
|-----|------|------|
| `emailsSentInCurrentWindow` | Integer | 当前窗口已发送邮件数 |
| `maxEmailsPerWindow` | Integer | 窗口内最大邮件数限制 |
| `remainingEmails` | Integer | 剩余可发送邮件数 |
| `windowMinutes` | Integer | 时间窗口长度 (分钟) |
| `windowStart` | String | 当前窗口开始时间 |
| `currentTime` | String | 当前时间 |

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
