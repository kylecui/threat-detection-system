# P0 修复清单 - 立即执行

**创建时间**: 2025-10-15  
**预计时间**: 30分钟  
**优先级**: 🔴 P0 (严重 - 阻塞功能)

---

## 📋 修复概览

| 问题 | 影响 | 修复类型 | 文件数 | 预计时间 |
|------|------|---------|--------|---------|
| Kafka主题不匹配 | 邮件通知完全失效 | 配置修改 | 2 | 5分钟 |
| 数据库表缺失 | 告警管理数据丢失风险 | SQL脚本 | 1 | 10分钟 |
| JPA DDL策略错误 | 数据丢失风险 | 配置修改 | 2 | 5分钟 |
| 主题发送不一致 | 架构混乱 | Java代码 | 1 | 10分钟 |

**总计**: 4个P0问题, 6个文件, 30分钟

---

## ✅ 修复任务清单

### 任务1: 修复Kafka主题配置 (告警管理)

**问题描述**:
- alert-management监听 `threat-events` (0条消息)
- stream-processing发送到 `threat-alerts` (266条消息)
- 导致邮件通知完全失效

**影响范围**: 
- 邮件通知 ❌
- SMS通知 ❌
- Slack通知 ❌
- 告警管理功能 ❌

**修复步骤**:

#### 1.1 修改 application.properties

**文件**: `services/alert-management/src/main/resources/application.properties`

**定位**: 第59行附近

**修改前**:
```properties
# Kafka Topics
app.kafka.topics.threat-events=threat-events
```

**修改后**:
```properties
# Kafka Topics
app.kafka.topics.threat-events=threat-alerts
```

**命令**:
```bash
cd /home/kylecui/threat-detection-system
# 备份
cp services/alert-management/src/main/resources/application.properties \
   services/alert-management/src/main/resources/application.properties.backup

# 修改
sed -i 's/app.kafka.topics.threat-events=threat-events/app.kafka.topics.threat-events=threat-alerts/' \
   services/alert-management/src/main/resources/application.properties
```

**验证**:
```bash
grep "app.kafka.topics.threat-events" \
   services/alert-management/src/main/resources/application.properties
# 应输出: app.kafka.topics.threat-events=threat-alerts
```

#### 1.2 修改 application-docker.properties

**文件**: `services/alert-management/src/main/resources/application-docker.properties`

**定位**: 第40行附近

**修改前**:
```properties
app.kafka.topics.threat-events=threat-events
```

**修改后**:
```properties
app.kafka.topics.threat-events=threat-alerts
```

**命令**:
```bash
# 备份
cp services/alert-management/src/main/resources/application-docker.properties \
   services/alert-management/src/main/resources/application-docker.properties.backup

# 修改
sed -i 's/app.kafka.topics.threat-events=threat-events/app.kafka.topics.threat-events=threat-alerts/' \
   services/alert-management/src/main/resources/application-docker.properties
```

**验证**:
```bash
grep "app.kafka.topics.threat-events" \
   services/alert-management/src/main/resources/application-docker.properties
# 应输出: app.kafka.topics.threat-events=threat-alerts
```

**状态**: [ ] 完成

---

### 任务2: 创建缺失的数据库表

**问题描述**:
- `alerts` 表仅靠Hibernate auto-create创建
- `notifications` 表缺失SQL定义
- 使用create-drop模式,每次重启丢失数据

**影响范围**:
- 告警记录丢失风险 ⚠️
- 通知历史丢失风险 ⚠️
- 生产环境不可用 ❌

**修复步骤**:

#### 2.1 创建SQL初始化脚本

**文件**: `docker/04-alert-management-tables.sql` (新建)

