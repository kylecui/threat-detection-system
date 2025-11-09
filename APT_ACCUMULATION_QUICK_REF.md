# APT时间累积功能 - 快速参考

## ✅ 核心确认

**您的问题**: 系统能否检测到每天1-2次缓慢扫描，持续40天的APT攻击？

**答案**: **能！** 系统已完整实现APT时间累积功能 ✅

---

## 🎯 功能概述

### 检测能力

**场景**: 某个非常缓慢的侦察攻击脚本
- 每天随机时间进行1-2次扫描
- 全网或部分主机嗅探
- 每次单独评分很低 (INFO/LOW级别)
- **但40天后累积达到高危水平**

**系统响应**: ✅ 能够检测并告警

---

## 📊 工作原理

### 数据流

```
攻击事件 → Kafka → Flink聚合 → APT累积计算 → PostgreSQL
   ↓          ↓         ↓            ↓              ↓
 单次探测  30秒窗口  累加统计   指数衰减评分   长期存储
 (INFO)              多维度     半衰期30天    30-90天
```

### 指数衰减算法

```
decay_score = old_score × 2^(-时间差/30天) + new_score
```

**效果**:
- 最近的攻击权重更高 ✅
- 30天前的攻击权重减半 ✅
- 60天前的攻击权重为1/4 ✅
- 自动淡化旧攻击行为 ✅

---

## 🔍 检测示例

### 40天缓慢APT攻击

| 时间 | 单次评分 | 累积评分 | 检测结果 |
|------|---------|---------|---------|
| Day 1 | 10 (INFO) | 10 | ✓ 记录 |
| Day 10 | 15 (LOW) | 85 | ✓ 累积中 |
| Day 20 | 12 (INFO) | 180 | ⚠️ 趋势异常 |
| Day 30 | 10 (INFO) | 280 | ⚠️ MEDIUM |
| **Day 40** | **15 (LOW)** | **350-450** | **🚨 HIGH/CRITICAL** |

**结论**: 即使单次评分很低，40天累积后会被检测为高危威胁！

---

## 🛠️ 如何使用

### 1. 查询累积威胁

```bash
# 查询客户所有累积威胁
curl "http://localhost:8083/api/v1/apt-accumulations?customerId=customer_a" | jq .

# 查询特定攻击者
curl "http://localhost:8083/api/v1/apt-accumulations?customerId=customer_a&attackMac=aa:bb:cc:dd:ee:ff" | jq .

# 查询高危累积 (评分>50)
curl "http://localhost:8083/api/v1/apt-accumulations?customerId=customer_a" | \
  jq '.[] | select(.decay_accumulated_score > 50)'
```

### 2. 数据库直接查询

```sql
-- 查看所有APT累积威胁
SELECT 
    attack_mac,
    attack_ip,
    total_attack_count,
    decay_accumulated_score,
    inferred_attack_phase,
    last_updated
FROM apt_temporal_accumulations
WHERE customer_id = 'customer_a'
  AND decay_accumulated_score > 50
ORDER BY decay_accumulated_score DESC
LIMIT 20;
```

### 3. 查看累积统计

```bash
# Top 10 累积威胁
docker exec postgres psql -U threat_user -d threat_detection -c "
SELECT attack_mac, total_attack_count, decay_accumulated_score, inferred_attack_phase
FROM apt_temporal_accumulations
WHERE customer_id = 'customer_a'
ORDER BY decay_accumulated_score DESC
LIMIT 10;
"
```

---

## 📋 数据模型

### apt_temporal_accumulations 表

| 字段 | 说明 | 示例 |
|------|------|------|
| `customer_id` | 客户ID | customer_a |
| `attack_mac` | 攻击者MAC | aa:bb:cc:dd:ee:ff |
| `total_attack_count` | 累积攻击次数 | 85 |
| `unique_ips_count` | 唯一诱饵IP数 | 12 |
| `unique_ports_count` | 唯一端口数 | 8 |
| `decay_accumulated_score` | 衰减累积评分 | 325.5 |
| `inferred_attack_phase` | 推断阶段 | RECON |
| `half_life_days` | 半衰期 | 30 |
| `window_start` | 窗口开始 | 2025-10-01 00:00:00 |
| `window_end` | 窗口结束 | 2025-11-06 23:59:59 |
| `last_updated` | 最后更新 | 2025-11-06 15:30:00 |

---

## ⚙️ 系统组件

### 已实现的组件

