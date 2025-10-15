# 🚀 MVP Phase 0: 快速测试指南

## 立即开始测试

### 1️⃣ 启动服务 (2分钟)

```bash
cd docker
docker-compose up -d postgres kafka zookeeper
```

等待服务就绪：
```bash
# 查看服务状态
docker-compose ps

# 应显示:
# postgres    running    0.0.0.0:5432->5432/tcp
# kafka       running    0.0.0.0:9092->9092/tcp
# zookeeper   running    0.0.0.0:2181->2181/tcp
```

### 2️⃣ 验证端口权重配置 (30秒)

```bash
# 检查端口权重表
docker-compose exec postgres psql -U threat_user -d threat_detection -c "SELECT COUNT(*), MIN(port_number), MAX(port_number) FROM port_risk_configs;"

# 预期输出: 至少50+条配置记录
```

### 3️⃣ 运行单元测试 (10秒)

```bash
cd ../scripts/test
python3 unit_test_mvp.py
```

**✅ 预期**: 8个测试全部通过

### 4️⃣ 启动流处理 (可选)

```bash
cd ../../docker
docker-compose up -d stream-processing
```

### 5️⃣ 运行端到端测试 (5分钟)

```bash
cd ../scripts/test
./run_mvp_tests.sh e2e
```

---

## 📊 测试数据说明

### 真实测试日志
- **位置**: `tmp/real_test_logs/`
- **格式**: JSON格式的syslog消息
- **内容**: 来自蜜罐设备的真实诱捕攻击日志

### 关键字段理解 ⚠️

```json
{
  "attack_mac": "8c:94:6a:27:14:01",    // 被诱捕的内网失陷主机MAC
  "attack_ip": "10.68.77.107",          // 被诱捕的内网主机IP
  "response_ip": "10.68.9.111",         // 诱饵IP (不存在的虚拟哨兵)
  "response_port": 161                  // 攻击意图: SNMP网络管理探测
}
```

**核心理解**: 这是蜜罐系统，`response_ip`是诱饵，任何访问都是恶意行为！

---

## 🎯 核心测试点

| 功能 | 测试方法 | 预期结果 |
|------|---------|---------|
| **蜜罐机制理解** | 单元测试 | ✓ 描述正确 |
| **3层时间窗口** | 端到端测试 | ✓ Tier1/2/3告警 |
| **端口权重** | 数据库查询 | ✓ 50+配置 |
| **威胁评分** | 单元测试 | ✓ 公式正确 |
| **端到端流** | 完整测试 | ✓ >80%成功率 |

---

## 🔍 快速验证命令

```bash
# 1. 检查Kafka主题
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# 2. 查看最近威胁评估
docker-compose exec postgres psql -U threat_user -d threat_detection -c "SELECT COUNT(*), MAX(threat_score), AVG(threat_score) FROM threat_assessments WHERE assessment_time > NOW() - INTERVAL '1 hour';"

# 3. 查看高危端口配置
docker-compose exec postgres psql -U threat_user -d threat_detection -c "SELECT port_number, port_name, risk_weight FROM port_risk_configs WHERE risk_level='CRITICAL' ORDER BY risk_weight DESC LIMIT 10;"

# 4. 统计测试日志
wc -l ../tmp/real_test_logs/*.log
grep -h "log_type=1" ../tmp/real_test_logs/*.log | wc -l
```

---

## ❓ 常见问题

### Q: Kafka连接失败？
```bash
# 重启Kafka
docker-compose restart kafka zookeeper
# 等待30秒后重试
```

### Q: PostgreSQL连接失败？
```bash
# 重启PostgreSQL
docker-compose restart postgres
# 检查日志
docker-compose logs postgres
```

### Q: 端口权重表为空？
```bash
# 手动执行迁移
docker cp port_weights_migration.sql postgres:/tmp/
docker-compose exec postgres psql -U threat_user -d threat_detection -f /tmp/port_weights_migration.sql
```

### Q: 测试日志在哪？
- 确保 `tmp/real_test_logs/` 目录存在
- 包含 `.log` 文件（JSON格式）

---

## 📖 详细文档

- [完整测试指南](MVP_TEST_GUIDE.md)
- [项目使用指南](../../USAGE_GUIDE.md)
- [Copilot指令](../../.github/copilot-instructions.md)

---

**测试愉快！** 🎉

如遇问题，请检查Docker服务状态和日志。
