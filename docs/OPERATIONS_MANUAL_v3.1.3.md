# 威胁检测系统 操作手册 v3.1.5

| 项目 | 内容 |
|------|------|
| 版本 | v3.1.5 |
| 发布日期 | 2026-04-03 |
| 适用环境 | K3s 单节点 (10.174.1.222) |
| 编写目的 | 供测试人员验证系统全功能运行状态 |

---

## 目录

1. [系统架构概述](#1-系统架构概述)
2. [登录与导航](#2-登录与导航)
3. [仪表盘 (Dashboard) 验证](#3-仪表盘-dashboard-验证)
4. [分析页面 (Analytics) 验证](#4-分析页面-analytics-验证)
5. [威胁列表 (ThreatList) 验证](#5-威胁列表-threatlist-验证)
6. [告警中心 (AlertCenter) 验证](#6-告警中心-alertcenter-验证)
7. [客户管理 (CustomerMgmt) 验证](#7-客户管理-customermgmt-验证)
8. [ML检测 (MlDetection) 验证](#8-ml检测-mldetection-验证)
9. [影子评分 (Shadow Scoring) 说明](#9-影子评分-shadow-scoring-说明)
10. [威胁情报与TIRE集成验证](#10-威胁情报与tire集成验证)
11. [系统设置 (Settings) 验证](#11-系统设置-settings-验证)
12. [系统监控 (SystemMonitor) 验证](#12-系统监控-systemmonitor-验证)
13. [测试数据发送](#13-测试数据发送)
14. [数据库验证](#14-数据库验证)
15. [Pod状态检查](#15-pod状态检查)
16. [威胁评分公式](#16-威胁评分公式)
17. [离线部署指南](#17-离线部署指南)
18. [常见问题排查](#18-常见问题排查)
19. [版本历史](#19-版本历史)

---

## 1. 系统架构概述

### 1.1 服务组成

本系统由18个微服务Pod组成，部署在K3s集群的 `threat-detection` 命名空间中。

| Pod名称前缀 | 服务名称 | 功能说明 | 内部端口 | 外部端口 |
|---|---|---|---|---|
| frontend | 前端界面 | React SPA管理仪表板 | 80 | **30080** (NodePort) |
| api-gateway | API网关 | 统一入口，JWT鉴权，路由转发 | 8888 | — |
| data-ingestion | 数据摄取 | 接收Logstash日志，发布到Kafka | 8080 | — |
| logstash | 日志收集 | 接收外部syslog，转发到data-ingestion | 9080 | **32318** (NodePort) |
| kafka | 消息队列 | 事件流中间件 | 9092 | — |
| zookeeper | ZooKeeper | Kafka协调服务 | 2181 | — |
| stream-processing-jobmanager | Flink JobManager | Flink流处理调度 | 6123/8081 | — |
| stream-processing-taskmanager | Flink TaskManager | Flink流处理执行 | — | — |
| threat-assessment | 威胁评估 | 评分、趋势分析、端口分布 | 8083 | — |
| alert-management | 告警管理 | 多通道通知、智能去重 | 8082 | — |
| customer-management | 客户管理 | 客户CRUD、设备绑定、多租户 | 8084 | — |
| threat-intelligence | 威胁情报 | 外部情报源聚合、IOC管理 | 8085 | — |
| tire | TIRE引擎 | 威胁情报信誉引擎 (FastAPI) | 8000 | — |
| ml-detection | ML检测 | ONNX模型推理、异常检测 | 8086 | — |
| config-server | 配置服务器 | Spring Cloud Config | 8899 | — |
| postgres-0 | 数据库 | PostgreSQL主存储 | 5432 | — |
| redis | 缓存 | Redis缓存 | 6379 | — |
| emqx | MQTT代理 | EMQX物联网消息代理 | 1883 | **31883** (NodePort) |

### 1.2 数据流

```
外部syslog日志
    |
    v
Logstash (10.174.1.222:32318, TCP)
    | (HTTP POST to data-ingestion)
    v
Data Ingestion (解析syslog KV格式, 解析customerId)
    |
    v
Kafka (attack-events topic)
    |
    v
Flink Stream Processing (3级时间窗口: 30s/5min/15min)
    |
    v
Kafka (threat-alerts topic)
    |
    +---> Threat Assessment ---> PostgreSQL (threat_assessments表)
    |         |                       |
    |         v                       v
    |    Threat Intelligence     Frontend Dashboard/Analytics
    |         |
    |         v
    |    TIRE (IP信誉评分)
    |
    +---> ML Detection (ONNX autoencoder + BiGRU)
              |
              v
         Kafka (ml-threat-detections topic)
              |
              v
         MlWeightService (缓存mlWeight) ---> 威胁评分公式
```

### 1.3 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | React 18 + TypeScript + Ant Design 5 + Vite |
| API网关 | Spring Cloud Gateway (WebFlux) |
| 后端微服务 | Spring Boot 3.1.5 (OpenJDK 21) |
| 流处理 | Apache Flink 1.18 |
| ML推理 | Python FastAPI + ONNX Runtime |
| TIRE | Python FastAPI + 11款插件 |
| 消息队列 | Apache Kafka 3.4 |
| 数据库 | PostgreSQL 15 |
| 缓存 | Redis |
| MQTT | EMQX 5.5.1 |
| 容器编排 | K3s (轻量Kubernetes) |

---

## 2. 登录与导航

### 2.1 访问地址

```
http://10.174.1.222:30080
```

在浏览器中打开上述地址，将显示登录页面。

### 2.2 测试账号

| 用户名 | 密码 | 角色 | 说明 |
|--------|------|------|------|
| admin | admin123 | SUPER_ADMIN | 系统超级管理员，可看到所有客户数据，customerId=demo-customer |
| demo_admin | admin123 | TENANT_ADMIN | 租户管理员，可管理 demo-customer 下的数据 |

### 2.3 菜单导航

登录后，左侧菜单栏包含以下页面:

| 菜单项 | 页面 | 功能 |
|--------|------|------|
| 仪表盘 | Dashboard | 系统总览，统计卡片，威胁趋势图，端口分布图，最新威胁 |
| 分析 | Analytics | 时间范围可选的趋势分析和端口分布 |
| 威胁列表 | ThreatList | 全部威胁评估记录表格 |
| 告警中心 | AlertCenter | 告警列表，支持操作(升级/解决/分配) |
| 客户管理 | CustomerMgmt | 客户和设备管理 |
| 威胁情报 | ThreatIntel | Feed管理，外部情报源 |
| ML检测 | MlDetection | 机器学习模型状态和检测结果 |
| 系统设置 | Settings | TIRE插件管理，LLM配置 |
| 系统监控 | SystemMonitor | 服务健康状态 |

注意: 不同角色可能看到不同的菜单项。SUPER_ADMIN可以看到所有菜单，TENANT_ADMIN可能无法看到部分管理功能。

---

## 3. 仪表盘 (Dashboard) 验证

### 3.1 验证内容

打开仪表盘页面后，应看到以下内容:

**顶部统计卡片 (4个)**:
- 总威胁数: 显示威胁评估记录总数 (当前约320+条)
- 平均威胁分数: 显示所有评估的平均分 (当前约25分)
- 高危威胁数: 威胁等级为 HIGH 或 CRITICAL 的数量
- 活跃攻击源: 唯一攻击IP数量

**威胁趋势图 (24小时)**:
- 类型: 柱状图或折线图
- X轴: 时间 (按小时)
- Y轴: 威胁数量
- 验证: 图表中应有数据柱/点，如果为空说明最近24小时内没有新数据

**端口攻击分布**:
- 类型: 饼图
- 显示各端口被攻击的百分比
- 常见端口: SSH(22), HTTP(80), HTTPS(443), RDP(3389), 8080等
- 验证: 饼图应显示多个扇区，每个对应一个端口

**最新威胁列表**:
- 表格形式，显示最新的威胁评估记录
- 字段: 客户ID, 攻击MAC, 攻击IP, 威胁等级, 威胁分数, 时间等

### 3.2 如果图表为空

如果威胁趋势图或端口分布图为空，说明最近时间范围内没有数据。请参考 [第13节 测试数据发送](#13-测试数据发送) 发送测试数据，等待约60秒后刷新页面。

---

## 4. 分析页面 (Analytics) 验证

### 4.1 验证内容

**时间范围选择器**:
- 页面顶部有3个选项: 24小时 / 7天 / 30天
- 切换后，下方图表和统计数据应随之变化

**威胁趋势图**:
- 与Dashboard类似，但支持更长时间范围
- 选择7天或30天后应显示更多历史数据

**端口攻击分布图**:
- 柱状图形式
- 按选定时间范围过滤数据

**统计卡片**:
- 显示选定时间范围内的总数、平均分等

### 4.2 验证步骤

1. 默认加载24小时视图，确认图表有数据
2. 切换到7天视图，确认数据范围扩大
3. 切换到30天视图，确认包含所有历史数据
4. 确认端口分布图在不同时间范围下显示正确

---

## 5. 威胁列表 (ThreatList) 验证

### 5.1 验证内容

**表格字段**:
- 客户ID (customer_id)
- 攻击MAC地址 (attack_mac)
- 攻击IP (attack_ip)
- 威胁等级 (threat_level): INFO / LOW / MEDIUM / HIGH / CRITICAL
- 威胁分数 (threat_score): 数值
- 端口列表 (port_list): 被攻击的端口
- 评估时间 (assessed_at)

**功能验证**:
- 分页: 默认每页显示一定数量，可翻页
- 排序: 点击列头可排序
- 筛选: 可按威胁等级、客户等条件筛选
- 详情: 点击记录可查看详情

### 5.2 验证要点

- 确认 port_list 字段不为空 (v3.1.0修复项)
- 确认有多个客户的数据 (demo-customer 和 customer_a)
- 确认威胁等级分布合理

---

## 6. 告警中心 (AlertCenter) 验证

### 6.1 验证内容

**告警列表**:
- 告警来源于威胁评估结果
- 严重程度: Critical (危急) / High (高) / Medium (中) / Low (低)
- 每条告警关联一个威胁评估

**操作按钮**:
- 升级 (Escalate): 将告警严重程度提升
- 解决 (Resolve): 标记告警已解决
- 分配 (Assign): 将告警分配给处理人

**邮件通知**:
- 系统已启用SMTP邮件通知
- 高严重度告警会触发邮件通知

### 6.2 验证步骤

1. 确认告警列表有数据
2. 尝试对一条告警执行"解决"操作，确认状态变更
3. 确认告警与威胁列表中的记录对应

---

## 7. 客户管理 (CustomerMgmt) 验证

### 7.1 当前客户数据

| 客户ID | 名称 | 设备数量 |
|--------|------|----------|
| customer_a | Acme Corporation | 16台蜜罐设备 (DEV-001 至 DEV-016) |
| demo-customer | Demo Customer | 1台设备 (序列号: 9d262111f2476d34) |

### 7.2 验证内容

- 客户列表: 确认上述两个客户存在
- 设备列表: 点击客户查看其绑定的设备
- 多租户隔离: 使用 demo_admin 账号登录，确认只能看到 demo-customer 的数据

---

## 8. ML检测 (MlDetection) 验证

### 8.1 页面验证

打开ML检测页面，应显示:
- 模型状态信息
- ML预测记录

### 8.2 命令行验证

以下命令需要SSH到服务器执行:

```bash
ssh kylecui@10.174.1.222
```

**检查ML服务健康状态:**

```bash
sudo kubectl exec -n threat-detection deploy/ml-detection -- curl -s http://localhost:8086/health
```

期望输出:
```json
{
  "status": "ok",
  "modelLoaded": true,
  "modelsAvailable": {
    "tier1": true,
    "tier1_bigru": true,
    "tier2": true,
    "tier2_bigru": true,
    "tier3": true,
    "tier3_bigru": true
  },
  "kafkaConnected": true
}
```

**查看模型详情:**

```bash
sudo kubectl exec -n threat-detection deploy/ml-detection -- curl -s http://localhost:8086/api/v1/ml/models
```

期望输出: 3个tier的模型信息，每个tier包含autoencoder和bigru两个模型:
```json
[
  {"tier":1,"available":true,"threshold":0.3,"modelPath":"/app/models/autoencoder_v1_tier1.onnx","bigruAvailable":true,"bigruModelPath":"/app/models/bigru_v1_tier1.onnx","optimalAlpha":0.8},
  {"tier":2,"available":true,"threshold":0.3,"modelPath":"/app/models/autoencoder_v1_tier2.onnx","bigruAvailable":true,"bigruModelPath":"/app/models/bigru_v1_tier2.onnx","optimalAlpha":0.8},
  {"tier":3,"available":true,"threshold":0.3,"modelPath":"/app/models/autoencoder_v1_tier3.onnx","bigruAvailable":true,"bigruModelPath":"/app/models/bigru_v1_tier3.onnx","optimalAlpha":0.8}
]
```

**查看缓冲区统计:**

```bash
sudo kubectl exec -n threat-detection deploy/ml-detection -- curl -s http://localhost:8086/api/v1/ml/buffer/stats
```

期望输出 (数值会变化):
```json
{"enabled":true,"totalKeys":139,"totalWindows":645}
```

**查看漂移检测状态:**

```bash
sudo kubectl exec -n threat-detection deploy/ml-detection -- curl -s http://localhost:8086/api/v1/ml/drift/status
```

期望输出:
```json
{
  "enabled": true,
  "psiThreshold": 0.2,
  "windowSize": 500,
  "tiers": {
    "tier1": {"hasBaseline":false,"baselineSamples":0,"recentObservations":4895,"currentWindowSize":500,"driftDetected":false},
    "tier2": {"hasBaseline":false,"baselineSamples":0,"recentObservations":555,"currentWindowSize":500,"driftDetected":false},
    "tier3": {"hasBaseline":false,"baselineSamples":0,"recentObservations":277,"currentWindowSize":277,"driftDetected":false}
  }
}
```

### 8.3 ML检测流水线说明

```
Kafka (threat-alerts topic)
    |
    v
ML Detection 消费者 (Python FastAPI)
    |
    v
ONNX Runtime 推理 (autoencoder重建误差 + BiGRU序列分析)
    |
    v
异常判定 (threshold=0.3, alpha=0.8混合权重)
    |
    v
Kafka (ml-threat-detections topic)
    |
    v
Threat Assessment 的 MlWeightService (缓存mlWeight值)
    |
    v
评分公式中的 mlWeight 因子 (范围: 0.5-3.0)
```

**模型说明:**
- Tier 1 (30秒窗口): 专注勒索软件快速检测
- Tier 2 (5分钟窗口): 主要威胁检测
- Tier 3 (15分钟窗口): APT长期行为分析

每个Tier有两个模型:
- Autoencoder: 通过重建误差检测异常
- BiGRU: 双向GRU序列模型，捕捉时间序列模式

### 8.4 数据库验证ML预测

```bash
sudo kubectl exec -n threat-detection postgres-0 -- psql -U threat_user -d threat_detection -c "SELECT tier, anomaly_type, COUNT(*) FROM ml_predictions GROUP BY tier, anomaly_type ORDER BY tier;"
```

期望输出 (数值会变化):
```
 tier |    anomaly_type     | count
------+---------------------+-------
    1 | borderline          |   254
    1 | statistical_outlier |   568
    2 | borderline          |   143
    2 | statistical_outlier |   141
    3 | statistical_outlier |    62
```

### 8.5 重要说明

当前 `ml.weight.enabled` 配置为 `false`，意味着 mlWeight 尚未纳入最终威胁评分公式。这是一个特性开关，待ML模型经过充分验证后再启用。启用方法:

1. 修改 `services/threat-assessment/src/main/resources/application.properties` 中 `ml.weight.enabled=true`
2. 重新构建并部署 threat-assessment 服务

---

## 9. 影子评分 (Shadow Scoring) 说明

### 9.1 当前状态

影子评分功能当前 **已禁用**。

### 9.2 功能说明

影子评分用于A/B测试机器学习模型。它允许同时运行两个模型:
- **冠军模型 (Champion)**: 当前正在使用的生产模型
- **挑战者模型 (Challenger)**: 新训练的待验证模型

两个模型同时对每条数据进行推理，但只有冠军模型的结果会影响实际评分。挑战者模型的结果仅用于对比统计。

### 9.3 启用步骤 (需要时)

1. 准备挑战者模型ONNX文件，放置到容器内 `/app/models/challenger/` 目录
2. 修改 `k8s/base/ml-detection.yaml`，在环境变量中添加:
   ```yaml
   - name: SHADOW_SCORING_ENABLED
     value: "true"
   - name: CHALLENGER_MODEL_DIR
     value: "/app/models/challenger"
   ```
3. 重新构建并部署 ml-detection 服务
4. 查看对比统计:
   ```bash
   sudo kubectl exec -n threat-detection deploy/ml-detection -- curl -s http://localhost:8086/api/v1/ml/shadow/stats
   ```

### 9.4 当前不建议启用

目前没有训练好的挑战者模型文件，因此无需启用。当前查询影子评分状态:

```bash
sudo kubectl exec -n threat-detection deploy/ml-detection -- curl -s http://localhost:8086/api/v1/ml/shadow/stats
```

期望输出:
```json
{"enabled":false,"challengerDir":"/app/models/challenger","challengerLoaded":false,"totalComparisons":0}
```

---

## 10. 威胁情报与TIRE集成验证

### 10.1 架构说明

威胁情报系统由两个服务协同工作:

```
Stream Processing (Flink)
    |
    | 调用 /api/v1/intel/weight?ip=xxx
    v
Threat Intelligence 服务 (端口8085)
    |
    | 调用 TireClient → /api/v1/analyze/ip
    v
TIRE 服务 (端口8000, FastAPI)
    |
    | 聚合11款插件结果
    v
返回信誉评分 → 转化为 intelWeight (1.0-4.5)
    |
    v
最终威胁评分公式中的 intelWeight 因子
```

**TIRE (Threat Intelligence Reputation Engine) 支持的11款插件:**
- AbuseIPDB
- VirusTotal
- AlienVault OTX
- GreyNoise
- Shodan
- RDAP
- Reverse DNS
- Honeynet
- Internal Flow
- ThreatBook
- 天际友盟

### 10.2 威胁情报页面验证

在左侧菜单点击"威胁情报"，应看到:
- Feed管理: 外部情报源列表
- 可以查看各Feed的状态、最后更新时间等

### 10.3 TIRE服务命令行验证

```bash
sudo kubectl run -n threat-detection curl-test --image=curlimages/curl --rm -it --restart=Never -- curl -s 'http://tire:8000/api/v1/ip/192.168.1.50'
```

期望输出:
```json
{
  "object": {"type": "ip", "value": "192.168.1.50"},
  "analysis": {
    "reputation_score": 0,
    "contextual_score": 0,
    "final_score": 0,
    "level": "Low",
    "confidence": 0.0,
    "decision": "allow_with_monitoring"
  },
  "summary": "IP 192.168.1.50 shows minimal threat indicators. Final score: 0/100. Safe for normal operations with monitoring.",
  "tags": [],
  "evidence": [],
  "metadata": {"generated_by": "Threat Intelligence Reasoning Engine", "version": "2.0.0"}
}
```

注意: 内网IP地址通常返回零分，这是正常的。TIRE插件需要外部API密钥才能查询真实互联网IP的威胁情报。

### 10.4 TIRE API端点

| 端点 | 方法 | 说明 |
|------|------|------|
| /api/v1/ip/{ip} | GET | 查询IP信誉 |
| /api/v1/analyze/ip | POST | 分析IP (JSON body: {"ip": "x.x.x.x"}) |
| /api/v1/report/generate | POST | 生成分析报告 |
| /api/v1/results/{ip}/history | GET | IP历史分析结果 |
| /api/v1/debug/sources/{ip} | GET | 调试各插件数据源 |
| /docs | GET | Swagger API文档 |

---

## 11. 系统设置 (Settings) 验证

### 11.1 TIRE插件管理

在系统设置页面中，可以管理TIRE的11款插件:
- 启用/禁用各插件
- 设置插件优先级
- 配置超时时间
- 管理API密钥

### 11.2 LLM配置

系统设置页面还支持:
- LLM连接配置
- LLM连接验证
- 共享LLM配置管理

---

## 12. 系统监控 (SystemMonitor) 验证

### 12.1 验证内容

系统监控页面显示:
- 各微服务的健康状态
- 系统日志查看

---

## 13. 测试数据发送

### 13.1 V1 数据格式 (syslog KV)

系统接受syslog KV格式的攻击日志，通过TCP发送到 `<服务器IP>:32318` (Logstash NodePort)。

> **注意**: V1 syslog格式中**不包含** `customer_id` 字段。customerId由data-ingestion服务根据 `dev_serial` 自动解析 (设备→客户映射)。

格式 (字段顺序必须严格匹配):
```
syslog_version=1.0,dev_serial=<设备序列号>,log_type=1,sub_type=4,attack_mac=<攻击MAC>,attack_ip=<攻击IP>,response_ip=<响应IP>,response_port=<端口>,line_id=1,Iface_type=1,Vlan_id=0,log_time=<Unix时间戳>
```

**字段说明:**

| 字段 | 说明 | 示例 |
|------|------|------|
| syslog_version | 协议版本，固定为1.0 | 1.0 |
| dev_serial | 设备序列号，必须绑定到某个客户 | 9d262111f2476d34 |
| log_type | 日志类型，固定为1 (攻击日志) | 1 |
| sub_type | 子类型，固定为4 | 4 |
| attack_mac | 被诱捕的内网主机MAC地址 | aa:bb:cc:dd:ee:10 |
| attack_ip | 被诱捕的内网主机IP地址 | 192.168.1.50 |
| response_ip | 蜜罐/诱饵IP | 10.0.1.1 |
| response_port | 攻击者尝试访问的端口 | 3389 |
| line_id | 行ID，固定为1 | 1 |
| Iface_type | 接口类型，固定为1 | 1 |
| Vlan_id | VLAN ID，通常为0 | 0 |
| log_time | Unix秒级时间戳 | 1743562800 |
| eth_type | (可选) 以太网类型 | 2048 |
| ip_type | (可选) IP协议类型 | 6 |

**可用的客户和设备:**

| customer_id | 设备序列号 | 说明 |
|---|---|---|
| demo-customer | 9d262111f2476d34 | 演示客户 |
| customer_a | DEV-001 至 DEV-016 | Acme Corporation (16个蜜罐设备) |

### 13.2 V1 发送单条测试数据

```bash
# 先在本地展开时间戳，再发送 (避免远程shell变量展开问题)
TIMESTAMP=$(date +%s) && echo "syslog_version=1.0,dev_serial=9d262111f2476d34,log_type=1,sub_type=4,attack_mac=aa:bb:cc:dd:ee:10,attack_ip=192.168.1.50,response_ip=10.0.1.1,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=${TIMESTAMP}" | nc -q1 <服务器IP> 32318
```

### 13.3 V1 批量发送测试数据

以下脚本发送50条测试数据，使用不同的端口和时间戳:

```bash
SERVER_IP="<服务器IP>"
PORTS=(22 80 443 3389 8080 21 25 53 3306 5432)
for i in $(seq 1 50); do
  PORT=${PORTS[$((RANDOM % 10))]}
  TS=$(($(date +%s) - RANDOM % 3600))
  MAC_SUFFIX=$((RANDOM % 90 + 10))
  IP_LAST=$((RANDOM % 254 + 1))
  echo "syslog_version=1.0,dev_serial=9d262111f2476d34,log_type=1,sub_type=4,attack_mac=aa:bb:cc:dd:ee:${MAC_SUFFIX},attack_ip=192.168.1.${IP_LAST},response_ip=10.0.1.1,response_port=${PORT},line_id=1,Iface_type=1,Vlan_id=0,log_time=${TS}" | nc -q1 $SERVER_IP 32318
  sleep 0.1
done
echo "发送完毕，共50条"
```

### 13.4 V1 重要提示

- **无需 customer_id**: V1 syslog格式中不包含customer_id字段。系统根据 `dev_serial` 自动查找绑定的客户
- **dev_serial 必须匹配**: 设备序列号必须在客户管理中绑定到某个客户，否则customerId默认为"unknown"
- **字段顺序严格**: data-ingestion的正则解析器要求字段严格按 `syslog_version, dev_serial, log_type, sub_type, attack_mac, attack_ip, response_ip, response_port, line_id, Iface_type, Vlan_id, log_time` 顺序排列
- **TCP协议 (非UDP)**: Logstash配置为TCP输入，使用 `nc` (不加 `-u` 参数)。UDP发送会静默丢失
- **时间戳要分散**: 建议使用不同的 log_time 值，确保Flink的事件时间窗口能正常关闭并触发计算。如果所有事件使用相同时间戳，Flink窗口可能不会触发
- **等待时间**: 发送后请等待30-60秒，Flink的30秒窗口关闭后数据才会进入threat-alerts，再经过处理后出现在Dashboard
- **数据流向确认**: 发送数据后可通过以下命令确认Kafka收到消息:
  ```bash
  sudo kubectl exec -n threat-detection deploy/kafka -- kafka-console-consumer --bootstrap-server localhost:9092 --topic threat-alerts --from-beginning --max-messages 3 --timeout-ms 10000
  ```

### 13.5 V2 MQTT测试 (V2哨兵)

V2哨兵设备通过MQTT协议发送JSON格式的攻击事件，连接到EMQX Broker的NodePort 31883。

#### MQTT Topic格式

```
jz/<deviceId>/logs/attack
```

其中 `<deviceId>` 为设备序列号 (如 `9d262111f2476d34`)。data-ingestion服务订阅 `jz/+/logs/#` 并从topic路径中提取deviceId，自动解析对应的customerId。

#### V2 JSON事件格式

```json
{
  "v": 2,
  "device_id": "9d262111f2476d34",
  "seq": 1001,
  "ts": "2026-04-04T10:30:00+08:00",
  "type": "attack",
  "data": {
    "src_ip": "10.0.1.200",
    "src_mac": "aa:bb:cc:11:22:33",
    "guard_ip": "10.0.1.50",
    "dst_port": 8080,
    "ifindex": 1,
    "vlan_id": 100,
    "ethertype": 2048,
    "ip_proto": 6
  }
}
```

**V2字段说明:**

| JSON字段 | 说明 | 映射到 |
|----------|------|--------|
| v | 协议版本，必须为2 | — |
| device_id | 设备序列号 | deviceSerial |
| seq | 序列号 | — |
| ts | ISO 8601时间戳 (必须包含时区偏移) | timestamp / logTime |
| type | 事件类型，"attack" | — |
| data.src_ip | 被诱捕的内网主机IP | attackIp |
| data.src_mac | 被诱捕的内网主机MAC | attackMac |
| data.guard_ip | 蜜罐/诱饵IP | responseIp |
| data.dst_port | 攻击者尝试访问的端口 | responsePort |
| data.ifindex | 接口索引 | — |
| data.vlan_id | VLAN ID | — |
| data.ethertype | 以太网类型 (2048=IPv4) | — |
| data.ip_proto | IP协议 (6=TCP, 17=UDP) | — |

#### 发送单条V2测试数据

```bash
# 使用 mosquitto_pub 发送 (服务器上已安装)
TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%S+00:00) && \
mosquitto_pub -h <服务器IP> -p 31883 \
  -t 'jz/9d262111f2476d34/logs/attack' \
  -m "{\"v\":2,\"device_id\":\"9d262111f2476d34\",\"seq\":1001,\"ts\":\"${TIMESTAMP}\",\"type\":\"attack\",\"data\":{\"src_ip\":\"10.0.1.200\",\"src_mac\":\"aa:bb:cc:11:22:33\",\"guard_ip\":\"10.0.1.50\",\"dst_port\":8080,\"ifindex\":1,\"vlan_id\":100,\"ethertype\":2048,\"ip_proto\":6}}"
```

#### 批量发送V2测试数据

```bash
SERVER_IP="<服务器IP>"
PORTS=(22 80 443 3389 8080 445 21 25 3306 5432)
for i in $(seq 1 20); do
  PORT=${PORTS[$((RANDOM % 10))]}
  SEQ=$((1000 + i))
  MAC_SUFFIX=$(printf "%02x" $((RANDOM % 256)))
  IP_LAST=$((RANDOM % 254 + 1))
  TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%S+00:00)
  mosquitto_pub -h $SERVER_IP -p 31883 \
    -t 'jz/9d262111f2476d34/logs/attack' \
    -m "{\"v\":2,\"device_id\":\"9d262111f2476d34\",\"seq\":${SEQ},\"ts\":\"${TIMESTAMP}\",\"type\":\"attack\",\"data\":{\"src_ip\":\"10.0.1.${IP_LAST}\",\"src_mac\":\"aa:bb:cc:dd:ee:${MAC_SUFFIX}\",\"guard_ip\":\"10.0.1.50\",\"dst_port\":${PORT},\"ifindex\":1,\"vlan_id\":100,\"ethertype\":2048,\"ip_proto\":6}}"
  sleep 0.2
done
echo "V2 MQTT测试数据发送完毕，共20条"
```

#### ⚠️ V2时间戳注意事项 (关键!)

**时区偏移必须正确**，否则事件会被Flink的watermark机制丢弃为"迟到事件"：

- ✅ `date -u +%Y-%m-%dT%H:%M:%S+00:00` — UTC时间配 `+00:00` 偏移 (推荐)
- ✅ `date +%Y-%m-%dT%H:%M:%S+08:00` — 本地时间 (北京时区) 配 `+08:00` 偏移
- ❌ `date -u +%Y-%m-%dT%H:%M:%S+08:00` — **错误!** UTC时间配了+08:00偏移，实际epoch时间会比当前时间早8小时，Flink会认为是迟到事件而丢弃

**原理**: Flink使用事件时间 (event time) 的watermark机制。如果V2事件的时间戳解析后epoch值远早于当前watermark (通常由V1事件或其他正常事件推进)，该事件会被当作迟到事件丢弃，不参与窗口计算。

---

## 14. 数据库验证

### 14.1 连接数据库

```bash
sudo kubectl exec -n threat-detection postgres-0 -- psql -U threat_user -d threat_detection
```

退出数据库: 输入 `\q` 或按 `Ctrl+D`

### 14.2 常用查询

**查看威胁评估记录数:**
```sql
SELECT COUNT(*) FROM threat_assessments;
```

**查看最近的评估记录 (含端口列表):**
```sql
SELECT customer_id, attack_mac, attack_ip, threat_level, threat_score, port_list, assessed_at
FROM threat_assessments
ORDER BY assessed_at DESC
LIMIT 10;
```

**查看攻击事件数:**
```sql
SELECT COUNT(*) FROM attack_events;
```

**查看ML预测统计:**
```sql
SELECT tier, anomaly_type, COUNT(*) FROM ml_predictions GROUP BY tier, anomaly_type ORDER BY tier;
```

**查看端口分布:**
```sql
SELECT port_list, COUNT(*) as cnt
FROM threat_assessments
WHERE port_list IS NOT NULL
GROUP BY port_list
ORDER BY cnt DESC
LIMIT 10;
```

**按客户统计:**
```sql
SELECT customer_id, COUNT(*) as total, AVG(threat_score)::numeric(10,2) as avg_score
FROM threat_assessments
GROUP BY customer_id;
```

**查看告警数:**
```sql
SELECT severity, COUNT(*) FROM threat_alerts GROUP BY severity ORDER BY severity;
```

**查看数据库表大小:**
```sql
SELECT relname as table_name,
       pg_size_pretty(pg_total_relation_size(relid)) as total_size
FROM pg_catalog.pg_statio_user_tables
ORDER BY pg_total_relation_size(relid) DESC;
```

### 14.3 单条查询 (不进入交互模式)

```bash
sudo kubectl exec -n threat-detection postgres-0 -- psql -U threat_user -d threat_detection -c "SELECT COUNT(*) FROM threat_assessments;"
```

---

## 15. Pod状态检查

### 15.1 检查所有Pod

```bash
sudo kubectl get pods -n threat-detection
```

期望输出: 18个Pod全部显示 `Running` 状态，READY 列为 `1/1`:

```
NAME                                              READY   STATUS    RESTARTS   AGE
alert-management-xxxxx                            1/1     Running   0          xxh
api-gateway-xxxxx                                 1/1     Running   0          xxh
config-server-xxxxx                               1/1     Running   1          xxh
customer-management-xxxxx                         1/1     Running   1          xxh
data-ingestion-xxxxx                              1/1     Running   0          xxh
emqx-xxxxx                                        1/1     Running   1          xxh
frontend-xxxxx                                    1/1     Running   0          xxh
kafka-xxxxx                                       1/1     Running   0          xxh
logstash-xxxxx                                    1/1     Running   2          xxh
ml-detection-xxxxx                                1/1     Running   0          xxh
postgres-0                                        1/1     Running   1          xxh
redis-xxxxx                                       1/1     Running   1          xxh
stream-processing-jobmanager-xxxxx                1/1     Running   0          xxh
stream-processing-taskmanager-xxxxx               1/1     Running   0          xxh
threat-assessment-xxxxx                           1/1     Running   0          xxh
threat-intelligence-xxxxx                         1/1     Running   1          xxh
tire-xxxxx                                        1/1     Running   0          xxh
zookeeper-xxxxx                                   1/1     Running   0          xxh
```

### 15.2 查看Pod日志

```bash
# 查看指定服务的日志 (最后100行)
sudo kubectl logs -n threat-detection deploy/<服务名> --tail=100

# 示例:
sudo kubectl logs -n threat-detection deploy/threat-assessment --tail=100
sudo kubectl logs -n threat-detection deploy/ml-detection --tail=100
sudo kubectl logs -n threat-detection deploy/stream-processing-jobmanager --tail=100
```

### 15.3 检查Kafka主题

```bash
sudo kubectl exec -n threat-detection deploy/kafka -- kafka-topics --list --bootstrap-server localhost:9092
```

期望看到以下主题:
- `attack-events` — 原始攻击事件
- `status-events` — 状态事件
- `threat-alerts` — Flink输出的威胁告警
- `minute-aggregations` — 分钟级聚合
- `device-health-alerts` — 设备健康告警
- `ml-threat-detections` — ML检测结果
- `__consumer_offsets` — Kafka内部主题

### 15.4 检查磁盘空间

```bash
df -h /
```

确保磁盘使用率低于85%。如果超过，需要清理数据。

---

## 16. 威胁评分公式

### 16.1 公式

```
threatScore = (attackCount * uniqueIps * uniquePorts) * timeWeight * ipWeight * portWeight * deviceWeight * netWeight * intelWeight * mlWeight
```

### 16.2 各因子说明

| 因子 | 说明 | 取值范围 |
|------|------|----------|
| attackCount | 攻击尝试次数 (窗口内) | 正整数 |
| uniqueIps | 唯一攻击IP数 (横向移动范围) | 正整数 |
| uniquePorts | 唯一端口数 (攻击意图多样性) | 正整数 |
| timeWeight | 时间权重 (深夜1.2, 工作时间1.0) | 0.8 - 1.2 |
| ipWeight | IP多样性放大 | 1.0 - 2.0 |
| portWeight | 端口多样性权重 | 1.0 - 2.0 |
| deviceWeight | 多设备覆盖奖励 | 1.0 - 1.5 |
| netWeight | 网段权重 (按客户+CIDR配置) | 0.01 - 10.0 |
| intelWeight | 威胁情报权重 (IOC置信度+严重性) | 1.0 - 4.5 |
| mlWeight | ML异常检测建议性倍率 (当前未启用) | 0.5 - 3.0 |

### 16.3 威胁等级

| 等级 | 分数范围 | 说明 |
|------|----------|------|
| INFO | < 10 | 信息级，无需关注 |
| LOW | 10 - 50 | 低风险 |
| MEDIUM | 50 - 100 | 中等风险，需关注 |
| HIGH | 100 - 200 | 高风险，需处理 |
| CRITICAL | > 200 | 危急，需立即处理 |

---

## 17. 离线部署指南

本节介绍如何将威胁检测系统部署到无法访问Docker Hub的机器上（如国内网络环境）。

### 17.1 前置条件

| 条件 | 说明 |
|------|------|
| 目标机器 | Ubuntu 20.04+ (推荐 24.04 LTS)，至少 8GB RAM，50GB 磁盘 |
| K3s | 已安装K3s (如未安装，可使用离线安装包) |
| 源代码 | 已clone `threat-detection-system` 仓库到目标机器 |
| 镜像包 | 已拷贝 `threat-detection-images.tar.gz` 到目标机器 |

### 17.2 在已有环境导出镜像

在已运行系统的机器上执行:

```bash
sudo bash scripts/k3s-export-images.sh
```

输出文件: `threat-detection-images.tar.gz` (约3.7 GB)，包含全部19个镜像。

将此文件拷贝到目标机器:
```bash
scp threat-detection-images.tar.gz user@target-machine:/home/user/threat-detection-system/
```

### 17.3 在目标机器导入镜像

```bash
sudo bash scripts/k3s-import-images.sh threat-detection-images.tar.gz
```

此命令会将所有镜像导入K3s的containerd中。

### 17.4 构建应用镜像（如需从源码构建）

如果您需要从源码构建应用镜像（而非使用导出的镜像），执行:

```bash
sudo bash scripts/k3s-build-images.sh
```

此命令会编译所有Java/Python服务并构建Docker镜像，自动导入到K3s。

### 17.5 部署系统

```bash
sudo bash scripts/k3s-deploy.sh
```

部署脚本会自动执行:
1. **镜像预检查**: 验证全部19个必需镜像存在于K3s containerd中
2. **命名空间创建**: 创建 `threat-detection` 命名空间
3. **按顺序部署**: PostgreSQL → Redis → ZooKeeper → Kafka → 应用服务 → 前端
4. **健康检查**: 等待各服务启动并验证Ready状态

### 17.6 部署后验证

```bash
# 检查全部Pod状态
sudo kubectl get pods -n threat-detection

# 验证前端可访问
curl -s -o /dev/null -w "%{http_code}" http://localhost:30080

# 发送测试数据验证E2E数据流
LOGSTASH_PORT=$(sudo kubectl get svc -n threat-detection logstash -o jsonpath='{.spec.ports[0].nodePort}')
echo "log_type=1,customer_id=demo-customer,dev_serial=9d262111f2476d34,attack_mac=aa:bb:cc:dd:ee:01,attack_ip=192.168.1.100,response_ip=10.0.1.1,response_port=3389,log_time=$(date +%s)" | nc -q0 localhost $LOGSTASH_PORT
```

### 17.7 离线安装K3s (可选)

如果目标机器没有K3s:

1. 在有网络的机器下载K3s二进制文件:
   ```bash
   wget https://github.com/k3s-io/k3s/releases/download/v1.28.4+k3s2/k3s
   wget https://github.com/k3s-io/k3s/releases/download/v1.28.4+k3s2/k3s-airgap-images-amd64.tar.gz
   ```

2. 拷贝到目标机器并安装:
   ```bash
   sudo cp k3s /usr/local/bin/ && sudo chmod +x /usr/local/bin/k3s
   sudo mkdir -p /var/lib/rancher/k3s/agent/images/
   sudo cp k3s-airgap-images-amd64.tar.gz /var/lib/rancher/k3s/agent/images/
   curl -sfL https://get.k3s.io | INSTALL_K3S_SKIP_DOWNLOAD=true sh -
   ```

### 17.8 镜像清单

以下是系统所需的全部19个镜像:

**基础设施镜像 (8个):**

| 镜像 | 用途 | 大小 |
|------|------|------|
| postgres:15-alpine | 数据库 | ~105M |
| redis:7-alpine | 缓存 | ~17M |
| busybox:1.35 | Init容器 | ~2M |
| confluentinc/cp-zookeeper:7.4.0 | Kafka协调 | ~418M |
| confluentinc/cp-kafka:7.4.0 | 消息队列 | ~418M |
| docker.elastic.co/logstash/logstash:8.11.0 | 日志收集 | ~439M |
| emqx/emqx:5.5.1 | MQTT代理 | ~199M |
| curlimages/curl:latest | 健康检查 | ~11M |

**应用镜像 (11个):**

| 镜像 | 用途 | 大小 |
|------|------|------|
| threat-detection/data-ingestion:latest | 数据摄取 | ~355M |
| threat-detection/stream-processing:1.0 | Flink流处理 | ~785M |
| threat-detection/threat-assessment:latest | 威胁评估 | ~351M |
| threat-detection/alert-management:latest | 告警管理 | ~367M |
| threat-detection/customer-management:latest | 客户管理 | ~370M |
| threat-detection/threat-intelligence:latest | 威胁情报 | ~246M |
| threat-detection/api-gateway:latest | API网关 | ~250M |
| threat-detection/config-server:latest | 配置中心 | ~319M |
| threat-detection/ml-detection:latest | ML检测 | ~1.4G |
| threat-detection/tire:latest | TIRE引擎 | ~173M |
| threat-detection/frontend:latest | 前端界面 | ~52M |

### 17.9 测试人员快速部署清单 (Fresh Machine)

以下为**完整的离线部署步骤**，适合测试人员在全新机器上从零开始部署。

#### 准备阶段（在源机器上操作）

1. **导出镜像包**:
   ```bash
   cd ~/threat-detection-system
   sudo bash scripts/k3s-export-images.sh
   # 生成: threat-detection-images.tar.gz (~3.7 GB)
   ```

2. **准备传输文件清单**:
   - `threat-detection-images.tar.gz` — 全部19个镜像
   - `threat-detection-system/` — 项目源码 (包含K8s清单和部署脚本)

3. **拷贝到目标机器**:
   ```bash
   scp threat-detection-images.tar.gz user@目标IP:/home/user/
   scp -r threat-detection-system/ user@目标IP:/home/user/
   ```

#### 部署阶段（在目标机器上操作）

**第一步: 安装K3s** (如果尚未安装)

对于有网络的机器:
```bash
curl -sfL https://get.k3s.io | sh -
```

对于无网络的机器，参考 [17.7节](#177-离线安装k3s-可选)。

**第二步: 导入镜像**
```bash
cd ~/threat-detection-system
sudo bash scripts/k3s-import-images.sh /home/user/threat-detection-images.tar.gz
```

验证镜像已导入:
```bash
sudo crictl images | grep -c -v rancher
# 应输出 19 (或更多)
```

**第三步: 部署**
```bash
sudo bash scripts/k3s-deploy.sh
# 脚本会自动:
# 1. 预检查19个镜像是否全部存在
# 2. 创建 threat-detection 命名空间
# 3. 按依赖顺序部署所有18个Pod
# 4. 等待全部Pod就绪
# 总耗时约 5-10 分钟
```

**第四步: 验证**
```bash
# 确认全部Pod为Running
sudo kubectl get pods -n threat-detection
# 期望: 全部18个Pod均为 Running / 1/1 Ready

# 检查前端可访问
curl -s -o /dev/null -w "HTTP %{http_code}\n" http://localhost:30080
# 期望: HTTP 200

# 检查API网关
curl -s http://localhost:30080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | head -c 100
# 期望: 返回包含 "token" 的JSON

# 发送测试syslog验证E2E数据流 (V1)
LOGSTASH_PORT=$(sudo kubectl get svc -n threat-detection logstash -o jsonpath='{.spec.ports[0].nodePort}')
TIMESTAMP=$(date +%s)
echo "syslog_version=1.0,dev_serial=9d262111f2476d34,log_type=1,sub_type=4,attack_mac=aa:bb:cc:dd:ee:01,attack_ip=192.168.1.100,response_ip=10.0.1.1,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=${TIMESTAMP}" | nc -q1 localhost $LOGSTASH_PORT
echo "等待60秒让Flink窗口关闭..."
sleep 60
# 然后在前端 Dashboard 或 Analytics 页面查看数据

# V2 MQTT测试 (mosquitto_pub已预装)
TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%S+00:00)
mosquitto_pub -h localhost -p 31883 -t 'jz/9d262111f2476d34/logs/attack' -m "{\"v\":2,\"device_id\":\"9d262111f2476d34\",\"seq\":1001,\"ts\":\"${TIMESTAMP}\",\"type\":\"attack\",\"data\":{\"src_ip\":\"10.0.1.200\",\"src_mac\":\"aa:bb:cc:11:22:33\",\"guard_ip\":\"10.0.1.50\",\"dst_port\":8080,\"ifindex\":1,\"vlan_id\":100,\"ethertype\":2048,\"ip_proto\":6}}"
```

#### 常见问题速查

| 问题 | 原因 | 解决 |
|------|------|------|
| `ImagePullBackOff` | 镜像未导入 | `sudo bash scripts/k3s-import-images.sh <tar.gz路径>` |
| Pod一直`Pending` | 资源不足 | 检查 `free -h`，至少需要8GB RAM |
| 前端打不开 | api-gateway未启动 | `sudo kubectl logs -n threat-detection -l app=api-gateway --tail=30` |
| 发送测试数据无效果 | Logstash未就绪 | 确认logstash Pod为Running后重试 |
| Flink窗口不关闭 | 只发了1条数据 | 多发几条不同时间戳的数据触发watermark推进 |

---

## 18. 常见问题排查

### 18.1 Dashboard或Analytics图表为空

**原因**: 选定时间范围内没有数据。

**解决方法**:
1. 参考 [第13节](#13-测试数据发送) 发送测试数据
2. 等待30-60秒后刷新页面
3. 确认时间范围选择正确 (Analytics页面切换到30天试试)

### 18.2 Pod状态非Running

**排查步骤**:
```bash
# 查看Pod状态
sudo kubectl get pods -n threat-detection

# 查看异常Pod的日志
sudo kubectl logs -n threat-detection <pod-name>

# 查看Pod详情 (事件信息)
sudo kubectl describe pod -n threat-detection <pod-name>
```

**常见状态**:
- `CrashLoopBackOff`: 服务启动失败，反复重启。查看日志定位原因。
- `Pending`: 资源不足或调度问题。
- `ImagePullBackOff`: 镜像拉取失败。v3.1.3已为所有镜像设置 `imagePullPolicy: IfNotPresent`，使用离线导入的镜像即可避免此问题。如仍出现，运行 `sudo bash scripts/k3s-import-images.sh threat-detection-images.tar.gz` 导入镜像。

### 18.3 端口分布数据为空

**排查**:
```bash
sudo kubectl exec -n threat-detection postgres-0 -- psql -U threat_user -d threat_detection -c "SELECT port_list, COUNT(*) FROM threat_assessments WHERE port_list IS NOT NULL GROUP BY port_list LIMIT 5;"
```

如果返回0条记录，说明新数据中port_list未被填充。v3.1.0已修复此问题，确认使用的是最新镜像。

### 18.4 Kafka相关问题

**查看主题列表:**
```bash
sudo kubectl exec -n threat-detection deploy/kafka -- kafka-topics --list --bootstrap-server localhost:9092
```

**查看消费者组:**
```bash
sudo kubectl exec -n threat-detection deploy/kafka -- kafka-consumer-groups --list --bootstrap-server localhost:9092
```

**查看消费者组lag:**
```bash
sudo kubectl exec -n threat-detection deploy/kafka -- kafka-consumer-groups --describe --group <group-name> --bootstrap-server localhost:9092
```

### 18.5 磁盘空间不足

```bash
# 检查磁盘使用
df -h /

# 检查大目录
du -sh /var/lib/rancher /var/lib/kubelet

# 清理已停止的Docker容器和悬空镜像
sudo docker system prune -f
```

如果数据库过大，可以清理测试数据 (仅在测试环境):
```bash
sudo kubectl exec -n threat-detection postgres-0 -- psql -U threat_user -d threat_detection -c "TRUNCATE attack_events;"
```

### 18.6 服务重启

```bash
# 重启指定服务 (删除Pod，Deployment会自动重建)
sudo kubectl delete pod -n threat-detection -l app=<服务名> --force --grace-period=0

# 示例: 重启threat-assessment
sudo kubectl delete pod -n threat-detection -l app=threat-assessment --force --grace-period=0

# 重启Flink (需要同时重启jobmanager和taskmanager)
sudo kubectl delete pod -n threat-detection -l app=stream-processing,component=jobmanager --force --grace-period=0
sudo kubectl delete pod -n threat-detection -l app=stream-processing,component=taskmanager --force --grace-period=0
```

### 18.7 无法登录

- 确认访问地址: `http://10.174.1.222:30080`
- 确认账号密码: admin / admin123
- 如果页面无法加载，检查frontend Pod是否Running
- 如果API请求失败，检查api-gateway Pod是否Running

---

## 19. 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v3.1.5 | 2026-04-04 | V2 MQTT端到端验证通过。操作手册全面修正：V1 syslog格式去除错误的customer_id字段、补充完整字段列表和顺序要求；新增V2 MQTT测试章节 (topic格式、JSON事件格式、mosquitto_pub命令、时区注意事项)；修正快速部署清单中的测试命令。data-ingestion调试日志清理。 |
| v3.1.4 | 2026-04-04 | Logstash输出从Kafka直连改为HTTP到data-ingestion (修复V1 syslog customerId解析)。EMQX添加NodePort 31883支持集群外MQTT设备。Logstash添加wait-for-data-ingestion initContainer。操作手册增加测试人员快速部署清单。 |
| v3.1.3 | 2026-04-03 | 离线部署支持：所有K8s清单添加 `imagePullPolicy: IfNotPresent`，新增镜像导出/导入脚本，部署预检查。修复Flink因V1 syslog事件customerId为空导致的CrashLoopBackOff。修复镜像导出脚本对Docker Hub library镜像的兼容性。 |
| v3.1.2 | 2026-04-03 | K8s稳定性加固：Kafka init容器、topic-init转CronJob、端口统一为9092、ZooKeeper版本对齐7.4.0、各服务init容器添加依赖等待。 |
| v3.1.1 | 2026-04-02 | UI图表修复：端口攻击分布、威胁趋势24小时图表数据链路修复。TIRE源代码提取。部署工具链 (k3s-build-images.sh, k3s-deploy.sh)。 |
| v3.1.0 | 2026-04-02 | 首个全功能验证版本。全部18个服务验证通过。ML检测6模型已加载。TIRE信誉引擎已验证。完成全链路数据流测试。 |

---

*本手册基于 v3.1.5 版本编写，适用于 K3s 单节点测试环境。*
*如有问题请联系系统管理员。*
