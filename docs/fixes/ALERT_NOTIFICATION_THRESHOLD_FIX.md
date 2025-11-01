# 告警通知阈值修复报告

**日期**: 2025-10-25  
**修复人**: AI Assistant  
**问题编号**: Alert Notification Threshold Mismatch

---

## 问题描述

### 用户报告
用户发现系统生成了6个CRITICAL级别的告警，但只收到了3封邮件通知：

**生成的告警** (6个):
```
ID 43: 00:11:22:33:44:55, score=1650, severity=CRITICAL   ✅ 收到邮件
ID 44: AA:BB:CC:DD:EE:FF, score=344.85, severity=MEDIUM   ❌ 未收到邮件
ID 45: 11:22:33:44:55:66, score=880, severity=HIGH        ❌ 未收到邮件
ID 46: 00:11:22:33:44:55, score=1650, severity=CRITICAL   ✅ 收到邮件
ID 47: 11:22:33:44:55:66, score=880, severity=HIGH        ❌ 未收到邮件
ID 48: AA:BB:CC:DD:EE:FF, score=1254, severity=CRITICAL   ✅ 收到邮件
```

**收到的邮件** (3封):
- ID 43, 46, 48 (severity=CRITICAL)

**未收到的邮件** (3个):
- ID 44 (severity=MEDIUM)
- ID 45, 47 (severity=HIGH)

### 矛盾之处
所有告警的**描述中**都标记为 `威胁等级: CRITICAL`，但**数据库 severity 字段**却不同。

---

## 根本原因分析

### 1. 数据流追踪

```
stream-processing (TierWindowProcessor)
  ↓ 计算 threatScore 和 threatLevel
  ↓ 发送 Kafka 消息: {"threatLevel": "CRITICAL", "threatScore": 344.85}
  ↓
threat-alerts Kafka Topic
  ↓
alert-management (KafkaConsumerService)
  ↓ 从 Kafka 消息提取 threatLevel="CRITICAL"
  ↓ 调用 mapThreatLevelToSeverity(threatLevel) → AlertSeverity.CRITICAL
  ↓ 创建 Alert 对象: severity=CRITICAL, threatScore=344.85
  ↓
alert-management (AlertService.createAlert)
  ↓ ❌ 重新计算: AlertSeverity.fromScore(344.85) → MEDIUM
  ↓ 覆盖之前的 severity 值！
  ↓
保存到数据库: severity=MEDIUM (与 threatLevel="CRITICAL" 不一致)
```

### 2. 阈值不一致问题

**ThreatLevel 阈值** (stream-processing/TierWindowProcessor):
```java
private String determineThreatLevel(double score) {
    if (score >= 200) return "CRITICAL";   // ← 200 阈值
    if (score >= 100) return "HIGH";
    if (score >= 50) return "MEDIUM";
    if (score >= 10) return "LOW";
    return "INFO";
}
```

**AlertSeverity 阈值** (alert-management/AlertSeverity - 修复前):
```java
public enum AlertSeverity {
    CRITICAL("严重", 1000),  // ← 1000 阈值 (5倍差异!)
    HIGH("高危", 500),
    MEDIUM("中危", 100),
    LOW("低危", 10),
    INFO("信息", 0);
}
```

### 3. 问题影响

| 告警 | threatScore | ThreatLevel (stream) | AlertSeverity (before) | 邮件发送 |
|------|------------|---------------------|----------------------|---------|
| ID 43 | 1650 | CRITICAL (≥200) | CRITICAL (≥1000) ✅ | ✅ 发送 |
| ID 44 | 344.85 | CRITICAL (≥200) | MEDIUM (100-999) ❌ | ❌ 未发送 |
| ID 45 | 880 | CRITICAL (≥200) | HIGH (500-999) ❌ | ❌ 未发送 |

**结论**: 分数在 200-999 之间的威胁被正确识别为 CRITICAL，但由于阈值不一致被降级为 MEDIUM/HIGH，导致**邮件未发送**。

---

## 解决方案

### 修改 1: 调整 AlertSeverity 阈值

**文件**: `services/alert-management/src/main/java/com/threatdetection/alert/model/AlertSeverity.java`

```java
public enum AlertSeverity {
    CRITICAL("严重", 200),   // 与ThreatLevel阈值对齐: score >= 200
    HIGH("高危", 100),       // 与ThreatLevel阈值对齐: 100 <= score < 200
    MEDIUM("中危", 50),      // 与ThreatLevel阈值对齐: 50 <= score < 100
    LOW("低危", 10),         // 与ThreatLevel阈值对齐: 10 <= score < 50
    INFO("信息", 0);         // 与ThreatLevel阈值对齐: score < 10
}
```

**变更说明**:
- CRITICAL: 1000 → **200** (降低5倍)
- HIGH: 500 → **100** (降低5倍)
- MEDIUM: 100 → **50** (降低2倍)
- LOW 和 INFO: 保持不变

