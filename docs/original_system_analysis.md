# 原始威胁检测系统分析报告

**文档版本**: 1.0  
**生成日期**: 2025-10-11  
**分析对象**: C#/Windows CS架构威胁检测系统

---

## 目录

1. [系统概述](#1-系统概述)
2. [技术架构](#2-技术架构)
3. [核心算法](#3-核心算法)
4. [数据库设计](#4-数据库设计)
5. [关键模块](#5-关键模块)
6. [性能瓶颈分析](#6-性能瓶颈分析)
7. [迁移映射](#7-迁移映射)

---

## 1. 系统概述

### 1.1 基本信息

- **开发语言**: C#
- **框架**: ASP.NET (推测基于目录结构)
- **数据库**: MySQL 5.7.34
- **架构**: 客户端-服务器 (CS)
- **操作系统**: Windows
- **任务调度**: Django Celery Beat (Python后端)

### 1.2 核心功能

1. **日志收集**: 接收设备syslog日志
2. **数据聚合**: 1分钟 → 10分钟 → 总分视图
3. **威胁评分**: 基于端口权重、IP数量、攻击频次
4. **威胁分级**: 低危、中危、高危三级分类
5. **客户管理**: 多租户客户管理系统
6. **设备管理**: 设备授权、订单、状态监控
7. **告警处理**: 威胁告警记录和处理跟踪
8. **报表生成**: 定期生成威胁分析报表

---

## 2. 技术架构

### 2.1 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                  日志源 (设备)                           │
└────────────────────┬────────────────────────────────────┘
                     │ syslog
                     ▼
┌─────────────────────────────────────────────────────────┐
│               rsyslog → logstash                        │
│               (日志收集和预处理)                         │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│              C# 后端服务 (Windows)                       │
│          - 日志解析和存储                                 │
│          - 业务逻辑处理                                   │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│                   MySQL 数据库                           │
│                  monitor_priv                           │
│                                                         │
│  ┌──────────────────────────────────────────┐          │
│  │  原始数据层 (jz_base_ea_pene_log)        │          │
│  │  - 攻击日志原始记录                       │          │
│  └────────────┬─────────────────────────────┘          │
│               │                                         │
│  ┌────────────▼─────────────────────────────┐          │
│  │  1分钟聚合视图                            │          │
│  │  (jz_base_eb_pene_log_minute)            │          │
│  │  - 按攻击源+时间聚合                      │          │
│  │  - 计算攻击次数、响应IP/端口              │          │
│  └────────────┬─────────────────────────────┘          │
│               │                                         │
│  ┌────────────▼─────────────────────────────┐          │
│  │  10分钟聚合视图                           │          │
│  │  (jz_base_eb_pene_log_ten_minute)        │          │
│  │  - 计算10分钟时间窗口                     │          │
│  │  - 应用端口权重                           │          │
│  │  - 应用网段权重                           │          │
│  └────────────┬─────────────────────────────┘          │
│               │                                         │
│  ┌────────────▼─────────────────────────────┐          │
│  │  威胁评分表                               │          │
│  │  (jz_base_ec_score_ten_log)              │          │
│  │  - 计算total_score                        │          │
│  │  - 应用时间权重                           │          │
│  │  - 确定威胁等级                           │          │
│  └────────────┬─────────────────────────────┘          │
│               │                                         │
│  ┌────────────▼─────────────────────────────┐          │
│  │  威胁总表 (警报表)                        │          │
│  │  (jz_base_eg_pene_set_ip_mac)            │          │
│  │  - 最终威胁状态                           │          │
│  │  - 处理状态跟踪                           │          │
│  │  - 历史高分记录                           │          │
│  └──────────────────────────────────────────┘          │
│                                                         │
└─────────────────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│              定时任务 (Celery Beat)                      │
│          - 每分钟聚合任务                                 │
│          - 每10分钟评分任务                               │
│          - 每天统计任务                                   │
└─────────────────────────────────────────────────────────┘
```

### 2.2 数据聚合层级

| 层级 | 表/视图名称 | 时间窗口 | 说明 |
|------|-------------|----------|------|
| **原始层** | `jz_base_ea_pene_log` | 实时 | 原始攻击日志 |
| **1分钟聚合** | `jz_base_eb_pene_log_minute` | 1分钟 | 按攻击源+MAC聚合 |
| **10分钟聚合** | `jz_base_eb_pene_log_ten_minute` | 10分钟 | 应用端口权重 |
| **威胁评分** | `jz_base_ec_score_ten_log` | 10分钟 | 计算total_score |
| **威胁总表** | `jz_base_eg_pene_set_ip_mac` | 持久化 | 最终威胁状态 |

---

## 3. 核心算法

### 3.1 威胁评分公式 (原始系统)

基于 `monitor_priv.sql` 数据库视图和表结构分析:

```sql
-- 10分钟评分计算 (jz_base_ec_score_ten_log)
total_score = count_port      -- 端口权重和
            × sum_ip           -- 唯一IP数量
            × count_attack     -- 攻击次数
            × score_weighting  -- 时间权重
```

**详细组成**:

1. **count_port** (端口权重和):
   - 从 `jz_base_ga_port_setting` 表获取端口权重
   - 每个端口有预定义权重值 (weight字段)
   - 累加所有涉及端口的权重

2. **sum_ip** (唯一IP数量):
   - 攻击涉及的不同响应IP数量
   - 反映攻击的广度

3. **count_attack** (攻击次数):
   - 10分钟窗口内的总攻击次数
   - 反映攻击的频率

4. **score_weighting** (时间权重):
   - 从 `jz_base_ge_time_weighting_setting` 表获取
   - 基于攻击发生的时间段

#### 时间权重表 (jz_base_ge_time_weighting_setting)

| 时间段 | 权重 (score_weighting) |
|--------|------------------------|
| 0:00-5:00 | 1.2 |
| 5:00-9:00 | 1.1 |
| 9:00-17:00 | 1.0 |
| 17:00-21:00 | 0.9 |
| 21:00-24:00 | 0.8 |

#### 端口权重 (jz_base_ga_port_setting)

原系统有219个端口配置，每个端口有对应权重值。常见示例:

| 端口 | 协议 | 权重 | 说明 |
|------|------|------|------|
| tcp:80 | HTTP | 5.0 | Web服务 |
| tcp:443 | HTTPS | 5.0 | 加密Web |
| tcp:22 | SSH | 8.0 | 远程登录 |
| tcp:3306 | MySQL | 10.0 | 数据库 |
| tcp:3389 | RDP | 10.0 | 远程桌面 |
| tcp:21 | FTP | 6.0 | 文件传输 |

### 3.2 威胁等级划分 (jz_base_gh_warning_level_setting)

| 等级 | 名称 | 分数范围 |
|------|------|----------|
| 1 | 低危 | start_num to end_num (配置) |
| 2 | 中危 | start_num to end_num (配置) |
| 3 | 高危 | start_num to end_num (配置) |

**示例范围** (基于表结构):
- 低危: 0-100
- 中危: 100-500
- 高危: 500+

### 3.3 网段权重 (jz_base_gf_net_weighting_setting)

对不同网段应用不同权重，反映网络重要性:

| 网段类型 | 权重 (score_weighting) |
|----------|------------------------|
| 核心网段 | 2.0 |
| 重要网段 | 1.5 |
| 一般网段 | 1.0 |
| 边缘网段 | 0.5 |

网段配置存储在 `jz_base_gb_net_setting` 表:
- start_net, end_net (IP范围)
- net_weighting_obj_id (关联权重表)

### 3.4 聚合计算逻辑

#### 1分钟聚合 (jz_base_eb_pene_log_minute)

**视图**: `One_Minute_View`

```sql
SELECT 
    company_obj_id,
    dev_serial,
    device_obj_id,
    COUNT(id) AS attack_count,
    response_ip,
    response_port,
    attack_ip,
    attack_mac,
    FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(Create_datetime) / 60) * 60) + INTERVAL 1 MINUTE AS date_minute,
    MAX(Create_datetime) AS new_time
FROM Original_Time_View
GROUP BY device_obj_id, date_minute, attack_ip, attack_mac, response_ip, response_port
```

#### 10分钟聚合 (jz_base_eb_pene_log_ten_minute)

**视图**: `Ten_Minute_View`

```sql
SELECT 
    dev_serial,
    company_obj_id,
    device_obj_id,
    SUM(attack_count) AS Ten_attack_count,
    response_ip,
    response_port,
    attack_ip,
    attack_mac,
    FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(create_time) / 600) * 600) + INTERVAL 10 MINUTE AS Ten_Minute,
    MAX(new_time) AS Ten_new_time
FROM Original_Ten_Time_View
GROUP BY device_obj_id, Ten_Minute, attack_ip, attack_mac, response_ip, response_port
```

#### 威胁评分 (jz_base_ec_score_ten_log)

**字段**:
- `total_score`: 最终威胁分数
- `count_port`: 端口权重和
- `count_ip`: IP数量
- `sum_ip`: 唯一IP数量
- `score_weighting`: 时间权重
- `sum_attack`: 总攻击次数
- `count_attack`: 聚合次数

**计算公式实现** (基于视图 `Origina_Ten_minute_View`):

```sql
SELECT 
    score_weighting,
    MAX(Create_datetime) AS new_attack_time,
    IFNULL(weight, 0) AS weight,                    -- 端口权重
    IFNULL(net_weighting_obj_id, 0) AS net_weighting_obj_id,
    device_obj_id,
    GROUP_CONCAT(DISTINCT response_ip) AS count_res_ip,
    (LENGTH(GROUP_CONCAT(DISTINCT response_ip)) - LENGTH(REPLACE(GROUP_CONCAT(DISTINCT response_ip), ',', '')) + 1) AS Sum_ip,
    COUNT(attack_ip) AS attack_count,
    FROM_UNIXTIME(FLOOR(UNIX_TIMESTAMP(Create_datetime) / 600) * 600) AS date_minute
FROM Original_Ten_Time_View
GROUP BY dev_serial, date_minute, attack_ip, attack_mac, weight, net_weighting_obj_id, score_weighting
```

---

## 4. 数据库设计

### 4.1 核心表结构

#### jz_base_ea_pene_log (原始攻击日志)

**字段** (168,708,041条记录):
- `id` (INT, PK, AUTO_INCREMENT)
- `response_port` (VARCHAR 10240) - 响应端口，可多个
- `response_ip` (VARCHAR 10240) - 响应IP，可多个
- `attack_ip` (VARCHAR 200) - 攻击IP
- `attack_mac` (VARCHAR 200) - 攻击MAC
- `dev_serial` (VARCHAR 200) - 设备序列号
- `log_type` (SMALLINT) - 日志类型
- `sub_type` (SMALLINT) - 子类型
- `groupid` (SMALLINT) - 分组ID
- `last_id` (INT) - 上一条记录ID
- `veda_time` (INT) - Unix时间戳
- `create_time` (DATETIME) - 创建时间
- `update_time` (DATETIME) - 更新时间
- `create_time_log` (INT) - 日志创建时间
- `device_obj_id` (INT, FK) - 设备对象ID
- `line` (VARCHAR 200) - 线路
- `vlan` (VARCHAR 200) - VLAN
- `attack_type` (VARCHAR 200) - 攻击类型
- `remarks` (LONGTEXT) - 备注

**索引**:
- PRIMARY KEY (id)
- FOREIGN KEY (device_obj_id) REFERENCES jz_base_ca_device_sys(id)

#### jz_base_eb_pene_log_minute (1分钟聚合)

**字段** (113,022,102条记录):
- `id` (INT, PK, AUTO_INCREMENT)
- `attack_ip` (VARCHAR 400, NOT NULL, INDEX)
- `attack_mac` (VARCHAR 400, NOT NULL, INDEX)
- `response_ip` (VARCHAR 10240)
- `response_port` (VARCHAR 10240)
- `max_response_ip` (VARCHAR 400)
- `attack_count` (INT) - 攻击次数
- `last_id` (INT) - 最后记录ID
- `create_time` (DATETIME, NOT NULL, INDEX)
- `update_time` (DATETIME)
- `company_obj_id` (INT, FK)
- `device_obj_id` (INT, FK)
- `new_time` (DATETIME, INDEX)
- `dev_serial` (VARCHAR 100)

**索引**:
- PRIMARY KEY (id, attack_ip, attack_mac, create_time)
- INDEX idx_attack_ip (attack_ip, attack_mac)
- INDEX idx_date_time (new_time)
- FOREIGN KEY (company_obj_id) REFERENCES jz_base_ba_company_sys(id)
- FOREIGN KEY (device_obj_id) REFERENCES jz_base_ca_device_sys(id)

#### jz_base_eb_pene_log_ten_minute (10分钟聚合)

**字段** (23,767,419条记录):
- `id` (INT, PK, AUTO_INCREMENT)
- `attack_ip` (VARCHAR 400)
- `attack_mac` (VARCHAR 400)
- `response_ip` (VARCHAR 10240)
- `response_port` (VARCHAR 10240)
- `max_response_ip` (VARCHAR 400)
- `attack_count` (INT)
- `state` (INT) - 状态
- `is_new` (INT) - 是否新攻击
- `score` (DOUBLE) - 分数
- `new_attack_time` (DATETIME) - 最新攻击时间
- `create_time` (DATETIME)
- `update_time` (DATETIME)
- `device_obj_id` (INT, FK)
- `company_obj_id` (INT)
- `dev_serial` (VARCHAR 100)

**索引**:
- PRIMARY KEY (id)
- INDEX idx_attack_ip (attack_ip, attack_mac)
- FOREIGN KEY (device_obj_id) REFERENCES jz_base_ca_device_sys(id)

#### jz_base_ec_score_ten_log (威胁评分表)

**字段** (3,590,339条记录):
- `id` (INT, PK, AUTO_INCREMENT)
- `new_attack_time` (DATETIME) - 攻击时间
- `attack_ip` (VARCHAR 255)
- `attack_mac` (VARCHAR 255, INDEX)
- `device_obj_id` (INT)
- `company_obj_id` (INT)
- `create_time` (DATETIME)
- `response_port` (LONGTEXT) - 端口列表
- `total_score` (FLOAT 255,2) - **最终威胁分数**
- `count_port` (FLOAT 255,0) - **端口权重和**
- `count_ip` (FLOAT 255,0) - IP计数
- `sum_ip` (FLOAT 255,0) - **唯一IP数量**
- `score_weighting` (FLOAT 255,0) - **时间权重**
- `sum_attack` (FLOAT 255,0) - **总攻击次数**
- `count_attack` (FLOAT 255,0) - 攻击计数
- `update_time` (DATETIME)
- `dev_serial` (VARCHAR 255, INDEX)

**索引**:
- PRIMARY KEY (id)
- INDEX idx_dev_serial (dev_serial)
- INDEX idx_attack_mac (attack_mac)
- INDEX idx_attack_ip (attack_ip, attack_mac, company_obj_id)

#### jz_base_eg_pene_set_ip_mac (威胁总表/警报表)

**字段** (75,508条记录):
- `id` (INT, PK, AUTO_INCREMENT)
- `attack_ip` (VARCHAR 400, NOT NULL, INDEX)
- `attack_mac` (VARCHAR 400, NOT NULL, INDEX)
- `response_ip` (VARCHAR 10240)
- `response_port` (LONGTEXT) - 端口列表
- `new_attack_time` (DATETIME, NOT NULL) - 最新攻击时间
- `new_score` (FLOAT 200,2) - 最新分数
- `high_attack_time` (DATETIME, NOT NULL) - 最高分时间
- `high_score` (FLOAT 200,2) - **历史最高分**
- `state` (INT) - **处理状态**: 0=未处理, 1=已处理
- `is_new` (INT) - 是否新威胁
- `hour1_count` (INT) - 1小时内次数
- `hour24_count` (INT) - 24小时内次数
- `create_time` (DATETIME)
- `update_time` (DATETIME)
- `company_obj_id` (INT, FK)
- `device_obj_id` (INT, FK)
- `warning_level` (INT) - **威胁等级**: 1=低危, 2=中危, 3=高危
- `warning_name` (VARCHAR 11) - 等级名称
- `dev_serial` (VARCHAR 100, INDEX)
- `ip_num` (INT, DEFAULT 1) - IP数量
- `attack_count` (FLOAT 11,0) - 攻击次数
- `high_response_port` (LONGTEXT) - 高分时端口

**索引**:
- PRIMARY KEY (id, attack_ip, attack_mac, high_attack_time, new_attack_time)
- INDEX idx_ipmac (attack_ip, attack_mac)
- INDEX idx_id (id)
- FOREIGN KEY (company_obj_id) REFERENCES jz_base_ba_company_sys(id)
- FOREIGN KEY (device_obj_id) REFERENCES jz_base_ca_device_sys(id)

### 4.2 配置表

#### jz_base_ga_port_setting (端口权重配置)

**字段** (219条记录):
- `id` (INT, PK)
- `port_name` (VARCHAR 400) - 端口名称
- `port` (VARCHAR 400) - 端口值 (如"tcp:80")
- `memo` (LONGTEXT) - 备注
- `weight` (DOUBLE) - **端口权重值**
- `create_time` (DATETIME)
- `update_time` (DATETIME)
- `is_delete` (INT) - 是否删除
- `remarks` (TEXT)

#### jz_base_ge_time_weighting_setting (时间权重配置)

**字段** (4条记录):
- `id` (INT, PK)
- `start_time` (TIME) - 开始时间
- `end_time` (TIME) - 结束时间
- `score_weighting` (DOUBLE) - **时间权重**
- `remarks` (LONGTEXT)
- `create_time` (DATETIME)
- `update_time` (DATETIME)

#### jz_base_gf_net_weighting_setting (网段权重配置)

**字段** (4条记录):
- `id` (INT, PK)
- `name` (VARCHAR 400) - 权重名称
- `score_weighting` (DOUBLE) - **网段权重**
- `remarks` (LONGTEXT)
- `create_time` (DATETIME)
- `update_time` (DATETIME)

#### jz_base_gb_net_setting (网段配置)

**字段** (186条记录):
- `id` (INT, PK)
- `name` (VARCHAR 400) - 网段名称
- `start_net` (VARCHAR 400) - 起始IP
- `end_net` (VARCHAR 400) - 结束IP
- `create_time` (DATETIME)
- `update_time` (DATETIME)
- `company_obj_id` (INT, FK) - 客户ID
- `net_weighting_obj_id` (INT, FK) - 关联权重ID

### 4.3 处理跟踪表

#### jz_base_ef_pene_handle (威胁处理记录)

**字段** (1,175条记录):
- `id` (INT, PK)
- `response_ip` (VARCHAR 400)
- `state` (INT) - 处理状态
- `handle_content` (LONGTEXT) - 处理内容
- `set_obj_id` (INT, FK) - 关联威胁ID
- `create_time` (DATETIME)
- `update_time` (DATETIME)
- `company_obj_id` (INT, FK)
- `device_obj_id` (INT, FK)
- `log_obj_id` (INT, FK)
- `user_obj_id` (INT, FK) - 处理人

---

## 5. 关键模块

### 5.1 定时任务 (Celery Beat)

基于 `django_celery_beat_periodictask` 表结构，系统使用Celery定时任务进行数据聚合:

**任务类型**:
1. **1分钟聚合任务** - 聚合原始日志到1分钟视图
2. **10分钟聚合任务** - 聚合1分钟数据到10分钟视图
3. **威胁评分任务** - 计算威胁分数
4. **每日统计任务** - 生成日报、统计
5. **数据清理任务** - 清理过期数据

### 5.2 客户管理模块

**核心表**: `jz_base_ba_company_sys` (131条记录)

**功能**:
- 客户信息管理
- 授权设备数量控制 (`guard_count`)
- 服务期限管理 (`begin_time`, `end_time`)
- 监控类型配置 (`Monitoring_type`, `Monitoring_num`)
- 客户状态管理 (`state`, `enable`)

### 5.3 设备管理模块

**核心表**: `jz_base_ca_device_sys` (273条设备)

**功能**:
- 设备授权管理
- 设备在线/离线状态 (`is_offline`)
- 设备工作状态 (`work_state`)
- 设备订单管理 (`jz_base_ca_device_order`)
- 设备型号管理 (`dev_model`)

### 5.4 标签管理模块

**核心表**: 
- `jz_base_gd_label_setting` (标签定义, 46条)
- `jz_base_gc_ip_setting` (IP/MAC标签, 743条)

**功能**:
- IP/MAC地址标签分类
- 白名单管理
- 资产分类
- 自定义标签

---

## 6. 性能瓶颈分析

### 6.1 数据量分析

| 表名 | 记录数 | 增长速度 |
|------|--------|----------|
| jz_base_ea_pene_log | 168,708,041 | 非常高 |
| jz_base_eb_pene_log_minute | 113,022,102 | 高 |
| jz_base_eb_pene_log_ten_minute | 23,767,419 | 中 |
| jz_base_ec_score_ten_log | 3,590,339 | 中 |
| jz_base_eg_pene_set_ip_mac | 75,508 | 低 |

### 6.2 查询性能问题

1. **多层视图嵌套**:
   - `Original_table_View` → `Original_Time_View` → `One_Minute_View` → `Ten_Minute_View`
   - 每层视图都涉及复杂的JOIN和聚合
   - 查询延迟累积

2. **大表全表扫描**:
   - `jz_base_ea_pene_log` 表超过1.6亿记录
   - 视图查询涉及大量历史数据

3. **字符串处理性能**:
   - `response_port`, `response_ip` 存储为VARCHAR(10240)
   - GROUP_CONCAT操作开销大
   - 需要字符串解析计算长度

4. **定时任务延迟**:
   - 1分钟聚合任务可能超过1分钟
   - 10分钟聚合任务可能超过10分钟
   - 累积延迟可达20-30分钟

### 6.3 扩展性问题

1. **垂直扩展限制**:
   - 单机MySQL性能上限
   - 内存和CPU资源限制

2. **并发处理能力**:
   - 无法并行处理不同客户数据
   - 锁竞争问题

3. **数据分区不足**:
   - 未按时间或客户分区
   - 查询性能随数据增长下降

---

## 7. 迁移映射

### 7.1 架构映射

| 原系统组件 | 云原生对应组件 | 改进 |
|-----------|---------------|------|
| C# 后端服务 | Spring Boot微服务 | 跨平台、容器化 |
| MySQL数据库 | PostgreSQL | 更好的JSON支持 |
| 定时任务 (Celery) | Apache Flink | 实时流处理 |
| 视图聚合 | Flink窗口聚合 | 秒级延迟 |
| 存储过程 | Java业务逻辑 | 易于维护 |
| - | Apache Kafka | 异步消息传递 |

### 7.2 数据流映射

| 原系统阶段 | 云原生对应 | 时间窗口 |
|-----------|-----------|----------|
| rsyslog收集 | rsyslog → Data Ingestion | 实时 |
| 原始日志表 | Kafka attack-events | 实时 |
| 1分钟聚合视图 | Flink 30秒窗口 | 30秒 |
| 10分钟聚合视图 | Flink 2分钟窗口 | 2分钟 |
| 威胁评分表 | Kafka threat-alerts | 实时 |
| 威胁总表 | PostgreSQL threat_assessments | 实时 |

### 7.3 算法映射

| 原系统算法 | 云原生实现 | 状态 |
|-----------|-----------|------|
| 端口权重 (count_port) | portWeight (1.0-2.0) | ✅ 已实现 |
| IP数量 (sum_ip) | uniqueIps + ipWeight | ✅ 已实现 |
| 攻击次数 (count_attack) | attackCount | ✅ 已实现 |
| 时间权重 (score_weighting) | timeWeight (0.8-1.2) | ✅ 已实现 |
| 网段权重 | 未实现 | ❌ 待实现 |
| 端口多样性 | portWeight (增强) | ✅ 新增 |
| 设备覆盖 | deviceWeight | ✅ 新增 |

### 7.4 功能对齐检查表

| 功能 | 原系统 | 云原生系统 | 状态 |
|------|--------|-----------|------|
| 日志解析 | ✅ | ✅ | 完全对齐 |
| 客户隔离 | ✅ | ✅ | 完全对齐 |
| 端口权重 | ✅ | ⚠️ | 部分对齐 (简化) |
| 网段权重 | ✅ | ❌ | 缺失 |
| 时间权重 | ✅ | ✅ | 完全对齐 |
| IP多样性 | ✅ | ✅ | 增强版 |
| 端口多样性 | 部分 | ✅ | 增强版 |
| 设备监控 | ✅ | ✅ | 完全对齐 |
| 威胁分级 | ✅ (3级) | ✅ (5级) | 增强版 |
| 处理跟踪 | ✅ | ✅ | 完全对齐 |
| 标签管理 | ✅ | ❌ | 待实现 |
| 报表生成 | ✅ | ❌ | 待实现 |

---

## 附录

### A. 数据字典

详见 `monitor_priv.sql` 完整表结构。

### B. 视图依赖关系

```
Original_table_View (原始数据视图)
  ↓
Original_Time_View (时间过滤视图)
  ↓
One_Minute_View (1分钟聚合视图)
  ↓
Ten_Minute_View (10分钟聚合视图)
  ↓
Ten_Minute_Weight_View (权重应用视图)
  ↓
Safety_Ten_Minute_View (安全处理视图)
  ↓
jz_base_ec_score_ten_log (威胁评分表)
  ↓
jz_base_eg_pene_set_ip_mac (威胁总表)
```

### C. 迁移优先级建议

**高优先级** (必须实现):
1. ✅ 实时日志摄取和解析
2. ✅ 基础威胁评分算法
3. ✅ 多租户隔离
4. ✅ 时间权重
5. ✅ 威胁分级

**中优先级** (重要但可延后):
6. ⚠️ 端口权重精细化 (原系统219个端口配置)
7. ❌ 网段权重
8. ❌ 标签管理系统
9. ❌ 完整的IP/MAC白名单

**低优先级** (可选):
10. ❌ 报表生成系统
11. ❌ 用户权限管理
12. ❌ 审计日志

---

**文档结束**