**完整内容**:
```sql
-- =====================================================
-- Alert Management Tables
-- 用于告警管理服务的数据持久化
-- 创建时间: 2025-10-15
-- =====================================================

-- 告警管理表 (Alert Management)
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    attack_mac VARCHAR(17),
    threat_score DECIMAL(12,2),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    
    -- 索引
    CONSTRAINT chk_severity CHECK (severity IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED', 'SUPPRESSED'))
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_alerts_customer_id ON alerts(customer_id);
CREATE INDEX IF NOT EXISTS idx_alerts_created_at ON alerts(created_at);
CREATE INDEX IF NOT EXISTS idx_alerts_severity ON alerts(severity);
CREATE INDEX IF NOT EXISTS idx_alerts_status ON alerts(status);
CREATE INDEX IF NOT EXISTS idx_alerts_attack_mac ON alerts(customer_id, attack_mac);

-- 通知记录表 (Notification Records)
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    alert_id BIGINT REFERENCES alerts(id) ON DELETE CASCADE,
    customer_id VARCHAR(100) NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 索引和约束
    CONSTRAINT chk_notification_type CHECK (notification_type IN ('EMAIL', 'SMS', 'SLACK', 'WEBHOOK')),
    CONSTRAINT chk_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'RETRYING'))
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_notifications_alert_id ON notifications(alert_id);
CREATE INDEX IF NOT EXISTS idx_notifications_customer_id ON notifications(customer_id);
CREATE INDEX IF NOT EXISTS idx_notifications_status ON notifications(status);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at);

-- 添加注释
COMMENT ON TABLE alerts IS '告警管理表 - 存储告警生命周期管理信息';
COMMENT ON TABLE notifications IS '通知记录表 - 存储邮件/短信/Slack通知发送记录';

COMMENT ON COLUMN alerts.customer_id IS '客户ID - 多租户隔离字段';
COMMENT ON COLUMN alerts.alert_type IS '告警类型 - 如THREAT_ALERT, DEVICE_HEALTH等';
COMMENT ON COLUMN alerts.severity IS '严重程度 - INFO/LOW/MEDIUM/HIGH/CRITICAL';
COMMENT ON COLUMN alerts.status IS '告警状态 - ACTIVE/ACKNOWLEDGED/RESOLVED/SUPPRESSED';

COMMENT ON COLUMN notifications.notification_type IS '通知类型 - EMAIL/SMS/SLACK/WEBHOOK';
COMMENT ON COLUMN notifications.status IS '发送状态 - PENDING/SENT/FAILED/RETRYING';
COMMENT ON COLUMN notifications.retry_count IS '重试次数 - 失败后的重试计数';

-- 数据完整性检查
DO $$
BEGIN
    RAISE NOTICE 'Alert Management Tables Created Successfully';
    RAISE NOTICE 'Tables: alerts, notifications';
    RAISE NOTICE 'Indexes: 9 indexes total';
END $$;
```

**创建命令**:
```bash
cd /home/kylecui/threat-detection-system/docker
cat > 04-alert-management-tables.sql << 'EOF'
[粘贴上述SQL内容]
EOF
```

**验证**:
```bash
wc -l docker/04-alert-management-tables.sql
# 应输出: 77 docker/04-alert-management-tables.sql

grep "CREATE TABLE IF NOT EXISTS" docker/04-alert-management-tables.sql
# 应输出2行: alerts, notifications
```

#### 2.2 更新Docker Compose配置

**文件**: `docker/docker-compose.yml`

**定位**: postgres服务的volumes部分 (第28行附近)

**修改前**:
```yaml
volumes:
  - postgres_data:/var/lib/postgresql/data
  - ./01-init-db.sql:/docker-entrypoint-initdb.d/01-init-db.sql
  - ./02-attack-events-storage.sql:/docker-entrypoint-initdb.d/02-attack-events-storage.sql
  - ./03-port-weights.sql:/docker-entrypoint-initdb.d/03-port-weights.sql
```