### 修改 2: 优化 AlertService 重计算逻辑

**文件**: `services/alert-management/src/main/java/com/threatdetection/alert/service/alert/AlertService.java`

```java
// 修改前 (总是重新计算):
if (alert.getThreatScore() != null) {
    alert.setSeverity(AlertSeverity.fromScore(alert.getThreatScore()));
}

// 修改后 (仅在未设置时计算):
// 注意: severity已在KafkaConsumerService中从threatLevel正确映射，不再重新计算
// 如果未设置severity但有威胁分数，则作为fallback自动确定严重程度
if (alert.getSeverity() == null && alert.getThreatScore() != null) {
    alert.setSeverity(AlertSeverity.fromScore(alert.getThreatScore()));
    logger.warn("Severity not set, calculated from score: {}", alert.getSeverity());
}
```

**变更说明**:
- 保留 `KafkaConsumerService` 中从 `threatLevel` 映射的 `severity` 值
- 只在 `severity` 为 null 时才使用 `fromScore()` 计算 (fallback 机制)
- 避免覆盖已正确设置的 severity

---

## 验证结果

### 测试环境
- 时间: 2025-10-25 07:57
- 测试脚本: `test_v4_phase2_dual_dimension.sh`
- 事件数量: 350 (150 + 120 + 80)

### 修复后的告警

| ID | attackMac | threatScore | ThreatLevel | Severity (After) | 邮件通知 |
|----|-----------|------------|-------------|-----------------|---------|
| 49 | 00:11:22:33:44:55 | 1650 | CRITICAL | **CRITICAL** ✅ | ✅ 已发送 |
| 50 | 11:22:33:44:55:66 | 880 | CRITICAL | **CRITICAL** ✅ | ✅ 已发送 |
| 51 | AA:BB:CC:DD:EE:FF | 1254 | CRITICAL | **CRITICAL** ✅ | ✅ 已发送 |

### 对比总结

| 测试轮次 | 告警数 | CRITICAL数 | 邮件发送数 | 问题 |
|---------|-------|-----------|-----------|-----|
| **修复前** | 6 | 6 (威胁等级) | 3 | ❌ severity 阈值不一致 |
| **修复前** | 6 | 3 (severity) | 3 | ❌ 漏发 3 封邮件 |
| **修复后** | 3 | 3 (一致) | 3 | ✅ 完全正常 |

---

## 技术债务与改进

### 1. 系统一致性保证
✅ **已完成**: 统一威胁等级阈值定义
- ThreatLevel (stream-processing): 200/100/50/10/0
- AlertSeverity (alert-management): 200/100/50/10/0

### 2. 数据流完整性
✅ **已完成**: 避免数据覆盖
- 保留 Kafka 消息中的 `threatLevel` 映射结果
- 仅在必要时使用 `fromScore()` fallback

### 3. 配置集中化 (待改进)
⚠️ **建议**: 将威胁等级阈值提取到配置文件
```yaml
threat-scoring:
  thresholds:
    critical: 200
    high: 100
    medium: 50
    low: 10
```

### 4. 单元测试覆盖 (待改进)
⚠️ **建议**: 添加集成测试
- 测试场景: score=199 → MEDIUM, score=200 → CRITICAL
- 验证邮件发送触发条件

---

## 部署步骤

```bash
# 1. 重新编译 alert-management 服务
cd ~/threat-detection-system/services/alert-management
mvn clean package -DskipTests

# 2. 重新构建 Docker 镜像
cd ~/threat-detection-system/docker
docker compose stop alert-management
docker compose rm -f alert-management
docker compose build --no-cache alert-management
docker compose up -d alert-management

# 3. 验证服务启动
docker logs alert-management-service --tail 20

# 4. 运行集成测试
cd ~/threat-detection-system/scripts
bash test_v4_phase2_dual_dimension.sh
```

---

## 总结

### 问题根源
1. **阈值不一致**: AlertSeverity 使用 1000/500/100，ThreatLevel 使用 200/100/50
2. **数据覆盖**: AlertService 总是重新计算 severity，覆盖 Kafka 消息中的正确映射

### 修复效果
1. ✅ 统一阈值定义 (200/100/50/10/0)
2. ✅ 保留 threatLevel 映射结果
3. ✅ 所有 CRITICAL 告警正确发送邮件
4. ✅ 系统行为与威胁评分标准一致

### 影响范围
- **服务**: alert-management
- **修改文件**: 2 个 (AlertSeverity.java, AlertService.java)
- **兼容性**: ✅ 向后兼容 (只影响新告警)
- **现有告警**: 历史告警不受影响

**修复状态**: ✅ 已验证通过  
**上线时间**: 2025-10-25 07:57  
**回归风险**: 低 (仅调整阈值和去除数据覆盖)
