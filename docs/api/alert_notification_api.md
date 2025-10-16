# 告警通知API文档

**服务**: Alert Management Service (8082)  
**基础路径**: `/api/v1/alerts`

---

## 手动发送邮件通知

**端点**: `POST /api/v1/alerts/notify/email`

### 功能说明

手动发送邮件通知,不依赖自动触发机制。适用于:
- 测试邮件配置
- 手动补发通知
- 自定义通知内容

### 请求参数

| 字段 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `recipient` | String | ✅ | 收件人邮箱 |
| `subject` | String | ✅ | 邮件主题 |
| `content` | String | ✅ | 邮件内容 |
| `threatId` | Long | ❌ | 关联的告警ID |

### 请求示例 (curl)

```bash
curl -X POST http://localhost:8082/api/v1/alerts/notify/email \
  -H "Content-Type: application/json" \
  -d '{
    "recipient": "security-team@company.com",
    "subject": "测试: 威胁检测系统邮件通知",
    "content": "这是一封测试邮件,用于验证邮件通知功能是否正常工作。",
    "threatId": 12345
  }'
```

### 请求示例 (Java)

```java
public class EmailNotificationExample {
    
    private static final String BASE_URL = "http://localhost:8082/api/v1/alerts";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 发送邮件通知
     */
    public boolean sendEmailNotification(
            String recipient,
            String subject,
            String content,
            Long threatId) {
        
        SendEmailRequest request = new SendEmailRequest();
        request.setRecipient(recipient);
        request.setSubject(subject);
        request.setContent(content);
        request.setThreatId(threatId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<SendEmailRequest> httpRequest = new HttpEntity<>(request, headers);
        
        ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
            BASE_URL + "/notify/email",
            httpRequest,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        
        Map<String, Object> result = response.getBody();
        return (Boolean) result.get("success");
    }
    
    /**
     * 发送CRITICAL告警通知
     */
    public void sendCriticalAlertNotification(Long alertId, String attackIp) {
        String subject = "🚨 CRITICAL: 检测到严重威胁";
        String content = String.format(
            "检测到CRITICAL级别威胁!\n\n" +
            "告警ID: %d\n" +
            "攻击源IP: %s\n" +
            "时间: %s\n\n" +
            "请立即登录系统查看详情并采取措施。",
            alertId,
            attackIp,
            Instant.now().toString()
        );
        
        boolean success = sendEmailNotification(
            "security-team@company.com",
            subject,
            content,
            alertId
        );
        
        if (success) {
            System.out.println("✅ CRITICAL告警邮件已发送");
        } else {
            System.err.println("❌ 邮件发送失败");
        }
    }
    
    // DTO类
    public static class SendEmailRequest {
        private String recipient;
        private String subject;
        private String content;
        private Long threatId;
        
        // Getters and Setters
        public String getRecipient() { return recipient; }
        public void setRecipient(String recipient) { this.recipient = recipient; }
        
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public Long getThreatId() { return threatId; }
        public void setThreatId(Long threatId) { this.threatId = threatId; }
    }
}
```

### 响应示例 (成功)

**HTTP 200 OK**

```json
{
  "success": true,
  "message": "Email notification sent successfully",
  "recipient": "security-team@company.com"
}
```

### 响应示例 (失败)

**HTTP 500 Internal Server Error**

```json
{
  "success": false,
  "message": "Failed to send email notification: SMTP connection timeout"
}
```

### 错误码

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 邮件发送成功 |
| 400 | 请求参数验证失败 |
| 500 | 邮件发送失败 (SMTP错误等) |

---

## 使用场景

### 场景1: 批量发送告警通知

```java
public class BatchNotificationService {
    
    /**
     * 向多个收件人发送同一告警通知
     */
    public void sendToMultipleRecipients(Long alertId, String attackIp, List<String> recipients) {
        String subject = "🚨 威胁检测告警";
        String content = String.format(
            "检测到威胁活动!\n攻击源: %s\n告警ID: %d",
            attackIp,
            alertId
        );
        
        recipients.forEach(recipient -> {
            try {
                emailClient.sendEmailNotification(recipient, subject, content, alertId);
                System.out.println("✅ 已发送至: " + recipient);
            } catch (Exception e) {
                System.err.println("❌ 发送失败: " + recipient + " - " + e.getMessage());
            }
        });
    }
}
```

### 场景2: 测试邮件配置

```java
public class EmailConfigTester {
    
    /**
     * 测试邮件配置是否正常
     */
    public boolean testEmailConfiguration(String testRecipient) {
        String subject = "测试: 威胁检测系统邮件通知";
        String content = "这是一封测试邮件,用于验证SMTP配置。\n" +
                        "如果您收到这封邮件,说明邮件通知功能正常。\n\n" +
                        "发送时间: " + Instant.now();
        
        try {
            boolean success = emailClient.sendEmailNotification(
                testRecipient,
                subject,
                content,
                null
            );
            
            if (success) {
                System.out.println("✅ 邮件配置测试成功!");
                return true;
            } else {
                System.err.println("❌ 邮件发送失败,请检查SMTP配置");
                return false;
            }
        } catch (Exception e) {
            System.err.println("❌ 邮件配置测试失败: " + e.getMessage());
            return false;
        }
    }
}
```

---

## 邮件模板最佳实践

### CRITICAL告警模板

```java
public String generateCriticalAlertEmail(Alert alert) {
    return String.format(
        "🚨 CRITICAL级别威胁告警 🚨\n\n" +
        "===== 告警详情 =====\n" +
        "告警ID: %d\n" +
        "严重等级: %s\n" +
        "威胁评分: %.2f\n" +
        "攻击源MAC: %s\n" +
        "攻击源IP: %s\n" +
        "客户ID: %s\n" +
        "检测时间: %s\n\n" +
        "===== 建议措施 =====\n" +
        "1. 立即隔离攻击源 %s\n" +
        "2. 检查同网段其他主机\n" +
        "3. 审计网络访问日志\n" +
        "4. 启动应急响应流程\n\n" +
        "===== 操作链接 =====\n" +
        "查看详情: http://threat-detection.company.com/alerts/%d\n" +
        "执行隔离: http://threat-detection.company.com/mitigation/%d\n\n" +
        "此邮件由威胁检测系统自动发送,请勿回复。",
        alert.getId(),
        alert.getSeverity(),
        alert.getThreatScore(),
        alert.getAttackMac(),
        alert.getAttackIp(),
        alert.getCustomerId(),
        alert.getCreatedAt(),
        alert.getAttackIp(),
        alert.getId(),
        alert.getId()
    );
}
```

---

## 相关文档

- **[邮件通知配置](./email_notification_configuration.md)** - SMTP配置和动态邮件发送
- **[告警分析API](./alert_analytics_api.md)** - 通知统计信息
- **[告警概述](./alert_management_overview.md)** - 系统架构

---

**文档结束**

*最后更新: 2025-01-16*