**修改后**:
```yaml
volumes:
  - postgres_data:/var/lib/postgresql/data
  - ./01-init-db.sql:/docker-entrypoint-initdb.d/01-init-db.sql
  - ./02-attack-events-storage.sql:/docker-entrypoint-initdb.d/02-attack-events-storage.sql
  - ./03-port-weights.sql:/docker-entrypoint-initdb.d/03-port-weights.sql
  - ./04-alert-management-tables.sql:/docker-entrypoint-initdb.d/04-alert-management-tables.sql
```

**命令**:
```bash
cd /home/kylecui/threat-detection-system

# 备份
cp docker/docker-compose.yml docker/docker-compose.yml.backup

# 在03-port-weights.sql行后添加新行
sed -i '/03-port-weights.sql:/a\      - ./04-alert-management-tables.sql:/docker-entrypoint-initdb.d/04-alert-management-tables.sql' \
   docker/docker-compose.yml
```

**验证**:
```bash
grep "04-alert-management-tables.sql" docker/docker-compose.yml
# 应输出: - ./04-alert-management-tables.sql:/docker-entrypoint-initdb.d/04-alert-management-tables.sql
```

**状态**: [ ] 完成

---

### 任务3: 修复JPA DDL策略

**问题描述**:
- alert-management使用 `create-drop` 模式
- 每次重启会删除alerts和notifications表
- 导致历史数据丢失

**影响范围**:
- 告警历史丢失 ❌
- 通知记录丢失 ❌
- 审计追踪中断 ❌

**修复步骤**:

#### 3.1 修改 application.properties

**文件**: `services/alert-management/src/main/resources/application.properties`

**定位**: 第30行附近

**修改前**:
```properties
spring.jpa.hibernate.ddl-auto=create-drop
```

**修改后**:
```properties
spring.jpa.hibernate.ddl-auto=validate
```

**命令**:
```bash
sed -i 's/spring.jpa.hibernate.ddl-auto=create-drop/spring.jpa.hibernate.ddl-auto=validate/' \
   services/alert-management/src/main/resources/application.properties
```

**验证**:
```bash
grep "spring.jpa.hibernate.ddl-auto" \
   services/alert-management/src/main/resources/application.properties
# 应输出: spring.jpa.hibernate.ddl-auto=validate
```

#### 3.2 修改 application-docker.properties

**文件**: `services/alert-management/src/main/resources/application-docker.properties`

**定位**: 第26行附近

**修改前**:
```properties
spring.jpa.hibernate.ddl-auto=create-drop
```

**修改后**:
```properties
spring.jpa.hibernate.ddl-auto=validate
```

**命令**:
```bash
sed -i 's/spring.jpa.hibernate.ddl-auto=create-drop/spring.jpa.hibernate.ddl-auto=validate/' \
   services/alert-management/src/main/resources/application-docker.properties
```

**验证**:
```bash
grep "spring.jpa.hibernate.ddl-auto" \
   services/alert-management/src/main/resources/application-docker.properties
# 应输出: spring.jpa.hibernate.ddl-auto=validate
```

**状态**: [ ] 完成

---

### 任务4: 统一Threat Assessment主题发送

**问题描述**:
- threat-assessment发送到 `threat-events` (hardcoded)
- stream-processing发送到 `threat-alerts`
- 造成架构混乱,双重主题并存

**影响范围**:
- 架构不一致 ⚠️
- 维护困难 ⚠️

**修复步骤**:

#### 4.1 修改 RiskAssessmentService.java

**文件**: `services/threat-assessment/src/main/java/com/threatdetection/threatassessment/service/RiskAssessmentService.java`

**定位**: 第183行附近

**修改前**:
```java
kafkaTemplate.send("threat-events", customerId, threatAlert)
    .addCallback(
        result -> log.info("Sent threat alert to Kafka: customerId={}, attackMac={}", 
                          customerId, attackMac),
        ex -> log.error("Failed to send threat alert: customerId={}", customerId, ex)
    );
```

