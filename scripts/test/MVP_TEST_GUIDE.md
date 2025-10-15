# MVP Phase 0: 功能测试指南

## 📋 测试概述

本测试套件验证云原生威胁检测系统的MVP核心功能：

### 测试范围

✅ **数据理解修正**
- 蜜罐机制正确理解（responseIp=诱饵IP, responsePort=攻击意图）
- 攻击事件描述准确性

✅ **3层时间窗口**
- Tier-1: 30秒窗口（勒索软件快速检测）
- Tier-2: 5分钟窗口（主要威胁检测）
- Tier-3: 15分钟窗口（APT慢速扫描）

✅ **端口权重系统**
- CVSS高危端口权重（50个核心端口）
- 经验权重配置（169个原系统端口）
- 混合权重算法

✅ **威胁评分算法**
- 基础公式: `(attackCount × uniqueIps × uniquePorts) × timeWeight × ipWeight × portWeight × deviceWeight × tierWeight`
- 5级威胁分类: CRITICAL/HIGH/MEDIUM/LOW/INFO
- 与原C#系统对齐

✅ **端到端数据流**
- 日志解析 → Kafka → Flink流处理 → 威胁评估 → PostgreSQL

---

## 🚀 快速开始

### 1. 环境准备

**启动Docker Compose服务**
```bash
cd docker
docker-compose up -d postgres kafka zookeeper
```

**检查服务状态**
```bash
# PostgreSQL
docker-compose ps postgres

# Kafka
docker-compose ps kafka

# 日志
docker-compose logs -f postgres kafka
```

### 2. 数据库初始化

**运行端口权重配置迁移**
```bash
# 方式1: Docker Compose自动初始化
docker-compose exec postgres psql -U threat_user -d threat_detection -f /docker-entrypoint-initdb.d/port_weights_migration.sql

# 方式2: 手动执行
docker cp docker/port_weights_migration.sql postgres:/tmp/
docker-compose exec postgres psql -U threat_user -d threat_detection -f /tmp/port_weights_migration.sql
```

**验证端口配置**
```bash
docker-compose exec postgres psql -U threat_user -d threat_detection -c "SELECT COUNT(*) FROM port_risk_configs;"
```

### 3. 运行单元测试

**测试核心算法**
```bash
cd scripts/test
python3 unit_test_mvp.py
```

**预期输出**
```
========================================
MVP Phase 0: 单元测试
========================================

test_high_risk_ports (__main__.PortWeightTests) ... ok
test_port_diversity_weight (__main__.PortWeightTests) ... ok
test_ip_weight_calculation (__main__.ThreatScoreTests) ... ok
test_threat_level_classification (__main__.ThreatScoreTests) ... ok
test_threat_score_formula (__main__.ThreatScoreTests) ... ok
test_time_weight_calculation (__main__.ThreatScoreTests) ... ok
test_tier_alert_thresholds (__main__.MultiTierWindowTests) ... ok
test_tier_weight_calculation (__main__.MultiTierWindowTests) ... ok

----------------------------------------------------------------------
Ran 8 tests in 0.005s

OK
```

### 4. 启动流处理服务

**方式1: Docker Compose (推荐)**
```bash
cd docker
docker-compose up -d stream-processing
```

**方式2: 本地Maven启动**
```bash
cd services/stream-processing
mvn clean package -DskipTests
java -jar target/stream-processing-1.0-SNAPSHOT.jar
```

### 5. 运行端到端测试

**自动化测试脚本**
```bash
cd scripts/test
./run_mvp_tests.sh e2e
```

**手动测试**
```bash
# 设置环境变量
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=threat_detection
export DB_USER=threat_user
export DB_PASSWORD=threat_password

# 运行测试
python3 e2e_mvp_test.py
```

---

## 📊 测试数据

### 真实测试日志

位置: `tmp/real_test_logs/`

**日志格式**
```json
{
  "message": "syslog_version=1.10.0,dev_serial=caa0beea29676c6d,log_type=1,sub_type=1,attack_mac=8c:94:6a:27:14:01,attack_ip=10.68.77.107,response_ip=10.68.9.111,response_port=9100,line_id=1,Iface_type=1,Vlan_id=70,log_time=1747274520"
}
```