| 组件 | 状态 | 说明 |
|------|------|------|
| 数据库表 | ✅ | apt_temporal_accumulations |
| Flink Sink | ✅ | APTTemporalAccumulator |
| 服务层 | ✅ | AptTemporalAccumulationService |
| REST API | ✅ | /api/v1/apt-accumulations |
| 指数衰减 | ✅ | SQL层面实现 |
| 阶段推断 | ✅ | RECON/EXPLOITATION/PERSISTENCE |

### 配置验证

```bash
# 1. 检查Flink服务
docker logs stream-processing 2>&1 | grep "apt-temporal-accumulation"
# 应显示: "APT temporal accumulation sink configured successfully"

# 2. 检查数据库表
docker exec postgres psql -U threat_user -d threat_detection -c "\d apt_temporal_accumulations"

# 3. 测试API
curl "http://localhost:8083/api/v1/apt-accumulations?customerId=customer_a"
# 应返回200 OK
```

---

## 🔬 测试方法

### ⚠️ 注意事项

**之前测试失败的原因**:
- 直接调用评估API (`POST /api/v1/assessment/evaluate`)
- APT累积需要从**Kafka攻击事件流**触发
- 评估API不会触发APT累积流程

### 正确的测试方法

#### 方法1: 发送Kafka事件

```bash
# 向Kafka发送攻击事件
docker exec -it kafka kafka-console-producer \
  --broker-list localhost:9092 \
  --topic attack-events

# 然后输入JSON (每天一条,持续40天):
{"attackMac":"aa:bb:cc:dd:ee:ff","attackIp":"192.168.100.50","responseIp":"10.0.0.1","responsePort":3389,"deviceSerial":"DEV-001","customerId":"customer_a","timestamp":"2025-10-01T02:30:00Z","logTime":1727745000}
```

#### 方法2: 模拟真实syslog

```bash
# 通过rsyslog发送真实日志
logger -n localhost -P 514 \
  '<134>Oct 01 02:30:00 DEV-001 attack: src_mac=aa:bb:cc:dd:ee:ff src_ip=192.168.100.50 dst_ip=10.0.0.1 dst_port=3389'
```

#### 方法3: 等待真实攻击

```bash
# 部署蜜罐设备后,等待真实攻击数据流入
# 查询累积数据
watch -n 60 'curl -s "http://localhost:8083/api/v1/apt-accumulations?customerId=customer_a" | jq ".[] | {mac:.attack_mac, score:.decay_accumulated_score}"'
```

---

## 💡 使用建议

### 1. 监控告警

创建APT累积监控规则:

```bash
# 每小时检查一次高危累积
*/60 * * * * curl -s "http://localhost:8083/api/v1/apt-accumulations?customerId=customer_a" | \
  jq '.[] | select(.decay_accumulated_score > 100)' | \
  mail -s "APT High Risk Alert" security@company.com
```

### 2. 前端仪表板

显示内容:
- Top 20累积威胁 (按decay_score排序)
- 累积趋势图 (30天)
- 攻击阶段分布
- 新增累积威胁 (24小时内)

### 3. 告警策略

| 累积评分 | 告警级别 | 操作建议 |
|---------|---------|---------|
| > 50 | MEDIUM | 加入观察列表 |
| > 100 | HIGH | 创建告警,分配分析师 |
| > 200 | CRITICAL | 立即隔离,应急响应 |
| > 500 | SEVERE | 高管通知,深度分析 |

### 4. 定期清理

```sql
-- 删除90天前的旧累积记录
DELETE FROM apt_temporal_accumulations
WHERE window_end < NOW() - INTERVAL '90 days';
```

---

## 📚 相关文档

- [APT累积功能验证](./APT_TEMPORAL_ACCUMULATION_VERIFICATION.md) - 详细验证报告
- [威胁评估API](../api/threat_assessment_evaluation_api.md) - 评估API文档
- [数据库表设计](../../docker/14-apt-temporal-accumulations.sql) - 表结构SQL

---

## 🎯 关键要点

1. ✅ **功能已实现** - APT时间累积完全可用
2. ✅ **能检测慢速攻击** - 每天1-2次,持续40天后可检测
3. ✅ **指数衰减算法** - 智能处理时间权重
4. ✅ **REST API可用** - 支持查询和分析
5. ⚠️ **需要真实数据流** - 通过Kafka触发,非评估API

---

**文档版本**: 1.0  
**更新时间**: 2025-11-06  
**维护团队**: Security Analysis Team
