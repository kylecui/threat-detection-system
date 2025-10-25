# 告警系统修复总结

**修复日期**: 2025-10-25  
**修复内容**: alert-management 服务 Kafka 消息处理和主题配置  
**问题严重程度**: 🔴 严重 (所有告警内容显示为 null)

---

## 🐛 问题描述

### 现象
用户收到的告警邮件内容全部为 null：
```
subject: 【严重威胁】[null] null 威胁检测
body:
  告警ID: 41
  标题: [null] null 威胁检测
  描述: null
  威胁分数: 1600.00
  来源: null
  时间: 2025-10-24T16:46:10.276324
```

### 根本原因

1. **消息格式不匹配**: 
   - `alert-management` 服务监听 `threat-alerts` 主题
   - 该主题中的消息格式是 **ThreatAlertMessage** (来自 Flink stream-processing)
   - 包含字段: `customerId`, `attackMac`, `threatScore`, `threatLevel`, `attackCount`, `uniqueIps`, `uniquePorts`, `uniqueDevices`, `timestamp`
   
2. **错误的字段提取逻辑**:
   - `KafkaConsumerService.processThreatEvent()` 尝试提取不存在的字段: `eventType`, `source`, `message`, `severity`
   - 这些字段全部返回 `null`
   - 导致生成的告警标题 = `generateAlertTitle(null, null)` = `"[null] null 威胁检测"`

3. **主题命名混淆**:
   - 配置中使用 `app.kafka.topics.threat-events=threat-alerts`
   - 属性名为 `threat-events`,但实际主题名是 `threat-alerts`
   - 虽然配置正确(根据 PROJECT_STANDARDS.md),但缺少说明容易引起混淆

---

## ✅ 修复方案

### 1. 修复 KafkaConsumerService.java

**文件**: `services/alert-management/src/main/java/com/threatdetection/alert/service/KafkaConsumerService.java`

#### 修改点 1: processThreatEvent() 方法

**修复前**:
```java
private void processThreatEvent(Map<String, Object> event) {
    String eventType = (String) event.get("eventType");  // null
    String source = (String) event.get("source");        // null
    String message = (String) event.get("message");      // null
    Integer severity = (Integer) event.get("severity");  // null
    //...
}
```

**修复后**:
```java
private void processThreatEvent(Map<String, Object> event) {
    // 从 threat-alerts 主题接收的标准格式: ThreatAlertMessage
    String customerId = (String) event.get("customerId");
    String attackMac = (String) event.get("attackMac");
    String attackIp = (String) event.get("attackIp");
    String threatLevel = (String) event.get("threatLevel");
    Double threatScore = getDoubleValue(event, "threatScore");
    
    String eventType = "THREAT_DETECTION";
    String source = "stream-processing-service";
    String message = String.format(
        "检测到威胁行为: 攻击源 %s (%s) 在时间窗口内发起 %d 次攻击，" +
        "涉及 %d 个诱饵IP、%d 个端口、%d 个检测设备。威胁等级: %s",
        attackIp, attackMac, attackCount, uniqueIps, uniquePorts, uniqueDevices, threatLevel
    );
    //...
}
```

#### 修改点 2: 添加威胁等级映射方法

```java
private AlertSeverity mapThreatLevelToSeverity(String threatLevel) {
    if (threatLevel == null) return AlertSeverity.MEDIUM;
    
    switch (threatLevel.toUpperCase()) {
        case "CRITICAL": return AlertSeverity.CRITICAL;
        case "HIGH": return AlertSeverity.HIGH;
        case "MEDIUM": return AlertSeverity.MEDIUM;
        case "LOW": return AlertSeverity.LOW;
        case "INFO": return AlertSeverity.INFO;
        default: return AlertSeverity.MEDIUM;
    }
}
```

#### 修改点 3: 添加安全的类型转换方法

```java
private Integer getIntValue(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value instanceof Integer) return (Integer) value;
    if (value instanceof Long) return ((Long) value).intValue();
    if (value instanceof Double) return ((Double) value).intValue();
    return 0;
}

private Double getDoubleValue(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value instanceof Double) return (Double) value;
    if (value instanceof Integer) return ((Integer) value).doubleValue();
    if (value instanceof Long) return ((Long) value).doubleValue();
    return 0.0;
}
```

### 2. 优化配置文件注释

**文件**: `services/alert-management/src/main/resources/application.properties`

**修改前**:
```properties
# Custom Kafka Topics
# Kafka Topics
app.kafka.topics.threat-events=threat-alerts
```

