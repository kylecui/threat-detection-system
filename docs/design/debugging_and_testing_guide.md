# 调试与测试指南

**项目**: Cloud-Native Threat Detection System  
**版本**: 1.0  
**创建日期**: 2025-10-15  
**目的**: 记录开发过程中的常见问题、解决方案和最佳实践

---

## 📋 目录

1. [Docker容器调试](#docker容器调试)
2. [数据库持久化调试记录](#数据库持久化调试记录)
3. [E2E测试执行](#e2e测试执行)
4. [常见陷阱与解决方案](#常见陷阱与解决方案)
5. [快速参考命令](#快速参考命令)
6. [故障排查清单](#故障排查清单)

---

## 🐳 Docker容器调试

### 关键原则

**⚠️ 代码修改后必须重新构建容器镜像!**

```bash
# ❌ 错误做法 - 只重启容器不会加载新代码
docker compose restart data-ingestion

# ✅ 正确做法 - 完整重新构建流程
cd /home/kylecui/threat-detection-system

# 1. Maven编译 (确保最新代码编译)
mvn clean package -DskipTests

# 2. 重新构建Docker镜像 (使用--no-cache避免缓存问题)
cd docker
docker compose build data-ingestion --no-cache

# 3. 重启容器
docker compose up -d data-ingestion

# 4. 验证新代码已加载 (检查日志时间戳)
docker logs data-ingestion-service --since 1m
```

### 容器重建触发场景

| 场景 | 是否需要重建 | 命令 |
|------|------------|------|
| 修改Java代码 | ✅ 必须 | `mvn clean package` + `docker compose build --no-cache` |
| 修改配置文件 (application.yml) | ✅ 必须 | 同上 |
| 修改pom.xml依赖 | ✅ 必须 | 同上 |
| 修改环境变量 | ⚠️ 重启即可 | `docker compose up -d` |
| 修改docker-compose.yml | ⚠️ 重启即可 | `docker compose up -d` |
| 修改数据库schema | ❌ 不需要 | 直接执行SQL |

### Docker缓存陷阱

**问题**: 使用`docker compose build`可能使用缓存,导致新代码未被包含

```bash
# 症状: 修改代码后容器行为未改变
# 原因: Docker使用了旧的layer缓存

# ✅ 解决方案: 使用--no-cache强制重新构建
docker compose build data-ingestion --no-cache
docker compose build stream-processing --no-cache

# 或清理所有构建缓存
docker builder prune -a
```

### 配置文件优先级问题

**问题**: `application.properties` 会覆盖 `application-docker.yml`

```yaml
# ❌ 错误: application.properties 中的配置会覆盖Docker配置
# services/data-ingestion/.../resources/application.properties
spring.kafka.bootstrap-servers=localhost:9092  # 这个会覆盖docker配置!

# ✅ 解决方案1: 删除或注释application.properties中的Docker特定配置
# spring.kafka.bootstrap-servers=localhost:9092  # 已注释

# ✅ 解决方案2: 在application-docker.yml中显式覆盖
# services/data-ingestion/.../resources/application-docker.yml
spring:
  kafka:
    bootstrap-servers: kafka:29092  # 明确设置Docker环境值
    consumer:
      bootstrap-servers: kafka:29092  # 再次确认
```

**实际案例**: 2025-10-15修复的Kafka消费者未启动问题
- **症状**: Kafka consumer无法连接,日志显示`Connection refused`
- **根因**: `application.properties`中的`localhost:9092`覆盖了Docker配置
- **修复**: 在`application-docker.yml`中明确设置所有Kafka配置

---

## 💾 数据库持久化调试记录

### PostgreSQL JSONB类型映射问题

**问题**: Hibernate无法将String字段映射到PostgreSQL的JSONB列

```java
// ❌ 错误: 缺少类型转换注解
@Column(name = "raw_log_data", columnDefinition = "jsonb")
private String rawLogData;

// 错误信息:
// ERROR: column "raw_log_data" is of type jsonb but expression is of type character varying
```

**解决方案**: 使用Hibernate 6的`@JdbcTypeCode`注解

```java
// ✅ 正确: 添加JSONB类型转换
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Column(name = "raw_log_data", columnDefinition = "jsonb")
@JdbcTypeCode(SqlTypes.JSON)  // ← 关键修复
private String rawLogData;
```

**适用实体**:
- `AttackEventEntity.rawLogData`
- `ThreatAlertEntity.rawAlertData`

### 数据库连接验证

```bash
# 进入PostgreSQL容器
docker exec -it postgres bash

# 连接数据库 (注意用户名是threat_user而非postgres)
psql -U threat_user -d threat_detection

# 常用查询
\dt                          # 列出所有表
\d attack_events            # 查看表结构
SELECT COUNT(*) FROM attack_events;
SELECT COUNT(*) FROM threat_alerts;

# 查看JSONB数据
SELECT attack_mac, raw_log_data FROM attack_events LIMIT 3;

# 清空测试数据
TRUNCATE TABLE attack_events CASCADE;
TRUNCATE TABLE threat_alerts CASCADE;
```

### PostgreSQL初始化问题

**问题**: 重复端口配置导致初始化失败

```sql
-- ❌ 错误: port_weights_migration.sql中有两个443端口
INSERT INTO port_risk_configs (port_number, ...) VALUES (443, ...);  -- 第1次
...
INSERT INTO port_risk_configs (port_number, ...) VALUES (443, ...);  -- 第137行重复!

-- 错误信息:
-- ERROR: duplicate key value violates unique constraint "port_risk_configs_pkey"
```

**解决方案**:
```bash
# 删除重复行
sed -i '137d' docker/03-port-weights-migration.sql

# 或手动编辑文件删除重复的INSERT语句
```

---

## 🧪 E2E测试执行

### 测试脚本路径

**⚠️ 正确的测试脚本是 `e2e_mvp_test.py` 而非 `e2e_test.py`!**

```bash
# ✅ 正确
python3 scripts/test/e2e_mvp_test.py

# ❌ 错误 (不存在)
python3 scripts/test/e2e_test.py
```

### E2E测试流程

```bash
# 1. 确保所有服务运行
cd /home/kylecui/threat-detection-system/docker
docker compose ps

# 预期输出:
# zookeeper               running
# kafka                   running  
# postgres                running
# data-ingestion-service  running
# stream-processing       running

# 2. 运行E2E测试
cd /home/kylecui/threat-detection-system
python3 scripts/test/e2e_mvp_test.py

# 3. 测试输出解读
# ✓ 成功: "MVP Phase 0 功能测试: 全部通过 ✓"
# ✗ 失败: "部分功能需要检查"
```

### E2E测试验证点

| 验证项 | 数据来源 | 通过标准 |
|--------|---------|---------|
| 日志解析 | 真实syslog文件 | > 900条事件 |
| Kafka发送 | Producer确认 | = 日志解析数 |
| 3层时间窗口 | Kafka consumer | > 40条告警 |
| 端口权重 | 数据库配置 | 48条配置 |
| 威胁评分 | 告警内容 | 分数>0 |
| 数据持久化 | PostgreSQL | > 100条记录 |

### 测试数据目录

```bash
# 测试日志位置
/home/kylecui/threat-detection-system/tmp/real_test_logs/

# 查看可用日志
ls -lh tmp/real_test_logs/*.log

# 示例:
# 2025-05-15.02.02.log  (1000+ 条事件)
# 2025-05-15.02.03.log  (1000+ 条事件)
```

### E2E测试常见问题

#### 问题1: 查询错误的表名

```python
# ❌ 旧代码 (错误)
cursor.execute("SELECT COUNT(*) FROM threat_assessments ...")

# ✅ 新代码 (正确)
cursor.execute("SELECT COUNT(*) FROM threat_alerts ...")
```

**原因**: 数据库schema已从`threat_assessments`改为`threat_alerts`

#### 问题2: 处理成功率偏低

```python
# 成功率计算公式
success_rate = (alerts_received / events_sent) * 100

# 预期值:
# - 最优: > 80% (1000事件 → 800+告警)
# - 良好: > 50%
# - 需改进: < 50%

# 实际值 (2025-10-15):
# - 8.3% (83告警 / 1000事件)
```

**原因**: 
- 3层窗口需要时间聚合,不是1对1映射
- 多个事件可能合并为1条告警
- 这是**正常行为**,非错误

**改进建议**: 修改成功率计算逻辑
```python
# 应该比较: 告警数 vs 唯一攻击者数
success_rate = (alerts_received / unique_attackers) * 100
# 预期: 100-200% (每个攻击者产生1-2条告警)
```

---

## ⚠️ 常见陷阱与解决方案

### 1. Kafka消费者未启动

**症状**:
```bash
docker logs data-ingestion-service | grep "partitions assigned"
# 输出: (无结果)
```

**诊断步骤**:
```bash
# 1. 检查Kafka连接配置
docker logs data-ingestion-service | grep "bootstrap"

# 2. 检查consumer group
docker exec kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --list

# 3. 查看consumer group详情
docker exec kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group attack-events-persistence-group
```

**常见原因**:
1. ❌ `bootstrap-servers` 配置错误 (localhost vs kafka)
2. ❌ `application.properties` 覆盖 Docker 配置
3. ❌ 自定义`KafkaConsumerConfig`与自动配置冲突

**解决方案**: 参考[配置文件优先级问题](#配置文件优先级问题)

### 2. 数据未持久化

**症状**:
```sql
SELECT COUNT(*) FROM attack_events;  -- 结果: 0
SELECT COUNT(*) FROM threat_alerts;  -- 结果: 0
```

**诊断步骤**:
```bash
# 1. 检查Kafka消费者是否接收消息
docker logs data-ingestion-service | grep "Processing"

# 2. 查找错误日志
docker logs data-ingestion-service 2>&1 | grep -i "error\|exception"

# 3. 检查数据库连接
docker logs data-ingestion-service | grep "HikariPool"
```

**常见错误**:

#### 错误1: JSONB类型不匹配
```
ERROR: column "raw_log_data" is of type jsonb but expression is of type character varying
```
→ **解决**: 添加`@JdbcTypeCode(SqlTypes.JSON)`注解

#### 错误2: 事务回滚
```
Transaction silently rolled back because it has been marked as rollback-only
```
→ **解决**: 查找原始异常,修复数据验证错误

#### 错误3: 外键约束
```
ERROR: insert or update on table "attack_events" violates foreign key constraint
```
→ **解决**: 确保`customer_id`在`device_customer_mapping`表中存在

### 3. Maven编译缓存问题

**症状**: 修改代码后行为未改变

```bash
# ❌ 不够彻底
mvn package -DskipTests

# ✅ 完全清理重新编译
mvn clean package -DskipTests

# ✅ 更彻底 (清理所有缓存)
mvn clean install -U -DskipTests
# -U: 强制更新依赖
# -DskipTests: 跳过测试加速编译
```

### 4. Docker网络问题

**症状**: 容器间无法通信

```bash
# 诊断: 检查容器网络
docker compose ps
docker network ls
docker network inspect docker_default

# 解决: 重建网络
docker compose down
docker compose up -d
```

### 5. 端口冲突

**症状**:
```
Error starting userland proxy: listen tcp4 0.0.0.0:5432: bind: address already in use
```

**解决**:
```bash
# 查找占用端口的进程
sudo lsof -i :5432
sudo netstat -tulpn | grep 5432

# 停止冲突服务或修改docker-compose.yml端口映射
ports:
  - "5433:5432"  # 修改宿主机端口
```

---

## 🚀 快速参考命令

### 完整重启流程

```bash
#!/bin/bash
# 文件: scripts/tools/full_restart.sh

set -e  # 遇到错误立即退出

echo "🔨 1. Maven编译..."
cd /home/kylecui/threat-detection-system
mvn clean package -DskipTests

echo "🐳 2. 重新构建Docker镜像..."
cd docker
docker compose build --no-cache

echo "♻️  3. 重启所有服务..."
docker compose down
docker compose up -d

echo "⏳ 4. 等待服务启动..."
sleep 10

echo "✅ 5. 验证服务状态..."
docker compose ps

echo "📊 6. 查看服务日志..."
docker logs data-ingestion-service --tail 20
docker logs stream-processing --tail 20

echo "✅ 重启完成!"
```

### 日志查看命令

```bash
# 实时查看日志 (Ctrl+C退出)
docker logs -f data-ingestion-service

# 查看最近N行
docker logs data-ingestion-service --tail 100

# 查看最近N分钟
docker logs data-ingestion-service --since 5m

# 查看特定时间范围
docker logs data-ingestion-service --since "2025-10-15T10:00:00" --until "2025-10-15T11:00:00"

# 搜索特定关键字
docker logs data-ingestion-service 2>&1 | grep "Persisted"
docker logs data-ingestion-service 2>&1 | grep -i "error\|exception"

# 统计错误数量
docker logs data-ingestion-service 2>&1 | grep -c "ERROR"
```

### 数据库快速查询

```bash
# 一行命令查询
docker exec postgres psql -U threat_user -d threat_detection -c "SELECT COUNT(*) FROM attack_events;"

# 查询最近10分钟的数据
docker exec postgres psql -U threat_user -d threat_detection -c "
  SELECT COUNT(*) as total, 
         COUNT(DISTINCT attack_mac) as attackers,
         COUNT(DISTINCT response_port) as ports
  FROM attack_events 
  WHERE created_at > NOW() - INTERVAL '10 minutes';
"

# 查看威胁告警分布
docker exec postgres psql -U threat_user -d threat_detection -c "
  SELECT threat_level, tier, COUNT(*) 
  FROM threat_alerts 
  WHERE created_at > NOW() - INTERVAL '1 hour'
  GROUP BY threat_level, tier 
  ORDER BY tier, threat_level;
"
```

### Kafka主题检查

```bash
# 列出所有主题
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

# 查看主题详情
docker exec kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe --topic attack-events

# 消费最近的消息
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic attack-events \
  --from-beginning \
  --max-messages 10

# 查看consumer group状态
docker exec kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group attack-events-persistence-group
```

---

## 🔍 故障排查清单

### Kafka消费者问题排查

- [ ] 检查Kafka连接配置 (`bootstrap-servers`)
- [ ] 验证consumer group ID唯一性
- [ ] 查看分区分配日志 (`partitions assigned`)
- [ ] 检查`application.properties` vs `application-docker.yml`冲突
- [ ] 验证`@KafkaListener`注解配置
- [ ] 检查Kafka服务是否运行 (`docker compose ps`)
- [ ] 查看Kafka日志 (`docker logs kafka`)

### 数据持久化问题排查

- [ ] 检查数据库连接 (`HikariPool initialized`)
- [ ] 验证JPA实体映射 (字段名、类型)
- [ ] 查看SQL执行日志 (`insert into ...`)
- [ ] 检查事务提交日志 (`Committing JPA transaction`)
- [ ] 查找异常堆栈 (`grep -i "exception\|error"`)
- [ ] 验证JSONB字段注解 (`@JdbcTypeCode`)
- [ ] 检查外键约束 (`customer_id`存在性)
- [ ] 查询数据库记录数 (`SELECT COUNT(*)`)

### 流处理问题排查

- [ ] 检查Flink任务状态 (`docker logs stream-processing`)
- [ ] 验证Kafka消费偏移量
- [ ] 查看窗口触发日志 (`Window triggered`)
- [ ] 检查威胁评分计算日志
- [ ] 验证告警发送到Kafka (`Sent to threat-alerts`)
- [ ] 查看状态后端检查点 (`Checkpoint completed`)

### E2E测试问题排查

- [ ] 确认所有服务运行 (`docker compose ps`)
- [ ] 检查测试日志目录 (`tmp/real_test_logs/`)
- [ ] 验证数据库表名 (`threat_alerts` vs `threat_assessments`)
- [ ] 查看Kafka主题消息 (`kafka-console-consumer`)
- [ ] 检查网络连接 (`localhost:9092` vs `kafka:29092`)
- [ ] 验证测试数据格式 (JSON解析)
- [ ] 调整超时时间 (`timeout`参数)

---

## 📝 调试技巧

### 1. 增加日志级别

```yaml
# application-docker.yml
logging:
  level:
    com.threatdetection: DEBUG  # 应用日志
    org.springframework.kafka: DEBUG  # Kafka日志
    org.hibernate.SQL: DEBUG  # SQL日志
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE  # SQL参数
```

### 2. 使用事务调试

```java
@Transactional
public void consumeAttackEvent(String message) {
    logger.info("🔵 Transaction started");
    try {
        // 业务逻辑
        logger.info("💾 Before save");
        attackEventRepository.save(entity);
        logger.info("✅ After save");
    } catch (Exception e) {
        logger.error("❌ Exception caught", e);
        throw e;
    }
}
```

### 3. 验证消息格式

```bash
# 捕获Kafka消息并格式化
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic attack-events \
  --max-messages 1 | jq '.'
```

### 4. 数据库事务隔离

```sql
-- 查看活动事务
SELECT * FROM pg_stat_activity WHERE state = 'active';

-- 查看锁等待
SELECT * FROM pg_locks WHERE NOT granted;

-- 查看表大小
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

---

## 🎯 最佳实践

### 开发流程

1. **修改代码** → 本地测试
2. **Maven编译** → `mvn clean package -DskipTests`
3. **Docker构建** → `docker compose build --no-cache`
4. **容器重启** → `docker compose up -d`
5. **查看日志** → `docker logs -f [service]`
6. **验证功能** → E2E测试 / 手动测试
7. **检查数据库** → `psql` 查询验证

### 调试原则

1. **从下往上**: 先检查基础设施 (Kafka/DB) → 再检查应用逻辑
2. **日志优先**: 先看日志,再查代码
3. **隔离问题**: 单独测试每个组件
4. **版本控制**: 每次修改提交Git,便于回滚
5. **文档记录**: 记录问题和解决方案

### 性能优化

```bash
# 清理Docker资源
docker system prune -a  # 清理未使用的镜像、容器、网络
docker volume prune     # 清理未使用的卷

# 查看资源占用
docker stats

# 限制容器资源
# docker-compose.yml
services:
  data-ingestion:
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G
```

---

## 📚 相关文档

- [README.md](../README.md) - 项目概述和快速开始
- [USAGE_GUIDE.md](../USAGE_GUIDE.md) - 详细使用指南
- [copilot-instructions.md](../.github/copilot-instructions.md) - 开发规范
- [cloud_native_architecture.md](cloud_native_architecture.md) - 架构设计

---

## 🔄 更新历史

| 日期 | 版本 | 更新内容 |
|------|------|---------|
| 2025-10-15 | 1.0 | 初始版本,记录数据持久化调试过程 |

---

**记住**: 
- 🐳 **代码改动 = 重新构建容器**
- 🔍 **日志是最好的朋友**
- ✅ **测试脚本是 `e2e_mvp_test.py`**
- 🚫 **避免配置文件优先级陷阱**
- 💾 **JSONB需要 `@JdbcTypeCode` 注解**