**关键字段说明**
- `log_type=1`: 攻击事件（诱捕到的攻击行为）
- `log_type=2`: 状态日志（设备心跳）
- `attack_mac`: **被诱捕的内网失陷主机MAC**
- `attack_ip`: **被诱捕的内网主机IP**
- `response_ip`: **诱饵IP（不存在的虚拟哨兵）**
- `response_port`: **攻击者尝试访问的端口（暴露攻击意图）**

**端口65536说明**
- `response_port=65536`: ARP探测（仅二层探测，无端口访问）
- 测试中会自动过滤此类事件

### 高危端口示例

| 端口 | 服务 | 权重 | 攻击意图 |
|-----|------|------|---------|
| 22 | SSH | 10.0 | SSH远程控制 |
| 161 | SNMP | 7.5 | SNMP网络管理 |
| 445 | SMB | 9.5 | SMB横向移动 |
| 3389 | RDP | 10.0 | RDP远程桌面 |
| 3306 | MySQL | 9.0 | MySQL数据库攻击 |
| 9100 | Printer | 6.0 | 打印机服务探测 |
| 515 | LPR | 5.0 | 行式打印机协议 |

---

## 🧪 测试场景

### 场景1: 勒索软件快速传播 (Tier-1)

**特征**
- 30秒内高频攻击（>=50次）
- 或中频+多IP（>=20次且IP>=5个）
- 窗口权重: 1.5

**预期结果**
- 触发Tier-1告警
- `detectionType`: RANSOMWARE_DETECTION
- 威胁等级: CRITICAL/HIGH

### 场景2: 内网横向扫描 (Tier-2)

**特征**
- 5分钟内访问多个诱饵IP（>=3个）
- 或尝试多个端口（>=5个）
- 窗口权重: 1.0

**预期结果**
- 触发Tier-2告警
- `detectionType`: MAIN_THREAT_DETECTION
- 威胁等级: MEDIUM/HIGH

### 场景3: APT慢速扫描 (Tier-3)

**特征**
- 15分钟内持续低频攻击（>=5次）
- 多IP+多端口组合（IP>=2且Port>=3）
- 窗口权重: 1.2

**预期结果**
- 触发Tier-3告警
- `detectionType`: APT_SLOW_SCAN
- 威胁等级: MEDIUM

---

## 📈 预期测试结果

### 单元测试

```
✓ 端口多样性权重计算 (6档)
✓ 高危端口权重验证 (50+配置)
✓ 时间权重计算 (5时段)
✓ IP多样性权重 (5档)
✓ 威胁评分公式
✓ 威胁等级分类 (5级)
✓ 分层告警阈值
✓ 分层权重计算
```

### 端到端测试

```
数据摄取:
  原始日志解析:      1000 条
  Kafka事件发送:     1000 条
  唯一攻击者:          50 个
  唯一端口:            15 个
  高危端口攻击:       300 次

流处理 (3层时间窗口):
  总告警数:            80 条
  Tier-1 (30秒):       15 条
  Tier-2 (5分钟):      45 条
  Tier-3 (15分钟):     20 条

数据持久化:
  数据库记录:          80 条

处理成功率: 85.00% (优秀)

MVP功能验证清单:
  ✓ 蜜罐机制理解
  ✓ 日志解析与摄取
  ✓ Kafka消息传递
  ✓ 3层时间窗口
  ✓ 端口权重计算
  ✓ 威胁评分生成
  ✓ 数据库持久化
```

---

## 🔍 结果验证

### 查询威胁评估记录

```sql
-- 最近1小时的威胁评估
SELECT 
    customer_id,
    attack_mac,
    threat_score,
    threat_level,
    attack_count,
    unique_ips,
    unique_ports,
    unique_devices,
    detection_tier,
    assessment_time
FROM threat_assessments
WHERE assessment_time > NOW() - INTERVAL '1 hour'
ORDER BY threat_score DESC
LIMIT 20;
```

### 查询端口使用统计

```sql
-- 端口风险分布
SELECT 
    prc.port_number,
    prc.port_name,
    prc.risk_level,
    prc.risk_weight,
    COUNT(ta.id) as attack_count
FROM port_risk_configs prc
LEFT JOIN threat_assessments ta ON ta.port_list LIKE '%' || prc.port_number || '%'
WHERE prc.enabled = TRUE
  AND ta.assessment_time > NOW() - INTERVAL '1 hour'
GROUP BY prc.port_number, prc.port_name, prc.risk_level, prc.risk_weight
HAVING COUNT(ta.id) > 0
ORDER BY attack_count DESC;
```