**修改后**:
```properties
# Custom Kafka Topics
# 标准主题: threat-alerts (已锁定)
# 注意: app.kafka.topics.threat-events 是属性名,值是 threat-alerts
app.kafka.topics.threat-events=threat-alerts
```

---

## 📊 修复效果对比

### 数据库记录对比

| 字段 | 修复前 (ID 41-42) | 修复后 (ID 43-45) |
|------|------------------|------------------|
| title | `[null] null 威胁检测` | `[stream-processing-service] CRITICAL 威胁检测 - 00:11:22:33:44:55` |
| description | `null` | `检测到威胁行为: 攻击源 192.168.50.10 (00:11:22:33:44:55) 在时间窗口内发起 150 次攻击，涉及 1 个诱饵IP、1 个端口、1 个检测设备。威胁等级: CRITICAL` |
| source | `null` | `stream-processing-service` |
| event_type | `null` | `THREAT_DETECTION` |
| attack_mac | `null` | `00:11:22:33:44:55` |
| threat_score | `1600.00` | `1650.00` |

### 邮件通知对比

**修复前**:
```
主题: 【严重威胁】[null] null 威胁检测

告警ID: 41
标题: [null] null 威胁检测
描述: null
威胁分数: 1600.00
来源: null
时间: 2025-10-24T16:46:10.276324

请立即处理！
```

**修复后**:
```
主题: 【严重威胁】[stream-processing-service] CRITICAL 威胁检测 - 00:11:22:33:44:55

告警ID: 43
标题: [stream-processing-service] CRITICAL 威胁检测 - 00:11:22:33:44:55
描述: 检测到威胁行为: 攻击源 192.168.50.10 (00:11:22:33:44:55) 在时间窗口内发起 150 次攻击，
      涉及 1 个诱饵IP、1 个端口、1 个检测设备。威胁等级: CRITICAL
威胁分数: 1650.00
来源: stream-processing-service
时间: 2025-10-25T07:21:00.131871

请立即处理！
```

---

## 🔍 相关文档引用

修复过程严格遵循了以下文档规范:

1. **docs/design/PROJECT_STANDARDS.md**
   - Kafka 主题标准: `threat-alerts` 为官方主题
   - `threat-events` 已废弃,统一使用 `threat-alerts`
   
2. **docs/DATA_STRUCTURES_AND_CONNECTIONS.md**
   - threat-alerts 主题的标准消息格式
   - 包含字段: customerId, attackMac, threatScore, threatLevel 等

3. **docs/design/data_structures.md**
   - 威胁评分等级划分: INFO, LOW, MEDIUM, HIGH, CRITICAL
   - AlertSeverity 映射规则

---

## 🚀 部署步骤

```bash
# 1. 重新编译
cd ~/threat-detection-system/services/alert-management
mvn clean package -DskipTests

# 2. 重建容器
cd ~/threat-detection-system/docker
docker compose stop alert-management
docker compose rm -f alert-management
docker compose build --no-cache alert-management
docker compose up -d alert-management

# 3. 验证服务启动
docker logs alert-management-service --tail 20

# 4. 触发测试事件
cd ~/threat-detection-system/scripts
bash test_v4_phase2_dual_dimension.sh

# 5. 验证告警
docker exec postgres psql -U threat_user -d threat_detection \
  -c "SELECT id, title, threat_score, severity FROM alerts ORDER BY created_at DESC LIMIT 5;"
```

---

## ✅ 验证清单

- [x] KafkaConsumerService 正确解析 ThreatAlertMessage 格式
- [x] 告警标题包含来源、威胁等级、攻击MAC
- [x] 告警描述包含攻击详情(攻击源、次数、涉及资源)
- [x] 威胁等级正确映射到 AlertSeverity
- [x] 邮件主题和内容完整
- [x] 攻击MAC、威胁分数正确存储
- [x] 去重机制正常工作
- [x] 所有字段不再为 null

---

## 📝 经验教训

1. **严格遵循文档**: 修复前必须查阅 `docs/` 中的数据结构和API规范
2. **理解数据流**: 清楚每个 Kafka 主题的消息来源和格式
3. **字段命名一致性**: 遵循 PROJECT_STANDARDS.md 的命名规范
4. **类型安全**: 添加类型转换辅助方法,避免 ClassCastException
5. **完整测试**: 端到端验证数据库记录和邮件通知

---

**修复完成时间**: 2025-10-25 15:30 (UTC+8)  
**测试状态**: ✅ 通过  
**生产部署**: 待定
