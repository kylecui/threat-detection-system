# 时间窗口自定义配置指南

## 📋 概述

威胁检测系统使用3层时间窗口架构，每层窗口的时长现在都支持通过环境变量自定义配置。

## 🎯 3层时间窗口说明

### 时间窗口配置

| 层级 | 默认窗口 | 检测目标 | 环境变量 | 最小值 | 推荐范围 | 推荐最大值 |
|------|---------|---------|----------|--------|---------|----------|
| **Tier 1** | 30秒 | 勒索软件快速检测 | `TIER1_WINDOW_SECONDS` | 10秒 | 10-300秒 | 300秒 (5分钟) |
| **Tier 2** | 5分钟 | 主要威胁检测 | `TIER2_WINDOW_SECONDS` | 10秒 | 60-1800秒 | 1800秒 (30分钟) |
| **Tier 3** | 15分钟 | APT慢速扫描检测 | `TIER3_WINDOW_SECONDS` | 10秒 | 300-7200秒 | 7200秒 (2小时) |

### 窗口名称配置

| 层级 | 默认名称 | 环境变量 | 用途 |
|------|---------|----------|------|
| **Tier 1** | 勒索软件快速检测 | `TIER1_WINDOW_NAME` | 告警描述和日志显示 |
| **Tier 2** | 主要威胁检测 | `TIER2_WINDOW_NAME` | 告警描述和日志显示 |
| **Tier 3** | APT慢速扫描检测 | `TIER3_WINDOW_NAME` | 告警描述和日志显示 |

**💡 说明**:
- **最小值 (10秒)**: 强制限制，小于此值服务启动失败
- **推荐范围**: 基于实际威胁检测经验的最佳实践
- **推荐最大值**: 超过此值系统会产生警告（不阻止启动）

## ⚙️ 配置方法

### 方法1: 修改 docker-compose.yml（推荐）

编辑 `docker/docker-compose.yml` 中的 `stream-processing` 服务配置：

```yaml
stream-processing:
  environment:
    # ... 其他配置 ...
    
    # 时间窗口时长配置（单位：秒）
    TIER1_WINDOW_SECONDS: "30"        # Tier 1窗口时长（推荐: 10-300秒）
    TIER2_WINDOW_SECONDS: "300"       # Tier 2窗口时长（推荐: 60-1800秒）
    TIER3_WINDOW_SECONDS: "900"       # Tier 3窗口时长（推荐: 300-7200秒）
    
    # 窗口名称配置（可选，用于告警描述）
    TIER1_WINDOW_NAME: "勒索软件快速检测"
    TIER2_WINDOW_NAME: "主要威胁检测"
    TIER3_WINDOW_NAME: "APT慢速扫描检测"
```

### 方法2: 环境变量文件

创建 `.env` 文件：

```bash
# 时间窗口时长配置
TIER1_WINDOW_SECONDS=30
TIER2_WINDOW_SECONDS=300
TIER3_WINDOW_SECONDS=900

# 窗口名称配置（可选）
TIER1_WINDOW_NAME="快速响应层"
TIER2_WINDOW_NAME="主检测层"
TIER3_WINDOW_NAME="长期观察层"
```

然后在 `docker-compose.yml` 中引用：

```yaml
stream-processing:
  env_file:
    - .env
```

### 方法3: 命令行启动

```bash
docker run -e TIER1_WINDOW_SECONDS=60 \
           -e TIER2_WINDOW_SECONDS=600 \
           -e TIER3_WINDOW_SECONDS=1800 \
           -e TIER1_WINDOW_NAME="自定义快速检测" \
           -e TIER2_WINDOW_NAME="自定义主检测" \
           -e TIER3_WINDOW_NAME="自定义长期检测" \
           stream-processing:latest
```

---

## 📏 推荐值说明

### 为什么有这些推荐值？

#### Tier 1 窗口推荐范围: 10-300秒

**设计目标**: 快速响应勒索软件等高速威胁

| 配置 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **10-30秒** | 极速响应，检测延迟最短 | 告警频繁，噪声较多 | 高安全要求环境（金融、医疗） |
| **30-60秒** ✅ | 平衡响应速度和准确性 | - | 一般企业环境（推荐） |
| **60-300秒** | 告警质量高，噪声少 | 响应速度较慢 | 大型网络，流量较大 |
| **>300秒** ⚠️ | - | 失去快速检测意义 | 不推荐 |