**修改后**:
```java
kafkaTemplate.send("threat-alerts", customerId, threatAlert)
    .addCallback(
        result -> log.info("Sent threat alert to Kafka: customerId={}, attackMac={}", 
                          customerId, attackMac),
        ex -> log.error("Failed to send threat alert: customerId={}", customerId, ex)
    );
```

**详细修改**:
```bash
cd /home/kylecui/threat-detection-system

# 备份
cp services/threat-assessment/src/main/java/com/threatdetection/threatassessment/service/RiskAssessmentService.java \
   services/threat-assessment/src/main/java/com/threatdetection/threatassessment/service/RiskAssessmentService.java.backup
```

**手动修改** (使用编辑器):
1. 打开 `services/threat-assessment/src/main/java/com/threatdetection/threatassessment/service/RiskAssessmentService.java`
2. 搜索 `kafkaTemplate.send("threat-events"`
3. 替换为 `kafkaTemplate.send("threat-alerts"`
4. 保存

**或使用sed**:
```bash
sed -i 's/kafkaTemplate.send("threat-events"/kafkaTemplate.send("threat-alerts"/' \
   services/threat-assessment/src/main/java/com/threatdetection/threatassessment/service/RiskAssessmentService.java
```

**验证**:
```bash
grep 'kafkaTemplate.send("threat-' \
   services/threat-assessment/src/main/java/com/threatdetection/threatassessment/service/RiskAssessmentService.java
# 应输出: kafkaTemplate.send("threat-alerts", customerId, threatAlert)
```

**状态**: [ ] 完成

---

## 🔧 修复执行命令汇总

**一键执行所有修复** (谨慎使用):

```bash
#!/bin/bash
set -e

cd /home/kylecui/threat-detection-system

echo "开始P0修复..."

# 任务1: Kafka主题配置
echo "[1/4] 修复Kafka主题配置..."
cp services/alert-management/src/main/resources/application.properties \
   services/alert-management/src/main/resources/application.properties.backup
sed -i 's/app.kafka.topics.threat-events=threat-events/app.kafka.topics.threat-events=threat-alerts/' \
   services/alert-management/src/main/resources/application.properties

cp services/alert-management/src/main/resources/application-docker.properties \
   services/alert-management/src/main/resources/application-docker.properties.backup
sed -i 's/app.kafka.topics.threat-events=threat-events/app.kafka.topics.threat-events=threat-alerts/' \
   services/alert-management/src/main/resources/application-docker.properties

# 任务2: 创建数据库表SQL
echo "[2/4] 创建数据库表SQL..."
cat > docker/04-alert-management-tables.sql << 'EOF'
[粘贴完整SQL内容]
EOF

cp docker/docker-compose.yml docker/docker-compose.yml.backup
sed -i '/03-port-weights.sql:/a\      - ./04-alert-management-tables.sql:/docker-entrypoint-initdb.d/04-alert-management-tables.sql' \
   docker/docker-compose.yml

# 任务3: JPA DDL策略
echo "[3/4] 修复JPA DDL策略..."
sed -i 's/spring.jpa.hibernate.ddl-auto=create-drop/spring.jpa.hibernate.ddl-auto=validate/' \
   services/alert-management/src/main/resources/application.properties
sed -i 's/spring.jpa.hibernate.ddl-auto=create-drop/spring.jpa.hibernate.ddl-auto=validate/' \
   services/alert-management/src/main/resources/application-docker.properties

# 任务4: Threat Assessment主题
echo "[4/4] 统一Kafka主题..."
cp services/threat-assessment/src/main/java/com/threatdetection/threatassessment/service/RiskAssessmentService.java \
   services/threat-assessment/src/main/java/com/threatdetection/threatassessment/service/RiskAssessmentService.java.backup
sed -i 's/kafkaTemplate.send("threat-events"/kafkaTemplate.send("threat-alerts"/' \
   services/threat-assessment/src/main/java/com/threatdetection/threatassessment/service/RiskAssessmentService.java

echo "✅ P0修复完成!"
echo ""
echo "请执行验证步骤:"
echo "1. docker-compose down -v"
echo "2. docker-compose up -d"
echo "3. python3 scripts/test/full_integration_test.py"
```

