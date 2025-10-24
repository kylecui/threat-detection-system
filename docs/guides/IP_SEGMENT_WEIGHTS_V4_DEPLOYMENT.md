# V4.0 IP网段权重系统部署指南

## 📋 目录
- [系统概述](#系统概述)
- [部署方案对比](#部署方案对比)
- [快速部署](#快速部署)
- [部署验证](#部署验证)
- [常见问题](#常见问题)
- [生产环境建议](#生产环境建议)

---

## 系统概述

### V4.0双维度权重系统

**核心理念**: 蜜罐系统的威胁评分需要同时评估"被诱捕者的危害性"和"攻击者暴露的意图严重性"

```
威胁评分 = 基础分数 × 攻击源权重 × 蜜罐敏感度权重 × [其他权重]
         = baseScore × attackSourceWeight × honeypotSensitivityWeight × ...
```

### 两个维度

#### 维度1: 攻击源权重 (attack_source_weights)
**评估**: "如果这个内网设备被攻陷，危害有多大？"

| 网段类型 | 权重范围 | 示例 |
|---------|---------|------|
| 核心数据库 | 3.0 | 10.0.3.0/24 |
| 管理网段 | 3.0 | 10.0.100.0/24 |
| 应用服务器 | 2.0 | 10.0.2.0/24 |
| 办公区域 | 1.0 | 192.168.10.0/24 |
| IoT设备 | 0.9 | 192.168.50.0/24 |
| 访客网络 | 0.6 | 192.168.100.0/24 |

#### 维度2: 蜜罐敏感度权重 (honeypot_sensitivity_weights)
**评估**: "攻击者访问这个蜜罐暴露了什么意图？"

| 部署区域 | 权重范围 | 模拟服务 | 攻击意图 |
|---------|---------|---------|---------|
| 管理区域 | 3.5 | Domain Controller | 权限提升/横向移动 |
| 数据库区 | 3.5 | MySQL/PostgreSQL | 数据窃取 |
| 核心服务器 | 2.5 | File Server | 文件窃取/勒索 |
| 应用服务器 | 2.0 | Web Server | 服务渗透 |
| 办公区域 | 1.3 | Workstation | 初步探测 |
| DMZ区域 | 1.0 | Web Gateway | 外围侦察 |

### 典型场景

#### 场景1: IoT设备扫描管理区蜜罐 🚨 CRITICAL
```
攻击源: 192.168.50.10 (IoT设备) → 权重 0.9
蜜罐IP: 10.0.100.50 (管理区域) → 权重 3.5
组合权重: 0.9 × 3.5 = 3.15 → CRITICAL级别

解读: IoT设备本身风险低，但访问Domain Controller蜜罐暴露了
     "权限提升/横向移动"意图，这是APT攻击的典型特征
```

#### 场景2: 数据库服务器访问办公区蜜罐 🚨 CRITICAL
```
攻击源: 10.0.3.10 (数据库服务器) → 权重 3.0
蜜罐IP: 192.168.10.50 (办公区域) → 权重 1.3
组合权重: 3.0 × 1.3 = 3.9 → CRITICAL级别

解读: 数据库服务器被攻陷的危害极大，即便只是简单的端口扫描
     也应立即告警，防止勒索软件横向传播
```

#### 场景3: 办公设备扫描办公蜜罐 ⚠️ MEDIUM
```
攻击源: 192.168.10.100 (办公区域) → 权重 1.0
蜜罐IP: 192.168.10.50 (办公区域) → 权重 1.3
组合权重: 1.0 × 1.3 = 1.3 → MEDIUM级别

解读: 办公设备扫描同网段，可能是误操作或弱威胁，中等级别
```

#### 场景4: 访客网络访问数据库蜜罐 🔴 HIGH
```
攻击源: 192.168.100.20 (访客网络) → 权重 0.6
蜜罐IP: 10.0.3.50 (数据库区域) → 权重 3.5
组合权重: 0.6 × 3.5 = 2.1 → HIGH级别

解读: 访客网络能访问数据库蜜罐说明网络隔离失败，需修复ACL
```

---

## 部署方案对比

### 方案1: 全新部署（删除数据卷）

**优点**:
- ✅ 最简单可靠
- ✅ 自动执行所有初始化脚本
- ✅ 保证配置一致性

**缺点**:
- ❌ **所有现有数据会丢失**
- ❌ 下游服务需要停机
- ❌ 仅适合开发/测试环境

**使用场景**:
- 开发环境调试
- 测试环境重置
- 首次部署（无历史数据）

**命令**:
```bash
# 停止PostgreSQL
docker-compose down postgres

# 删除数据卷
docker volume rm docker_postgres_data

# 重建并启动
docker-compose build --no-cache postgres
docker-compose up -d --force-recreate postgres
```

### 方案2: 增量部署（手动执行SQL）✅ **推荐**

**优点**:
- ✅ **保留现有数据**
- ✅ 零停机部署
- ✅ 可控的变更流程
- ✅ 适合生产环境

**缺点**:
- ⚠️ 需要手动执行SQL（已脚本化）
- ⚠️ 需要验证执行结果

**使用场景**:
- 生产环境更新
- 有历史数据的系统
- 需要审计的变更

**命令**:
```bash
# 方式1: 使用脚本（推荐）
cd /home/kylecui/threat-detection-system
chmod +x scripts/deploy_ip_segment_weights_v4.sh
./scripts/deploy_ip_segment_weights_v4.sh
# 选择 选项2: 增量部署

# 方式2: 手动执行
docker exec -i postgres psql -U threat_user -d threat_detection \
    < docker/12-ip-segment-weights-v4.sql
```

### 方案3: 生产级迁移脚本

**优点**:
- ✅ 事务性保证（全部成功或全部回滚）
- ✅ 完整的审计日志
- ✅ 支持回滚
- ✅ 符合企业规范

**缺点**:
- ⚠️ 需要额外开发（计划中）

**使用场景**:
- 正式生产环境
- 需要变更管理流程
- 需要合规审计

**实现方式**（计划中）:
```bash
# 使用Flyway/Liquibase迁移工具
# TODO: 将在后续版本实现
```

---

## 快速部署

### 前置条件

```bash
# 检查Docker容器状态
docker ps | grep postgres

# 检查数据卷
docker volume ls | grep postgres

# 检查SQL脚本存在
ls -lh docker/12-ip-segment-weights-v4.sql
```

### 标准部署流程（推荐）

#### 步骤1: 运行部署脚本

```bash
cd /home/kylecui/threat-detection-system

# 添加执行权限
chmod +x scripts/deploy_ip_segment_weights_v4.sh

# 执行部署
./scripts/deploy_ip_segment_weights_v4.sh
```

#### 步骤2: 选择部署选项

```
部署选项:
  1) 全新部署 (删除现有数据，重新初始化) ⚠️ 数据会丢失!
  2) 增量部署 (保留现有数据，手动执行SQL)      👈 推荐
  3) 仅验证部署

请选择 [1/2/3]: 2
```

#### 步骤3: 自动验证

脚本会自动执行以下验证:
- ✅ 检查表结构
- ✅ 验证记录数量
- ✅ 测试查询函数
- ✅ 验证4个关键场景

**预期输出**:
```
==========================================
验证V4.0部署
==========================================
检查表结构...
 table_name                      | record_count
---------------------------------+--------------
 attack_source_weights           |           12
 honeypot_sensitivity_weights    |           10

检查查询函数...
场景1: IoT设备(192.168.50.10) → 管理区蜜罐(10.0.100.50)
✓ 场景1验证通过 (combined_weight=3.15)

场景2: 数据库服务器(10.0.3.10) → 办公区蜜罐(192.168.10.50)
✓ 场景2验证通过 (combined_weight=3.90)

场景3: 办公区(192.168.10.100) → 办公区蜜罐(192.168.10.50)
✓ 场景3验证通过 (combined_weight=1.30)

==========================================
✓ V4.0部署完成并验证成功!
==========================================
```

---

## 部署验证

### 手动验证方法

#### 1. 检查表结构和数据

```sql
-- 连接数据库
docker exec -it postgres psql -U threat_user -d threat_detection

-- 检查攻击源权重表
SELECT segment_name, segment_type, weight, risk_level 
FROM attack_source_weights 
WHERE customer_id = 'default' AND is_active = TRUE
ORDER BY weight DESC;

-- 预期输出: 12条记录，权重从0.5到3.0

-- 检查蜜罐敏感度表
SELECT honeypot_name, deployment_zone, simulated_service, weight, honeypot_tier
FROM honeypot_sensitivity_weights
WHERE customer_id = 'default' AND is_active = TRUE
ORDER BY weight DESC;

-- 预期输出: 10条记录，权重从1.0到3.5
```

#### 2. 测试查询函数

```sql
-- 测试场景1: IoT → 管理区
SELECT * FROM get_combined_segment_weight('default', '192.168.50.10', '10.0.100.50');

-- 预期: attack_source_weight=0.90, honeypot_sensitivity_weight=3.50, combined_weight=3.15

-- 测试场景2: 数据库 → 办公区
SELECT * FROM get_combined_segment_weight('default', '10.0.3.10', '192.168.10.50');

-- 预期: attack_source_weight=3.00, honeypot_sensitivity_weight=1.30, combined_weight=3.90

-- 测试场景3: 办公区 → 办公区
SELECT * FROM get_combined_segment_weight('default', '192.168.10.100', '192.168.10.50');

-- 预期: attack_source_weight=1.00, honeypot_sensitivity_weight=1.30, combined_weight=1.30

-- 测试场景4: 访客 → 数据库
SELECT * FROM get_combined_segment_weight('default', '192.168.100.20', '10.0.3.50');

-- 预期: attack_source_weight=0.60, honeypot_sensitivity_weight=3.50, combined_weight=2.10
```

#### 3. 运行单元测试

```bash
cd services/threat-assessment

# 运行V4服务测试
mvn test -Dtest=IpSegmentWeightServiceV4Test

# 预期: 所有10个测试用例通过
# Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
```

#### 4. 检查日志

```bash
# 查看PostgreSQL日志
docker logs postgres | grep -i "ip-segment-weights-v4"

# 查看threat-assessment服务日志
docker logs threat-assessment | grep -i "IpSegmentWeightService"
```

---

## 常见问题

### Q1: 为什么需要两个维度的权重？

**A**: 蜜罐系统的特殊性决定的

**单维度问题**（V3.0设计缺陷）:
```
IoT设备(0.9) → 管理区蜜罐
威胁分数 = baseScore × 0.9 = LOW级别 ❌

问题: IoT设备权重低，但访问Domain Controller蜜罐暴露了
     APT攻击意图（权限提升），应该是CRITICAL级别
```

**双维度解决**（V4.0设计）:
```
攻击源: IoT设备 → 权重0.9 (设备本身风险低)
蜜罐: 管理区域 → 权重3.5 (暴露高危意图)
组合权重: 0.9 × 3.5 = 3.15 → CRITICAL级别 ✅

原理: 低风险设备访问高敏感蜜罐 = 横向移动行为
```

### Q2: 为什么init脚本不重新执行？

**A**: Docker设计机制

```bash
# PostgreSQL容器启动逻辑:
if [ "$(ls -A /var/lib/postgresql/data)" ]; then
    # 数据目录非空 → 跳过初始化
    echo "Database already initialized"
else
    # 数据目录为空 → 执行docker-entrypoint-initdb.d/*.sql
    run_init_scripts
fi
```

**解决方案**:
- 全新部署: 删除数据卷 `docker volume rm docker_postgres_data`
- 增量部署: 手动执行SQL `docker exec -i postgres psql ... < script.sql`
- 生产环境: 使用迁移工具（Flyway/Liquibase）

### Q3: 如何回滚V4.0部署？

**A**: 分情况处理

**场景1: 全新部署（数据已删除）**
```bash
# 无法回滚，数据已丢失
# 只能从备份恢复
```

**场景2: 增量部署（数据保留）**
```sql
-- 连接数据库
docker exec -it postgres psql -U threat_user -d threat_detection

-- 删除V4.0表
DROP TABLE IF EXISTS attack_source_weights CASCADE;
DROP TABLE IF EXISTS honeypot_sensitivity_weights CASCADE;

-- 删除V4.0函数
DROP FUNCTION IF EXISTS get_attack_source_weight(VARCHAR, VARCHAR);
DROP FUNCTION IF EXISTS get_honeypot_sensitivity_weight(VARCHAR, VARCHAR);
DROP FUNCTION IF EXISTS get_combined_segment_weight(VARCHAR, VARCHAR, VARCHAR);
```

### Q4: 如何自定义网段配置？

**A**: 通过SQL插入/更新

**添加新的攻击源配置**:
```sql
INSERT INTO attack_source_weights (
    customer_id, segment_name, ip_range_start, ip_range_end,
    segment_type, risk_level, weight, priority, description
) VALUES (
    'default',
    '生产环境服务器',
    '10.0.10.0', '10.0.10.255',
    'SERVER', 'CRITICAL',
    2.8, 90,
    '生产环境应用服务器，业务关键'
);
```

**添加新的蜜罐配置**:
```sql
INSERT INTO honeypot_sensitivity_weights (
    customer_id, honeypot_name, ip_range_start, ip_range_end,
    honeypot_tier, deployment_zone, simulated_service, attack_intent,
    weight, priority, description
) VALUES (
    'default',
    'Active Directory蜜罐',
    '10.0.100.100', '10.0.100.100',
    'CRITICAL_ASSET', 'MANAGEMENT',
    'Domain Controller', '域控权限提升',
    3.8, 100,
    'AD域控蜜罐，访问即为APT攻击'
);
```

### Q5: 如何监控V4.0系统运行状态？

**A**: 使用PostgreSQL日志和应用指标

**数据库层面**:
```sql
-- 查询使用频率
SELECT 
    segment_name,
    query_count,
    last_query_time
FROM attack_source_weights
WHERE customer_id = 'default'
ORDER BY query_count DESC
LIMIT 10;

-- 查询未匹配的IP（需要添加配置）
-- TODO: 需要添加日志表记录未匹配查询
```

**应用层面**:
```bash
# 查看Redis缓存命中率
docker exec -it redis redis-cli INFO stats | grep hit

# 查看威胁评估服务日志
docker logs threat-assessment | grep "IpSegmentWeightService" | tail -100
```

---

## 生产环境建议

### 1. 部署策略

**推荐**: 蓝绿部署或灰度发布

```
步骤1: 在测试环境完整验证
步骤2: 在生产环境创建数据库快照
步骤3: 使用增量部署方式（手动SQL）
步骤4: 验证4个关键场景
步骤5: 监控1小时，观察告警准确性
步骤6: 如有问题，执行回滚SQL
```

### 2. 性能优化

**数据库索引**（已包含在V4.0 SQL中）:
```sql
-- 攻击源表索引
CREATE INDEX idx_attack_source_customer ON attack_source_weights(customer_id);
CREATE INDEX idx_attack_source_segment_type ON attack_source_weights(segment_type);
CREATE INDEX idx_attack_source_active ON attack_source_weights(is_active);

-- 蜜罐表索引
CREATE INDEX idx_honeypot_customer ON honeypot_sensitivity_weights(customer_id);
CREATE INDEX idx_honeypot_zone ON honeypot_sensitivity_weights(deployment_zone);
CREATE INDEX idx_honeypot_tier ON honeypot_sensitivity_weights(honeypot_tier);
CREATE INDEX idx_honeypot_active ON honeypot_sensitivity_weights(is_active);
```

**应用层缓存**（已实现）:
```java
@Cacheable(value="attackSourceWeights", key="#customerId + ':' + #attackIp")
public BigDecimal getAttackSourceWeight(String customerId, String attackIp) {
    // 缓存1小时，减少数据库查询
}
```

### 3. 监控告警

**关键指标**:
- 配置查询响应时间 < 10ms (Redis缓存命中)
- 配置查询响应时间 < 100ms (数据库查询)
- 未匹配配置比例 < 5% (需要添加更多配置)
- 威胁评分准确性（需要人工审核）

**Prometheus指标**（TODO）:
```java
@Timed("ip_segment_weight_query_duration")
@Counted("ip_segment_weight_query_total")
public BigDecimal getAttackSourceWeight(...) { ... }
```

### 4. 数据备份

**推荐策略**:
```bash
# 每日全量备份
docker exec postgres pg_dump -U threat_user threat_detection \
    -t attack_source_weights \
    -t honeypot_sensitivity_weights \
    > backup/ip_weights_$(date +%Y%m%d).sql

# 版本控制
git add backup/ip_weights_*.sql
git commit -m "Backup: IP segment weights configuration"
```

### 5. 变更管理

**配置变更流程**:
1. 在Git中维护配置文件（CSV/JSON格式）
2. 代码审查配置变更
3. 在测试环境验证
4. 生成SQL迁移脚本
5. 在生产环境执行（带审计日志）
6. 验证并监控

---

## 下一步行动

### 1. 立即行动
- [ ] 运行部署脚本验证V4.0系统
- [ ] 确认所有4个测试场景通过
- [ ] 检查应用日志无错误

### 2. 集成开发（本周）
- [ ] 更新ThreatScoreCalculator集成V4.0
- [ ] 实现端到端测试
- [ ] 更新API文档

### 3. 功能增强（下周）
- [ ] 开发配置管理API
- [ ] 实现批量导入/导出
- [ ] 添加Prometheus监控指标

### 4. 生产准备（2周内）
- [ ] 准备生产迁移脚本
- [ ] 编写运维手册
- [ ] 进行压力测试

---

## 参考文档

- **设计文档**: `docs/design/honeypot_based_threat_scoring.md`
- **理解修正**: `docs/design/understanding_corrections_summary.md`
- **原SQL脚本**: `docker/12-ip-segment-weights-v4.sql`
- **服务实现**: `services/threat-assessment/.../service/IpSegmentWeightServiceV4.java`
- **单元测试**: `services/threat-assessment/.../service/IpSegmentWeightServiceV4Test.java`

---

**文档版本**: 1.0  
**最后更新**: 2025-10-24  
**维护者**: ThreatDetection Team