**推荐最大值 (300秒)**: 超过5分钟后，Tier 1已经失去"快速响应"的特性，应使用Tier 2。

---

#### Tier 2 窗口推荐范围: 60-1800秒

**设计目标**: 主要威胁检测，平衡速度和准确性

| 配置 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **60-300秒** | 快速检测，适度聚合 | 可能遗漏慢速扫描 | 需要快速响应的中小型网络 |
| **300-600秒** ✅ | 良好的检测覆盖 | - | 一般企业环境（推荐） |
| **600-1800秒** | 全面检测，噪声极少 | 检测延迟较长 | 大型企业，流量巨大 |
| **>1800秒** ⚠️ | - | 告警延迟过长 | 特殊研究场景 |

**推荐最大值 (1800秒)**: 超过30分钟后，告警延迟过长，可能影响应急响应效果。

---

#### Tier 3 窗口推荐范围: 300-7200秒

**设计目标**: APT慢速扫描检测，长期观察

| 配置 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **300-900秒** | 适度长期观察 | 可能遗漏极慢APT | 一般APT检测需求 |
| **900-1800秒** ✅ | 良好的APT检测覆盖 | - | 一般企业环境（推荐） |
| **1800-7200秒** | 极全面的APT检测 | 告警延迟很长 | 高价值目标，重点监控 |
| **>7200秒** ⚠️ | - | 2小时以上延迟不实用 | 离线分析场景 |

**推荐最大值 (7200秒)**: 超过2小时后，已经接近离线分析的时间尺度，实时性意义不大。

---

### 窗口名称自定义场景

#### 场景1: 业务语言对齐

```yaml
# 金融行业示例
TIER1_WINDOW_NAME: "交易异常快速响应"
TIER2_WINDOW_NAME: "账户安全监控"
TIER3_WINDOW_NAME: "长期风险分析"
```

#### 场景2: 多语言环境

```yaml
# 英文环境
TIER1_WINDOW_NAME: "Rapid Response Detection"
TIER2_WINDOW_NAME: "Main Threat Detection"
TIER3_WINDOW_NAME: "APT Long-term Monitoring"
```

#### 场景3: 特定威胁聚焦

```yaml
# 针对特定威胁的自定义
TIER1_WINDOW_NAME: "勒索病毒实时拦截"
TIER2_WINDOW_NAME: "横向移动检测"
TIER3_WINDOW_NAME: "数据渗漏监控"
```

---

### 方法1: 修改 docker-compose.yml（推荐）

编辑 `docker/docker-compose.yml` 中的 `stream-processing` 服务配置：

```yaml
stream-processing:
  environment:
    # ... 其他配置 ...
    
    # 时间窗口时长配置（单位：秒）
    TIER1_WINDOW_SECONDS: "30"        # Tier 1窗口时长（推荐: 10-300秒）
    TIER2_WINDOW_SECONDS: "300"       # Tier 2窗口时长（推荐: 60-1800秒）
    TIER3_WINDOW_SECONDS: "900"       # Tier 3窗口时长（推荐: 300-7200秒）
    
    # 窗口名称配置（可选，用于告警描述）
    TIER1_WINDOW_NAME: "勒索软件快速检测"
    TIER2_WINDOW_NAME: "主要威胁检测"
    TIER3_WINDOW_NAME: "APT慢速扫描检测"
```

### 方法2: 环境变量文件

创建 `.env` 文件：

```bash
# 时间窗口配置
TIER1_WINDOW_SECONDS=30
TIER2_WINDOW_SECONDS=300
TIER3_WINDOW_SECONDS=900
```

然后在 `docker-compose.yml` 中引用：

```yaml
stream-processing:
  env_file:
    - .env
```

### 方法3: 命令行启动

```bash
docker run -e TIER1_WINDOW_SECONDS=60 \
           -e TIER2_WINDOW_SECONDS=600 \
           -e TIER3_WINDOW_SECONDS=1800 \
           stream-processing:latest
```

## 📊 配置示例

### 示例1: 提高勒索软件检测灵敏度

**场景**: 勒索软件攻击非常迅速，需要更快速的响应

