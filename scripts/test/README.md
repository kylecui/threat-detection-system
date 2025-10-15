# 🧪 测试套件总览

## 测试结构

```
scripts/test/
├── 📘 QUICK_START.md                    # 5分钟快速测试指南
├── 📗 MVP_TEST_GUIDE.md                 # 完整测试文档（400+行）
├── 📊 TEST_IMPLEMENTATION_SUMMARY.md    # 实施总结与结果
│
├── 🧪 unit_test_mvp.py                  # 单元测试（✅ 8/8通过）
├── 🚀 e2e_mvp_test.py                   # 端到端功能测试
└── 🛠️  run_mvp_tests.sh                 # 统一测试入口
```

---

## 🎯 测试目标

验证MVP Phase 0核心功能：

1. ✅ **蜜罐机制理解** - 正确理解responseIp=诱饵IP
2. ✅ **3层时间窗口** - 30s/5min/15min多层检测
3. ✅ **端口权重系统** - CVSS + 经验权重混合
4. ✅ **威胁评分算法** - 与原系统对齐的完整公式
5. ⏳ **端到端数据流** - Kafka → Flink → PostgreSQL

---

## ⚡ 快速开始

### 1️⃣ 单元测试（10秒）

```bash
python3 unit_test_mvp.py
```

**结果**: ✅ 8/8 测试通过

### 2️⃣ 完整测试（5分钟）

```bash
# 启动服务
cd ../../docker
docker-compose up -d

# 运行测试
cd ../scripts/test
./run_mvp_tests.sh all
```

---

## 📚 文档导航

### 新手入门
👉 **[QUICK_START.md](QUICK_START.md)** - 5步快速测试指南

### 完整测试
👉 **[MVP_TEST_GUIDE.md](MVP_TEST_GUIDE.md)** - 详细测试文档
- 环境准备
- 测试场景
- 结果验证
- 故障排查

### 开发者
👉 **[TEST_IMPLEMENTATION_SUMMARY.md](TEST_IMPLEMENTATION_SUMMARY.md)** - 实施细节
- 技术实现
- 代码变更
- 测试结果
- 对齐分析

---

## 🧪 测试类型

### 单元测试 (`unit_test_mvp.py`)

**测试类**:
- `PortWeightTests` - 端口权重计算
- `ThreatScoreTests` - 威胁评分算法
- `MultiTierWindowTests` - 多层窗口逻辑

**测试方法**: 8个
**状态**: ✅ 全部通过

### 端到端测试 (`e2e_mvp_test.py`)

**测试流程**:
1. 加载真实测试日志 (`tmp/real_test_logs/`)
2. 发送事件到Kafka (`attack-events`)
3. 等待Flink流处理
4. 消费威胁告警 (`threat-alerts`)
5. 验证PostgreSQL记录

**状态**: ⏳ 待执行（需要Docker服务）

---

## 📊 测试数据

### 真实日志
- **位置**: `../../tmp/real_test_logs/`
- **格式**: JSON格式syslog
- **内容**: 蜜罐诱捕的真实攻击日志

### 高危端口示例
```
端口  22: SSH远程控制        (权重 10.0)
端口 161: SNMP网络管理       (权重  7.5)
端口 445: SMB横向移动        (权重  9.5)
端口3389: RDP远程桌面        (权重 10.0)
端口3306: MySQL数据库攻击    (权重  9.0)
```

---

## 🎓 核心概念

### 蜜罐机制 ⚠️ 重要
```
attack_mac:     被诱捕的内网失陷主机MAC
attack_ip:      被诱捕的内网主机IP  
response_ip:    诱饵IP（不存在的虚拟哨兵）
response_port:  攻击意图端口（暴露攻击目的）
```

**关键理解**: 任何访问诱饵IP的行为都是恶意的！

### 3层时间窗口
```
Tier-1 (30秒):   勒索软件快速检测    权重 1.5
Tier-2 (5分钟):  主要威胁检测        权重 1.0  
Tier-3 (15分钟): APT慢速扫描        权重 1.2
```

### 威胁评分公式
```java
threatScore = (attackCount × uniqueIps × uniquePorts) 
            × timeWeight × ipWeight × portWeight 
            × deviceWeight × tierWeight
```

---

## ✅ 测试结果

| 测试类型 | 状态 | 结果 |
|---------|------|------|
| 单元测试 | ✅ 完成 | 8/8 通过 |
| 端到端测试 | ⏳ 待执行 | 需要Docker服务 |
| 性能测试 | 📋 计划中 | 下一阶段 |

---

## 🚀 下一步

### 立即执行
```bash
# 1. 启动服务
cd ../../docker
docker-compose up -d postgres kafka zookeeper stream-processing

# 2. 运行完整测试
cd ../scripts/test
./run_mvp_tests.sh e2e

# 3. 查看结果
docker-compose exec postgres psql -U threat_user -d threat_detection \
  -c "SELECT COUNT(*), AVG(threat_score) FROM threat_assessments;"
```

### 后续计划
- [ ] 端到端测试验证
- [ ] 性能测试（吞吐量、延迟）
- [ ] 补充完整端口配置（219个）
- [ ] 实现网段权重系统

---

## 📞 获取帮助

### 常见问题
- Kafka连接失败 → 检查服务状态
- PostgreSQL连接失败 → 查看日志
- 端口权重表为空 → 执行迁移脚本

### 详细文档
- 测试问题: [MVP_TEST_GUIDE.md](MVP_TEST_GUIDE.md#故障排查)
- 快速修复: [QUICK_START.md](QUICK_START.md#常见问题)
- 技术细节: [TEST_IMPLEMENTATION_SUMMARY.md](TEST_IMPLEMENTATION_SUMMARY.md)

---

**测试套件版本**: 1.0  
**最后更新**: 2025-10-14  
**状态**: 单元测试完成 ✓ | 端到端测试就绪 ⏳
