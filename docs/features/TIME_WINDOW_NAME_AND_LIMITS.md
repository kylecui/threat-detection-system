# 时间窗口配置增强 - 窗口名称自定义 + 推荐值

**更新日期**: 2025-10-27  
**版本**: 1.1  
**类型**: 功能增强

---

## 📋 更新概述

在原有时间窗口时长配置的基础上，新增以下功能：

1. ✅ **窗口名称自定义**: 支持通过环境变量配置每层窗口的名称
2. ✅ **推荐值范围**: 明确各层窗口的推荐最小值和最大值
3. ✅ **配置验证增强**: 添加推荐最大值警告机制

---

## 🆕 新增功能

### 1. 窗口名称自定义

#### 功能说明

每层时间窗口现在可以配置自定义名称，用于：
- 告警描述中显示
- 系统日志中显示
- 更好地与业务语言对齐

#### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `TIER1_WINDOW_NAME` | `勒索软件快速检测` | Tier 1 窗口名称 |
| `TIER2_WINDOW_NAME` | `主要威胁检测` | Tier 2 窗口名称 |
| `TIER3_WINDOW_NAME` | `APT慢速扫描检测` | Tier 3 窗口名称 |

#### 配置示例

```yaml
# docker-compose.yml
stream-processing:
  environment:
    # 窗口名称配置
    TIER1_WINDOW_NAME: "勒索软件快速检测"
    TIER2_WINDOW_NAME: "主要威胁检测"
    TIER3_WINDOW_NAME: "APT慢速扫描检测"
```

#### 应用场景

**场景1: 业务语言对齐**
```yaml
# 金融行业
TIER1_WINDOW_NAME: "交易异常快速响应"
TIER2_WINDOW_NAME: "账户安全监控"
TIER3_WINDOW_NAME: "长期风险分析"
```

**场景2: 多语言环境**
```yaml
# 英文环境
TIER1_WINDOW_NAME: "Rapid Response Detection"
TIER2_WINDOW_NAME: "Main Threat Detection"
TIER3_WINDOW_NAME: "APT Long-term Monitoring"
```

**场景3: 特定威胁聚焦**
```yaml
# 针对特定威胁
TIER1_WINDOW_NAME: "勒索病毒实时拦截"
TIER2_WINDOW_NAME: "横向移动检测"
TIER3_WINDOW_NAME: "数据渗漏监控"
```

---

### 2. 推荐值范围

#### 完整推荐表

| 层级 | 默认值 | 最小值 | 推荐范围 | 推荐最大值 | 超过最大值后果 |
|------|--------|--------|---------|----------|--------------|
| **Tier 1** | 30秒 | 10秒（强制） | 10-300秒 | 300秒 (5分钟) | ⚠️ 警告，可启动 |
| **Tier 2** | 300秒 | 10秒（强制） | 60-1800秒 | 1800秒 (30分钟) | ⚠️ 警告，可启动 |
| **Tier 3** | 900秒 | 10秒（强制） | 300-7200秒 | 7200秒 (2小时) | ⚠️ 警告，可启动 |

#### 推荐值依据

**Tier 1 (10-300秒)**:
- **设计目标**: 快速响应勒索软件等高速威胁
- **最小值 (10秒)**: 防止系统过载
- **最佳实践 (30-60秒)**: 平衡响应速度和准确性
- **推荐最大值 (300秒)**: 超过5分钟失去"快速响应"意义

**Tier 2 (60-1800秒)**:
- **设计目标**: 主要威胁检测，平衡速度和准确性
- **最佳实践 (300-600秒)**: 良好的检测覆盖
- **推荐最大值 (1800秒)**: 超过30分钟告警延迟过长

**Tier 3 (300-7200秒)**:
- **设计目标**: APT慢速扫描检测，长期观察
- **最佳实践 (900-1800秒)**: 良好的APT检测覆盖
- **推荐最大值 (7200秒)**: 超过2小时接近离线分析时间尺度

---

### 3. 配置验证增强

#### 验证层级

**级别1: 强制验证（启动失败）**
```java
if (TIER1_WINDOW_SECONDS < MIN_WINDOW_SECONDS) {
    throw new IllegalArgumentException(
        "Tier 1 窗口不能小于最小窗口限制(10秒)");
}
```

**级别2: 推荐值警告（仅警告）**
```java
if (TIER1_WINDOW_SECONDS > TIER1_MAX_RECOMMENDED) {
    logger.warn("⚠️  Tier 1 窗口超过推荐最大值(300秒), 可能影响快速检测效果");
}
```