```yaml
TIER1_WINDOW_SECONDS: "15"        # 从30秒缩短到15秒
TIER2_WINDOW_SECONDS: "300"       # 保持5分钟不变
TIER3_WINDOW_SECONDS: "900"       # 保持15分钟不变
```

**效果**: 
- ✅ 勒索软件告警速度提升50%
- ⚠️ 告警频率增加，可能产生更多低分告警

---

### 示例2: 延长APT检测窗口

**场景**: APT攻击非常隐蔽，需要更长的观察周期

```yaml
TIER1_WINDOW_SECONDS: "30"        # 保持30秒不变
TIER2_WINDOW_SECONDS: "300"       # 保持5分钟不变
TIER3_WINDOW_SECONDS: "1800"      # 从15分钟延长到30分钟
```

**效果**:
- ✅ 更全面的APT行为模式捕获
- ⚠️ APT告警延迟增加15分钟

---

### 示例3: 平衡配置（低流量环境）

**场景**: 网络流量较小，攻击事件稀疏

```yaml
TIER1_WINDOW_SECONDS: "60"        # 延长到1分钟
TIER2_WINDOW_SECONDS: "600"       # 延长到10分钟
TIER3_WINDOW_SECONDS: "1800"      # 延长到30分钟
```

**效果**:
- ✅ 减少噪声告警
- ✅ 聚合更多事件，提升威胁评分准确性
- ⚠️ 告警延迟增加

---

### 示例4: 高频检测（高危环境）

**场景**: 关键业务系统，需要极快响应

```yaml
TIER1_WINDOW_SECONDS: "15"        # 缩短到15秒
TIER2_WINDOW_SECONDS: "120"       # 缩短到2分钟
TIER3_WINDOW_SECONDS: "300"       # 缩短到5分钟
```

**效果**:
- ✅ 极速威胁响应
- ⚠️ 告警量大幅增加，需要更强的分析能力

## 🔧 应用配置

### 步骤1: 修改配置

编辑 `docker/docker-compose.yml`，修改环境变量。

### 步骤2: 重新构建和启动

```bash
cd ~/threat-detection-system/docker

# 停止服务
docker compose down stream-processing

# 重新构建镜像（如果代码有变化）
docker compose build stream-processing

# 启动服务
docker compose up -d stream-processing
```

### 步骤3: 验证配置

检查服务日志，确认配置已生效：

```bash
docker logs stream-processing 2>&1 | grep "时间窗口配置"
```

**预期输出**:
```
==================== 时间窗口配置 ====================
Tier 1 窗口: 30 秒 (勒索软件快速检测)
Tier 2 窗口: 300 秒 (5 分钟) (主要威胁检测)
Tier 3 窗口: 900 秒 (15 分钟) (APT慢速扫描检测)
=====================================================
✅ 3层时间窗口处理器已启动: Tier1=30s, Tier2=300s(5min), Tier3=900s(15min)
```

## ⚠️ 配置限制和验证

### 强制限制（违反将导致启动失败）

#### 最小窗口限制

所有窗口必须 **>= 10秒**，否则服务启动失败：

```
❌ 错误配置：
TIER1_WINDOW_SECONDS: "5"

✅ 启动失败日志：
IllegalArgumentException: Tier 1 窗口(5秒)不能小于最小窗口限制(10秒)
```

**原因**: 过小的窗口会导致：
- 极高的计算负载（频繁触发窗口）
- 大量低质量告警（数据不足）
- 系统资源耗尽风险

---

### 推荐限制（违反将产生警告）

#### 推荐最大值警告

超过推荐最大值时，系统会产生警告但仍然启动：

```yaml
# ⚠️ 超过推荐最大值的配置
TIER1_WINDOW_SECONDS: "600"      # 超过300秒推荐最大值
TIER2_WINDOW_SECONDS: "3600"     # 超过1800秒推荐最大值
TIER3_WINDOW_SECONDS: "14400"    # 超过7200秒推荐最大值
```

**警告日志示例**:
```
⚠️  Tier 1 窗口(600 秒 = 10 分钟)超过推荐最大值(300 秒 = 5 分钟), 可能影响快速检测效果
⚠️  Tier 2 窗口(3600 秒 = 60 分钟)超过推荐最大值(1800 秒 = 30 分钟), 可能延迟告警
⚠️  Tier 3 窗口(14400 秒 = 240 分钟)超过推荐最大值(7200 秒 = 120 分钟), 检测延迟较长
```