**保存为脚本**:
```bash
cat > /home/kylecui/threat-detection-system/scripts/apply_p0_fixes.sh << 'SCRIPT'
[粘贴上述脚本内容]
SCRIPT

chmod +x /home/kylecui/threat-detection-system/scripts/apply_p0_fixes.sh
```

---

## ✅ 验证步骤

### 验证1: 代码修改确认

```bash
cd /home/kylecui/threat-detection-system

# 检查所有修改
echo "=== Kafka主题配置 ==="
grep "app.kafka.topics.threat-events" \
  services/alert-management/src/main/resources/application.properties \
  services/alert-management/src/main/resources/application-docker.properties

echo -e "\n=== JPA DDL策略 ==="
grep "spring.jpa.hibernate.ddl-auto" \
  services/alert-management/src/main/resources/application.properties \
  services/alert-management/src/main/resources/application-docker.properties

echo -e "\n=== Threat Assessment主题 ==="
grep 'kafkaTemplate.send("threat-' \
  services/threat-assessment/src/main/java/com/threatdetection/threatassessment/service/RiskAssessmentService.java

echo -e "\n=== Docker Compose SQL挂载 ==="
grep "alert-management-tables.sql" docker/docker-compose.yml

echo -e "\n=== SQL文件存在性 ==="
ls -lh docker/04-alert-management-tables.sql
```

**预期输出**:
```
=== Kafka主题配置 ===
application.properties:app.kafka.topics.threat-events=threat-alerts
application-docker.properties:app.kafka.topics.threat-events=threat-alerts

=== JPA DDL策略 ===
application.properties:spring.jpa.hibernate.ddl-auto=validate
application-docker.properties:spring.jpa.hibernate.ddl-auto=validate

=== Threat Assessment主题 ===
kafkaTemplate.send("threat-alerts", customerId, threatAlert)

=== Docker Compose SQL挂载 ===
      - ./04-alert-management-tables.sql:/docker-entrypoint-initdb.d/04-alert-management-tables.sql

=== SQL文件存在性 ===
-rw-r--r-- 1 user group 3.2K ... docker/04-alert-management-tables.sql
```

### 验证2: 编译检查

```bash
cd /home/kylecui/threat-detection-system

# 编译 alert-management
cd services/alert-management
mvn clean compile
echo "Alert Management编译状态: $?"

# 编译 threat-assessment
cd ../threat-assessment
mvn clean compile
echo "Threat Assessment编译状态: $?"
```

**预期**: 退出码为0 (编译成功)

### 验证3: 数据库初始化测试

```bash
cd /home/kylecui/threat-detection-system

# 完全清理
docker-compose down -v

# 重新启动
docker-compose up -d postgres

# 等待初始化
sleep 10

# 检查表创建
docker exec postgres psql -U threat_user -d threat_detection -c "\dt" | grep -E "alerts|notifications"
```

**预期输出**:
```
 public | alerts         | table | threat_user
 public | notifications  | table | threat_user
```

### 验证4: 完整集成测试

```bash
cd /home/kylecui/threat-detection-system

# 完全清理
docker-compose down -v

# 启动所有服务
docker-compose up -d

# 等待服务就绪
sleep 60

# 运行集成测试
python3 scripts/test/full_integration_test.py
```

**预期结果**:
- ✅ 发送 1000 条日志 (成功率 > 99%)
- ✅ 生成 200+ 威胁告警
- ✅ alert-management日志显示: `partitions assigned: [threat-alerts-0]`
- ✅ PostgreSQL `alerts` 表有 200+ 条记录
- ✅ PostgreSQL `notifications` 表有 200+ 条记录
- ✅ 收到邮件通知 (kylecui@outlook.com)

