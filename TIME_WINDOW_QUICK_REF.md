# ⚙️ 时间窗口配置快速参考

## 🎯 一句话说明
**通过环境变量自定义3层威胁检测时间窗口的时长**

---

## 📋 快速配置

### 修改 `docker/docker-compose.yml`
```yaml
stream-processing:
  environment:
    # 时间窗口时长（单位：秒）
    TIER1_WINDOW_SECONDS: "30"     # 推荐范围: 10-300秒
    TIER2_WINDOW_SECONDS: "300"    # 推荐范围: 60-1800秒
    TIER3_WINDOW_SECONDS: "900"    # 推荐范围: 300-7200秒
    
    # 窗口名称（可选，用于告警描述）
    TIER1_WINDOW_NAME: "勒索软件快速检测"
    TIER2_WINDOW_NAME: "主要威胁检测"
    TIER3_WINDOW_NAME: "APT慢速扫描检测"
```

### 应用配置
```bash
cd ~/threat-detection-system/docker
docker compose up -d --force-recreate stream-processing
```

---

## 🔢 默认值与推荐范围

| 层级 | 默认值 | 说明 | 最小值 | 推荐范围 | 推荐最大值 |
|------|--------|------|--------|---------|----------|
| Tier 1 | 30秒 | 勒索软件快速检测 | 10秒 | 10-300秒 | 300秒 (5分钟) |
| Tier 2 | 300秒 | 主要威胁检测 | 10秒 | 60-1800秒 | 1800秒 (30分钟) |
| Tier 3 | 900秒 | APT慢速扫描 | 10秒 | 300-7200秒 | 7200秒 (2小时) |

### 窗口名称（可选配置）

| 层级 | 默认名称 | 环境变量 |
|------|---------|----------|
| Tier 1 | 勒索软件快速检测 | `TIER1_WINDOW_NAME` |
| Tier 2 | 主要威胁检测 | `TIER2_WINDOW_NAME` |
| Tier 3 | APT慢速扫描检测 | `TIER3_WINDOW_NAME` |

---

## 🎨 预定义场景

### 快速响应 (高安全环境)
```yaml
TIER1_WINDOW_SECONDS: "15"
TIER2_WINDOW_SECONDS: "120"
TIER3_WINDOW_SECONDS: "300"
```

### 平衡配置 (大型网络)
```yaml
TIER1_WINDOW_SECONDS: "60"
TIER2_WINDOW_SECONDS: "600"
TIER3_WINDOW_SECONDS: "1800"
```

### 测试环境
```yaml
TIER1_WINDOW_SECONDS: "10"
TIER2_WINDOW_SECONDS: "60"
TIER3_WINDOW_SECONDS: "180"
```

---

## ✅ 验证配置

```bash
# 查看配置
docker logs stream-processing 2>&1 | grep "时间窗口配置"

# 预期输出
==================== 时间窗口配置 ====================
Tier 1 窗口: 30 秒 (勒索软件快速检测)
Tier 2 窗口: 300 秒 (5 分钟) (主要威胁检测)
Tier 3 窗口: 900 秒 (15 分钟) (APT慢速扫描检测)
=====================================================
```

---

## 🧪 测试工具

```bash
cd ~/threat-detection-system/scripts

# 测试默认配置
./test_custom_time_windows.sh default

# 测试快速响应配置
./test_custom_time_windows.sh fast

# 自定义配置测试
./test_custom_time_windows.sh custom 45 450 1350
```

---

## ⚠️ 重要提示

### 配置限制
1. **最小值限制** (强制): 所有窗口 >= 10秒，违反将启动失败
2. **推荐最大值** (警告): 
   - Tier 1 <= 300秒 (5分钟)
   - Tier 2 <= 1800秒 (30分钟)
   - Tier 3 <= 7200秒 (2小时)
   - 超过推荐最大值会产生警告但不影响启动
3. **重启需求**: 修改后必须重启 `stream-processing` 服务
4. **性能影响**: 缩短窗口会增加告警频率和系统负载
5. **推荐配置**: 保持递增关系 (Tier1 <= Tier2 <= Tier3)

### 窗口名称说明
- 名称会显示在告警描述和系统日志中
- 支持中文、英文或自定义业务术语
- 不配置时使用默认名称

---

## 📚 完整文档

详见: [docs/guides/TIME_WINDOW_CONFIGURATION.md](../guides/TIME_WINDOW_CONFIGURATION.md)

---

**版本**: 1.0 | **更新**: 2025-10-27