**影响**:
- ✅ 系统可以正常运行
- ⚠️ 可能不符合设计初衷
- ⚠️ 告警延迟显著增加
- ⚠️ 偏离最佳实践

---

### 窗口递增建议

系统会检查窗口是否递增，非递增配置会产生警告：

```yaml
# ⚠️ 不推荐：Tier 2 小于 Tier 1
TIER1_WINDOW_SECONDS: "60"
TIER2_WINDOW_SECONDS: "30"   # 警告！

# ✅ 推荐：递增配置
TIER1_WINDOW_SECONDS: "30"
TIER2_WINDOW_SECONDS: "300"
TIER3_WINDOW_SECONDS: "900"
```

**警告日志**:
```
⚠️  Tier 2 窗口(30 秒)小于 Tier 1 窗口(60 秒), 这可能不是预期配置
⚠️  Tier 3 窗口(300 秒)小于 Tier 2 窗口(600 秒), 这可能不是预期配置
```

---

### 配置验证总结

| 验证类型 | 最小值 | 推荐最大值 | 违反后果 |
|---------|--------|-----------|---------|
| **Tier 1** | 10秒（强制） | 300秒（建议） | <10秒启动失败，>300秒警告 |
| **Tier 2** | 10秒（强制） | 1800秒（建议） | <10秒启动失败，>1800秒警告 |
| **Tier 3** | 10秒（强制） | 7200秒（建议） | <10秒启动失败，>7200秒警告 |
| **递增关系** | - | - | 非递增仅警告，不影响启动 |

## 📊 性能影响分析

### 缩短窗口的影响

| 指标 | 影响 | 建议 |
|------|------|------|
| **告警频率** | ⬆️ 显著增加 | 确保数据库和网络容量充足 |
| **告警延迟** | ⬇️ 减少 | 适合需要快速响应的场景 |
| **威胁评分** | ⬇️ 可能降低 | 窗口内事件较少，评分可能偏低 |
| **CPU使用率** | ⬆️ 略微增加 | Flink处理更多窗口触发事件 |
| **内存使用** | ➡️ 基本不变 | 单窗口数据量减少 |

### 延长窗口的影响

| 指标 | 影响 | 建议 |
|------|------|------|
| **告警频率** | ⬇️ 减少 | 适合低流量环境 |
| **告警延迟** | ⬆️ 增加 | 不适合需要快速响应的场景 |
| **威胁评分** | ⬆️ 可能提升 | 窗口内聚合更多事件 |
| **CPU使用率** | ⬇️ 略微减少 | 窗口触发频率降低 |
| **内存使用** | ⬆️ 可能增加 | 单窗口数据量增大 |

## 🧪 测试配置

### 快速测试脚本

创建测试配置并验证：

```bash
#!/bin/bash
# test_custom_windows.sh

echo "📝 测试自定义时间窗口配置"

# 1. 备份原配置
cp docker/docker-compose.yml docker/docker-compose.yml.backup

# 2. 设置测试窗口（1分钟/10分钟/30分钟）
export TIER1_WINDOW_SECONDS=60
export TIER2_WINDOW_SECONDS=600
export TIER3_WINDOW_SECONDS=1800

# 3. 重启服务
docker compose down stream-processing
docker compose up -d stream-processing

# 4. 等待启动
sleep 10

# 5. 验证配置
echo "🔍 验证窗口配置："
docker logs stream-processing 2>&1 | grep -A 5 "时间窗口配置"

# 6. 发送测试事件
cd scripts
bash test_v4_phase2_dual_dimension.sh

# 7. 持续监控告警（持续10分钟）
echo "📊 监控告警生成（等待10分钟以观察Tier 2窗口）..."
for i in {1..10}; do
    sleep 60
    echo "[$i/10分钟] 告警统计："
    docker exec -i postgres psql -U threat_user -d threat_detection -c "
    SELECT 
        substring(metadata from 'tier\":([0-9]+)') as tier,
        COUNT(*) as count
    FROM alerts 
    WHERE created_at > NOW() - INTERVAL '15 minutes'
    GROUP BY tier
    ORDER BY tier;
    "
done

echo "✅ 测试完成"
```