### 查询分层告警统计

```sql
-- 分层告警分布
SELECT 
    detection_tier,
    threat_level,
    COUNT(*) as alert_count,
    AVG(threat_score) as avg_score,
    MAX(threat_score) as max_score
FROM threat_assessments
WHERE assessment_time > NOW() - INTERVAL '1 hour'
GROUP BY detection_tier, threat_level
ORDER BY detection_tier, 
    CASE threat_level
        WHEN 'CRITICAL' THEN 1
        WHEN 'HIGH' THEN 2
        WHEN 'MEDIUM' THEN 3
        WHEN 'LOW' THEN 4
        ELSE 5
    END;
```

---

## 🐛 故障排查

### 问题1: Kafka连接失败

**症状**
```
✗ Kafka连接失败: NoBrokersAvailable
```

**解决方案**
```bash
# 检查Kafka状态
docker-compose ps kafka

# 重启Kafka
docker-compose restart kafka

# 查看日志
docker-compose logs kafka
```

### 问题2: PostgreSQL连接失败

**症状**
```
✗ PostgreSQL连接失败: connection refused
```

**解决方案**
```bash
# 检查PostgreSQL状态
docker-compose ps postgres

# 重启PostgreSQL
docker-compose restart postgres

# 验证连接
docker-compose exec postgres psql -U threat_user -d threat_detection -c "SELECT 1;"
```

### 问题3: 端口权重表为空

**症状**
```
⚠ 端口权重配置表为空，将使用默认权重
```

**解决方案**
```bash
# 手动执行迁移脚本
docker cp docker/port_weights_migration.sql postgres:/tmp/
docker-compose exec postgres psql -U threat_user -d threat_detection -f /tmp/port_weights_migration.sql

# 验证
docker-compose exec postgres psql -U threat_user -d threat_detection -c "SELECT COUNT(*) FROM port_risk_configs;"
```

### 问题4: 流处理服务未启动

**症状**
```
⚠ Flink 未运行，流处理可能无法工作
```

**解决方案**
```bash
# 启动流处理服务
cd docker
docker-compose up -d stream-processing

# 或本地启动
cd services/stream-processing
mvn clean package -DskipTests
java -jar target/stream-processing-1.0-SNAPSHOT.jar
```

### 问题5: 测试日志不存在

**症状**
```
✗ 测试日志目录不存在: tmp/real_test_logs
```

**解决方案**
- 确认测试日志已复制到 `tmp/real_test_logs/` 目录
- 或使用模拟数据生成器创建测试数据

---

## 📝 测试报告

### 生成测试报告

测试完成后会自动生成控制台报告，包含：

1. **数据摄取统计**: 日志解析数、事件发送数、攻击者统计
2. **流处理统计**: 3层窗口告警分布、威胁等级分布
3. **数据持久化**: 数据库记录数、评估统计
4. **处理成功率**: 端到端数据流完整性
5. **功能验证清单**: 7项核心功能检查

### 保存测试结果

```bash
# 重定向输出到文件
./run_mvp_tests.sh e2e | tee test_report_$(date +%Y%m%d_%H%M%S).log

# 导出数据库结果
docker-compose exec postgres psql -U threat_user -d threat_detection -c "\copy (SELECT * FROM threat_assessments WHERE assessment_time > NOW() - INTERVAL '1 hour') TO '/tmp/test_results.csv' WITH CSV HEADER"
```

---

## 🎯 下一步计划

- [ ] 性能测试（吞吐量、延迟）
- [ ] 压力测试（大规模并发）
- [ ] 网段权重实现
- [ ] 标签/白名单系统
- [ ] API接口测试
- [ ] Kubernetes部署测试

---

## 📚 参考文档

- [项目README](../../README.md)
- [使用指南](../../USAGE_GUIDE.md)
- [Copilot指令](../../.github/copilot-instructions.md)
- [蜜罐威胁评分方案](../../docs/honeypot_based_threat_scoring.md)

---

**测试时间**: 2025-10-14  
**MVP版本**: Phase 0  
**测试范围**: 核心功能完整性验证