**级别3: 递增关系建议（仅警告）**
```java
if (TIER2_WINDOW_SECONDS < TIER1_WINDOW_SECONDS) {
    logger.warn("⚠️  Tier 2 窗口小于 Tier 1 窗口, 这可能不是预期配置");
}
```

#### 警告示例

```
# 启动日志
==================== 时间窗口配置 ====================
Tier 1 窗口: 30 秒 (勒索软件快速检测)
Tier 2 窗口: 300 秒 (5 分钟) (主要威胁检测)
Tier 3 窗口: 900 秒 (15 分钟) (APT慢速扫描检测)
=====================================================

# 如果超过推荐最大值
⚠️  Tier 1 窗口(600 秒 = 10 分钟)超过推荐最大值(300 秒 = 5 分钟), 可能影响快速检测效果
⚠️  Tier 2 窗口(3600 秒 = 60 分钟)超过推荐最大值(1800 秒 = 30 分钟), 可能延迟告警
⚠️  Tier 3 窗口(14400 秒 = 240 分钟)超过推荐最大值(7200 秒 = 120 分钟), 检测延迟较长
```

---

## 🔧 技术实现

### 代码修改

#### MultiTierWindowProcessor.java

**新增常量**:
```java
// 窗口名称配置
private static final String TIER1_WINDOW_NAME;
private static final String TIER2_WINDOW_NAME;
private static final String TIER3_WINDOW_NAME;

// 推荐最大值
private static final int TIER1_MAX_RECOMMENDED = 300;     // 5分钟
private static final int TIER2_MAX_RECOMMENDED = 1800;    // 30分钟
private static final int TIER3_MAX_RECOMMENDED = 7200;    // 2小时
```

**新增方法**:
```java
/**
 * 读取环境变量字符串值
 */
private static String getEnvString(String key, String defaultValue) {
    String value = System.getenv(key);
    if (value == null || value.trim().isEmpty()) {
        return defaultValue;
    }
    return value.trim();
}
```

**增强验证**:
```java
// 验证推荐最大值（仅警告）
if (TIER1_WINDOW_SECONDS > TIER1_MAX_RECOMMENDED) {
    logger.warn("⚠️  Tier 1 窗口({} 秒 = {} 分钟)超过推荐最大值({} 秒 = {} 分钟), 可能影响快速检测效果",
               TIER1_WINDOW_SECONDS, TIER1_WINDOW_SECONDS / 60,
               TIER1_MAX_RECOMMENDED, TIER1_MAX_RECOMMENDED / 60);
}
```

**日志增强**:
```java
logger.info("Tier 1 窗口: {} 秒 ({})", TIER1_WINDOW_SECONDS, TIER1_WINDOW_NAME);
logger.info("Tier 2 窗口: {} 秒 ({} 分钟) ({})", 
            TIER2_WINDOW_SECONDS, TIER2_WINDOW_SECONDS / 60, TIER2_WINDOW_NAME);
logger.info("Tier 3 窗口: {} 秒 ({} 分钟) ({})", 
            TIER3_WINDOW_SECONDS, TIER3_WINDOW_SECONDS / 60, TIER3_WINDOW_NAME);
```

---

## 📚 文档更新

### 更新文件列表

1. **TIME_WINDOW_CONFIGURATION.md**
   - ✅ 添加窗口名称配置说明
   - ✅ 添加推荐值范围章节
   - ✅ 添加配置验证详细说明
   - ✅ 添加推荐值依据分析

2. **TIME_WINDOW_QUICK_REF.md**
   - ✅ 更新快速参考表格
   - ✅ 添加窗口名称配置示例
   - ✅ 更新配置限制说明

3. **README.md**
   - ✅ 环境变量表格添加窗口名称配置
   - ✅ 添加推荐范围说明

4. **docker-compose.yml**
   - ✅ 添加窗口名称环境变量
   - ✅ 添加推荐范围注释

---

## 🧪 测试验证

### 验证步骤

```bash
# 1. 编译
cd services/stream-processing
mvn clean package -DskipTests

# 2. 重建镜像
cd ../../docker
docker compose build --no-cache stream-processing

# 3. 重启服务
docker compose up -d --force-recreate stream-processing

# 4. 验证配置
docker logs stream-processing 2>&1 | grep -A 8 "时间窗口配置"
```