### 验证窗口触发时间

```bash
# 查询不同窗口的告警生成时间分布
docker exec -i postgres psql -U threat_user -d threat_detection -c "
SELECT 
    substring(metadata from 'tier\":([0-9]+)') as tier,
    DATE_TRUNC('minute', created_at) as minute,
    COUNT(*) as alert_count
FROM alerts 
WHERE created_at > NOW() - INTERVAL '1 hour'
GROUP BY tier, minute
ORDER BY tier, minute;
"
```

## 🔍 故障排查

### 问题1: 配置未生效

**症状**: 修改环境变量后，窗口时长未变化

**排查步骤**:
```bash
# 1. 检查容器环境变量
docker exec stream-processing env | grep TIER

# 2. 检查日志中的窗口配置
docker logs stream-processing 2>&1 | grep "时间窗口配置"

# 3. 确认容器已重启
docker compose ps stream-processing
```

**解决方法**:
```bash
# 强制重新创建容器
docker compose up -d --force-recreate stream-processing
```

---

### 问题2: 服务启动失败

**症状**: stream-processing服务无法启动

**排查步骤**:
```bash
# 查看错误日志
docker logs stream-processing --tail 50

# 检查是否违反配置限制
```

**常见错误**:
```
IllegalArgumentException: Tier 1 窗口(5秒)不能小于最小窗口限制(10秒)
```

**解决方法**: 修改配置，确保窗口 >= 10秒

---

### 问题3: 告警数量异常

**症状**: 缩短窗口后，告警数量没有明显增加

**排查步骤**:
```bash
# 1. 检查是否有新事件进入
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic attack-events \
  --max-messages 10

# 2. 检查Flink作业状态
curl -s http://localhost:8081/jobs | jq

# 3. 检查最近告警时间
docker exec -i postgres psql -U threat_user -d threat_detection -c "
SELECT MAX(created_at) as latest_alert FROM alerts;
"
```

## 📚 相关文档

- [3层时间窗口机制说明](../../MULTI_TIER_WINDOW_EXPLANATION.md)
- [系统使用指南](./USAGE_GUIDE.md)
- [架构设计规范](../design/new_system_architecture_spec.md)
- [容器重建流程](./CONTAINER_REBUILD_WORKFLOW.md)

## 📊 推荐配置参考

### 默认配置（平衡型）

```yaml
TIER1_WINDOW_SECONDS: "30"      # 30秒
TIER2_WINDOW_SECONDS: "300"     # 5分钟
TIER3_WINDOW_SECONDS: "900"     # 15分钟
```

**适用场景**: 一般企业网络，流量适中

---

### 高敏感配置（安全优先）

```yaml
TIER1_WINDOW_SECONDS: "15"      # 15秒
TIER2_WINDOW_SECONDS: "120"     # 2分钟
TIER3_WINDOW_SECONDS: "300"     # 5分钟
```

**适用场景**: 金融、医疗等高安全要求行业

---

### 低噪声配置（大型网络）

```yaml
TIER1_WINDOW_SECONDS: "60"      # 1分钟
TIER2_WINDOW_SECONDS: "600"     # 10分钟
TIER3_WINDOW_SECONDS: "1800"    # 30分钟
```

**适用场景**: 大型企业网络，流量巨大，需要过滤噪声

---

### 研究配置（实验环境）

```yaml
TIER1_WINDOW_SECONDS: "10"      # 10秒（最小值）
TIER2_WINDOW_SECONDS: "60"      # 1分钟
TIER3_WINDOW_SECONDS: "180"     # 3分钟
```

**适用场景**: 测试环境，快速验证功能

## 🎯 未来增强计划

- [ ] **多租户独立配置**: 每个客户可配置不同的时间窗口
- [ ] **动态调整**: 支持运行时修改窗口配置（无需重启）
- [ ] **智能窗口**: 基于流量模式自动调整窗口大小
- [ ] **配置模板**: 预定义行业最佳实践配置模板
- [ ] **配置API**: 通过REST API管理时间窗口配置

---

**文档版本**: 1.0  
**最后更新**: 2025-10-27  
**维护者**: 威胁检测系统开发团队