### 验证5: 数据库记录检查

```bash
# 检查alerts表
docker exec postgres psql -U threat_user -d threat_detection -c \
  "SELECT COUNT(*) FROM alerts;"

# 检查notifications表
docker exec postgres psql -U threat_user -d threat_detection -c \
  "SELECT COUNT(*), status FROM notifications GROUP BY status;"

# 检查threat_alerts表 (对比)
docker exec postgres psql -U threat_user -d threat_detection -c \
  "SELECT COUNT(*) FROM threat_alerts;"
```

**预期**:
- `alerts` 表: 200+ 条记录
- `notifications` 表: 200+ 条记录 (status='SENT')
- `threat_alerts` 表: 200+ 条记录 (应与alerts数量一致)

---

## 🚨 回滚计划

**如果修复失败,执行回滚**:

```bash
cd /home/kylecui/threat-detection-system

# 恢复所有备份文件
cp services/alert-management/src/main/resources/application.properties.backup \
   services/alert-management/src/main/resources/application.properties

cp services/alert-management/src/main/resources/application-docker.properties.backup \
   services/alert-management/src/main/resources/application-docker.properties

cp services/threat-assessment/src/main/java/com/threatdetection/threatassessment/service/RiskAssessmentService.java.backup \
   services/threat-assessment/src/main/java/com/threatdetection/threatassessment/service/RiskAssessmentService.java

cp docker/docker-compose.yml.backup docker/docker-compose.yml

# 删除新增SQL文件
rm -f docker/04-alert-management-tables.sql

# 重启服务
docker-compose down -v
docker-compose up -d

echo "✅ 回滚完成,系统恢复到修复前状态"
```

---

## 📊 预期改进效果

### 修复前

| 指标 | 值 | 状态 |
|------|-----|------|
| 威胁告警生成 | 266 | ✅ 正常 |
| alert-management消费 | 0 | ❌ 失败 |
| 邮件发送 | 0 | ❌ 失败 |
| alerts表记录 | 0 | ❌ 空表 |
| notifications表 | 不存在 | ❌ 缺失 |

### 修复后

| 指标 | 预期值 | 状态 |
|------|--------|------|
| 威胁告警生成 | 266 | ✅ 正常 |
| alert-management消费 | 266 | ✅ 修复 |
| 邮件发送 | 266 | ✅ 修复 |
| alerts表记录 | 266 | ✅ 修复 |
| notifications表记录 | 266 | ✅ 修复 |

**修复率**: 100% (5/5项指标恢复正常)

---

## 📝 注意事项

1. **数据丢失风险**: 本次修复会清空Docker卷 (`docker-compose down -v`),请确认测试环境
2. **编译时间**: threat-assessment服务首次编译可能需要5-10分钟
3. **邮件配置**: 确认SMTP服务器可用 (smtp.163.com:587)
4. **网络依赖**: 需要访问Docker Hub和Maven Central
5. **磁盘空间**: 确保至少有2GB可用空间

---

## ✅ 完成确认

**修复完成后,请在此签名**:

- [ ] 任务1完成: Kafka主题配置修复
- [ ] 任务2完成: 数据库表SQL创建
- [ ] 任务3完成: JPA DDL策略修复
- [ ] 任务4完成: Threat Assessment主题统一
- [ ] 验证1通过: 代码修改确认
- [ ] 验证2通过: 编译检查
- [ ] 验证3通过: 数据库初始化
- [ ] 验证4通过: 完整集成测试
- [ ] 验证5通过: 数据记录检查
- [ ] 邮件通知收到: kylecui@outlook.com

**签名**: _____________  
**日期**: _____________  
**耗时**: _________ 分钟

---

**文档版本**: 1.0  
**最后更新**: 2025-10-15  
**下一步**: 执行 P1/P2 修复 (参见 PROJECT_INCONSISTENCIES_ANALYSIS.md)