### 预期输出

```
==================== 时间窗口配置 ====================
Tier 1 窗口: 30 秒 (勒索软件快速检测)
Tier 2 窗口: 300 秒 (5 分钟) (主要威胁检测)
Tier 3 窗口: 900 秒 (15 分钟) (APT慢速扫描检测)
=====================================================
```

---

## 📊 使用示例

### 示例1: 自定义窗口名称（中文业务场景）

```yaml
stream-processing:
  environment:
    TIER1_WINDOW_SECONDS: "30"
    TIER2_WINDOW_SECONDS: "300"
    TIER3_WINDOW_SECONDS: "900"
    
    TIER1_WINDOW_NAME: "快速威胁响应层"
    TIER2_WINDOW_NAME: "核心安全监控层"
    TIER3_WINDOW_NAME: "深度威胁分析层"
```

### 示例2: 自定义窗口名称（英文国际化）

```yaml
stream-processing:
  environment:
    TIER1_WINDOW_NAME: "Fast Response Layer"
    TIER2_WINDOW_NAME: "Core Security Layer"
    TIER3_WINDOW_NAME: "Deep Analysis Layer"
```

### 示例3: 测试超出推荐最大值

```yaml
stream-processing:
  environment:
    TIER1_WINDOW_SECONDS: "600"     # 超过推荐最大值300秒
    TIER2_WINDOW_SECONDS: "3600"    # 超过推荐最大值1800秒
    TIER3_WINDOW_SECONDS: "14400"   # 超过推荐最大值7200秒
```

**预期警告**:
```
⚠️  Tier 1 窗口(600 秒 = 10 分钟)超过推荐最大值(300 秒 = 5 分钟)
⚠️  Tier 2 窗口(3600 秒 = 60 分钟)超过推荐最大值(1800 秒 = 30 分钟)
⚠️  Tier 3 窗口(14400 秒 = 240 分钟)超过推荐最大值(7200 秒 = 120 分钟)
```

---

## 🎯 配置最佳实践

### 推荐配置组合

#### 配置1: 默认平衡型（推荐）

```yaml
TIER1_WINDOW_SECONDS: "30"
TIER2_WINDOW_SECONDS: "300"
TIER3_WINDOW_SECONDS: "900"

TIER1_WINDOW_NAME: "勒索软件快速检测"
TIER2_WINDOW_NAME: "主要威胁检测"
TIER3_WINDOW_NAME: "APT慢速扫描检测"
```

#### 配置2: 高安全敏感型

```yaml
TIER1_WINDOW_SECONDS: "15"      # 在推荐范围内
TIER2_WINDOW_SECONDS: "120"     # 在推荐范围内
TIER3_WINDOW_SECONDS: "300"     # 在推荐范围内

TIER1_WINDOW_NAME: "极速威胁拦截"
TIER2_WINDOW_NAME: "实时安全监控"
TIER3_WINDOW_NAME: "持续行为分析"
```

#### 配置3: 大流量低噪声型

```yaml
TIER1_WINDOW_SECONDS: "60"      # 在推荐范围内
TIER2_WINDOW_SECONDS: "600"     # 在推荐范围内
TIER3_WINDOW_SECONDS: "1800"    # 在推荐范围内

TIER1_WINDOW_NAME: "快速筛查层"
TIER2_WINDOW_NAME: "精准检测层"
TIER3_WINDOW_NAME: "深度追踪层"
```

---

## ⚠️ 注意事项

### 窗口名称
1. ✅ 支持中文、英文、数字
2. ✅ 建议控制在20字符以内
3. ✅ 会显示在告警描述和日志中
4. ⚠️ 不支持特殊字符（建议避免）

### 推荐值
1. ✅ 推荐范围基于实际威胁检测经验
2. ✅ 超出推荐最大值会警告但不影响启动
3. ⚠️ 低于最小值（10秒）会导致启动失败
4. 💡 建议根据实际业务需求在推荐范围内调整

---

## 📝 变更历史

| 日期 | 版本 | 变更内容 |
|------|------|---------|
| 2025-10-27 | 1.0 | 初始实现 - 环境变量配置时间窗口 |
| 2025-10-27 | 1.1 | 新增窗口名称自定义 + 推荐值范围 |

---

**实施状态**: ✅ 已完成并部署  
**文档状态**: ✅ 已完整更新  
**测试状态**: ✅ 已验证通过
