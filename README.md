# 🚀 威胁检测系统 (Threat Detection System)

一个现代化的、可扩展的云原生蜜罐威胁检测平台，基于微服务架构构建。

---

## 📚 文档导航指南

**请按照标准三步流程使用文档系统：**

1. **📋 第一步**: 阅读本 README.md 了解项目概况
2. **🔍 第二步**: 查看 [📚 文档索引](docs/DOCUMENTATION_INDEX.md) 快速定位所需文档
3. **📂 第三步**: 进入相应子目录获取详细信息
   - API文档 → `docs/api/`
   - 设计规范 → `docs/design/`
   - 测试指南 → `docs/testing/`
   - 构建部署 → `docs/build/` (含部署指南)
   - RBAC文档 → `docs/rbac/`
   - ML文档 → `docs/ml/`
   - TIRE文档 → `docs/tire/`

📄 **快速导航**: [DOCS_NAVIGATION.md](./DOCS_NAVIGATION.md)

---

## 🏗️ 系统架构

本系统由以下微服务组成：

| 服务 | 状态 | 端口 | 说明 |
|------|------|------|------|
| **数据摄取服务** (Data Ingestion) | ✅ 完成 | 8080 | 接收rsyslog/logstash日志并发布到Kafka，支持V1 syslog KV + V2 MQTT JSON，批量处理，心跳持久化 |
| **流处理服务** (Stream Processing) | ✅ 完成 | 8081 (Flink UI) | Apache Flink实时威胁检测，3级时间窗口 (30s/5min/15min)，多维度评分算法 |
| **告警管理服务** (Alert Management) | ✅ 完成 | 8082 | 多通道通知 (Email/SMS/Webhook/Slack/Teams)，智能去重和升级 |
| **威胁评估服务** (Threat Assessment) | ✅ 完成 | 8083 | 威胁评分和风险评估，历史趋势分析，设备管理，白名单 |
| **客户管理服务** (Customer Management) | ✅ 完成 | 8084 | 客户CRUD、设备绑定、多租户层级、通知配置 |
| **威胁情报服务** (Threat Intelligence) | ✅ 完成 | 8085 | 威胁情报指标管理、IP信誉查询、多源IOC聚合 |
| **API网关** (API Gateway) | ✅ 完成 | 8888 / 30080 | 统一入口，JWT RBAC鉴权，Spring Cloud Gateway (WebFlux)，系统配置管理，TIRE插件配置，LLM验证 |
| **配置服务器** (Config Server) | ✅ 完成 | 8899 | Spring Cloud Config Server (native backend)，统一配置中心 |
| **ML检测服务** (ML Detection) | ✅ 完成 | 8086 | PyTorch自编码器+BiGRU异常检测，ONNX Runtime推理，3级模型，Kafka异步集成 |
| **TIRE服务** (TIRE) | ✅ 完成 | 5000 | 威胁情报信誉引擎 (Threat Intelligence Reputation Engine)，11款插件集成，LLM支持 |
| **前端界面** (Frontend) | ✅ 完成 | 3000 / 30080 | React SPA 仪表板，包含分析、告警、客户、ML检测、情报及系统设置 |

## 🛠️ 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 后端 | Spring Boot | 3.1.5 (OpenJDK 21 LTS) |
| 前端 | React | 18 |
| 事件流 | Apache Kafka | 3.4+ |
| 流处理 | Apache Flink | 1.17+ |
| 容器化 | Docker + Docker Compose | — |
| 编排 | Kubernetes (K3s) | 1.25+ |
| 构建工具 | Maven | 3.8.7 |
| 数据库 | PostgreSQL | 15 |
| 缓存 | Redis | 可选 |
| MQTT Broker | EMQX | 5.5.1 |
| ML推理 | PyTorch + ONNX Runtime | 2.2+ |
| TIRE后端 | Python (FastAPI) | 3.11+ |

## 🚀 快速启动

### 开发环境 (Docker)

```bash
# 克隆仓库
git clone <repository-url>
cd threat-detection-system

# 启动所有服务
cd docker && docker-compose up -d

# 等待服务启动 (约60秒)
sleep 60

# 验证服务状态
curl http://localhost:8080/actuator/health          # 数据摄取服务
curl http://localhost:8081/overview                  # Flink Web UI
curl http://localhost:8082/actuator/health          # 告警管理服务
curl http://localhost:8083/api/v1/assessment/health  # 威胁评估服务
curl http://localhost:8084/actuator/health          # 客户管理服务
curl http://localhost:8085/actuator/health          # 威胁情报服务
curl http://localhost:8086/health                    # ML检测服务
curl http://localhost:8888/actuator/health          # API网关

# 查看日志
docker-compose logs -f
```

### 生产环境 (Kubernetes)

```bash
kubectl apply -k k8s/overlays/development   # 开发环境
kubectl apply -k k8s/overlays/production    # 生产环境
kubectl get pods -n threat-detection-dev    # 检查部署状态
```

## 📊 数据流

```
V1哨兵 (syslog KV) → rsyslog:9080 → Data Ingestion → Kafka (attack-events)
V2哨兵 (MQTT JSON) → EMQX:1883  ↗                        ↓
                                                    Flink Stream Processing
                                                          ↓
                                                    Kafka (threat-alerts)
                                                      ↓         ↓
                                   Threat Assessment ← → PostgreSQL
                                          ↓           ↓         ↓
                                   TIRE Enrichment ← → ML Detection (PyTorch/ONNX)
                                          ↓                     ↓
                                   Alert Management ← Kafka (ml-threat-detections)
                                          ↓
                                   Email/SMS/Slack/Webhook/Teams
```

1. **日志摄取 (V1)**: rsyslog:9080 → 数据摄取服务，支持单条和批量处理 (syslog KV格式)
2. **日志摄取 (V2)**: EMQX MQTT Broker → 数据摄取服务，支持JSON事件 (attack/sniffer/threat/heartbeat等7类)
3. **事件发布**: 结构化事件发布到Kafka主题 (`attack-events`, `status-events`)
4. **实时处理**: Apache Flink多维度威胁评分 (30s/5min/15min 三级时间窗口)
5. **威胁评估**: 风险等级评估 + 历史趋势分析 → PostgreSQL持久化
6. **ML检测**: PyTorch自编码器+BiGRU异常检测 → ONNX Runtime推理 → mlWeight建议性评分倍率 (0.5-3.0)
7. **TIRE增强**: 11款插件情报聚合 + LLM验证，为告警提供深度上下文
8. **告警通知**: 多通道通知 + 智能去重 + 升级策略

## 🛡️ 核心特性

### 🔐 RBAC & 多租户架构
- **层级管理**: SuperAdmin (系统管理) → TenantAdmin (分销商/大客户) → CustomerUser (普通用户)
- **鉴权机制**: 统一 JWT 认证，支持 `POST /api/v1/auth/login` 登录
- **权限控制**: 基于角色的菜单可见性与 API 访问控制 (RBAC)

### 🔍 TIRE 威胁情报引擎
- **多源聚合**: 集成 AbuseIPDB, VirusTotal, OTX, GreyNoise, Shodan, RDAP, Reverse DNS, Honeynet, Internal Flow, ThreatBook, 天际友盟等 11 款插件
- **智能集成**: 支持 LLM 连接验证，插件启用/优先级/超时管理

### 🤖 ML 检测流水线
- **三级模型**: 
  - Tier 1: 30s 窗口，专注勒索软件检测
  - Tier 2: 5min 窗口，主要威胁检测
  - Tier 3: 15min 窗口，APT 长期行为分析
- **技术架构**: BiGRU 序列模型 + 自编码器，ONNX Runtime 高性能推理
- **闭环流程**: `threat-alerts` → `ml-detection` → `ml-threat-detections` → DB

### 🖥️ Web 管理仪表板
- **React SPA**: 包含 Dashboard (概览), Analytics (分析), Alerts (告警), Customers (客户), ML Detection (机器学习), Threat Intel (情报), Logs (日志), Settings (设置), Login (登录) 等 9 大核心页面
- **系统配置**: 支持 TIRE 插件管理、LLM 配置、RBAC 权限分配

### 🌐 多区域支持
- **高可用部署**: 支持 K8s Overlays 环境定制
- **数据同步**: 集成 MirrorMaker2 与 PostgreSQL 复制配置，支持跨地域部署

## 🧮 威胁评分算法

```
threatScore = (attackCount × uniqueIps × uniquePorts) × timeWeight × ipWeight × portWeight × deviceWeight × netWeight × intelWeight × mlWeight
```

| 因子 | 说明 | 范围 |
|------|------|------|
| `attackCount` | 攻击尝试频率 | — |
| `uniqueIps` | 访问的诱饵IP数量 (横向移动范围) | — |
| `uniquePorts` | 尝试的端口种类 (攻击意图多样性) | — |
| `timeWeight` | 时间衰减因子 (深夜1.2, 工作时间1.0) | 0.8–1.2 |
| `ipWeight` | IP多样性放大 | 1.0–2.0 |
| `portWeight` | 端口多样性权重 | 1.0–2.0 |
| `deviceWeight` | 多设备覆盖奖励 | 1.0–1.5 |
| `netWeight` | 网段权重 (按客户+CIDR配置) | 0.01–10.0 |
| `intelWeight` | 威胁情报权重 (IOC置信度+严重性) | 1.0–4.5 |
| `mlWeight` | ML异常检测建议性倍率 (自编码器异常分数→乘数，特征开关控制) | 0.5–3.0 |

**威胁等级**: INFO (<10) · LOW (10–50) · MEDIUM (50–100) · HIGH (100–200) · CRITICAL (>200)

## 📁 项目结构

```
threat-detection-system/
├── docker/                 # Docker开发环境
│   ├── docker-compose.yml  # 服务编排配置 (含EMQX MQTT Broker)
│   ├── *.sql              # 数据库初始化脚本 (01-23)
│   └── README.md
├── k8s/                   # Kubernetes部署配置
│   ├── base/              # 基础配置
│   └── overlays/          # 环境覆盖 (development, production)
├── services/              # 微服务源码
│   ├── data-ingestion/    # ✅ 数据摄取服务 (V1 syslog + V2 MQTT/JSON)
│   ├── stream-processing/ # ✅ Flink流处理服务 (增强威胁评分、网段权重)
│   ├── threat-assessment/ # ✅ 威胁评估服务 (风险评估、趋势分析)
│   ├── alert-management/  # ✅ 告警管理服务 (多通道通知、智能去重)
│   ├── customer-management/ # ✅ 客户管理服务 (CRUD、设备绑定、网段权重)
│   ├── threat-intelligence/ # ✅ 威胁情报服务 (IOC管理、IP信誉、情报聚合)
│   ├── api-gateway/       # ✅ API网关 (路由、熔断、测试、K8s)
│   ├── config-server/     # ✅ 配置服务器 (native backend, 含单元测试)
│   └── ml-detection/      # ✅ ML检测服务 (PyTorch自编码器、ONNX Runtime、FastAPI)
├── scripts/               # 工具脚本
│   ├── test/              # 测试脚本
│   ├── tools/             # 生产工具 (full_restart.sh, bulk_ingest_logs.py)
│   └── utils/             # 辅助工具
├── docs/                  # 文档
│   ├── api/               # REST API文档
│   ├── design/            # 架构设计文档
│   ├── fixes/             # 问题修复记录
│   └── progress/          # 进度报告
└── infrastructure/        # 基础设施配置
```

## 配置

### 环境变量

| 变量 | 描述 | 默认值 |
|------|------|--------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka代理地址 | `localhost:9092` |
| `SPRING_PROFILES_ACTIVE` | Spring激活配置文件 | `development` |
| `FLINK_JOBMANAGER_RPC_ADDRESS` | Flink JobManager地址 | `stream-processing` |
| `SMS_PROVIDER` | SMS服务提供商 | `twilio` |
| `SLACK_WEBHOOK_URL` | Slack webhook URL | — |
| `TEAMS_WEBHOOK_URL` | Microsoft Teams webhook URL | — |
| `ENCRYPT_KEY` | 配置服务器加密密钥 | — |
| `GIT_CONFIG_URI` | 配置Git仓库URI | — |
| `MQTT_ENABLED` | 启用V2哨兵MQTT摄取 | `false` |
| `MQTT_BROKER_URL` | EMQX MQTT Broker地址 | `tcp://emqx:1883` |
| `NET_WEIGHT_SERVICE_URL` | 网段权重服务地址 | `http://customer-management:8084` |
| `THREAT_INTEL_SERVICE_URL` | 威胁情报服务地址 | `http://threat-intelligence:8085` |
| **⭐ `TIER1_WINDOW_SECONDS`** | Tier 1 时间窗口 — 勒索软件检测 | `30` (推荐 10–300) |
| **⭐ `TIER2_WINDOW_SECONDS`** | Tier 2 时间窗口 — 主要威胁检测 | `300` (推荐 60–1800) |
| **⭐ `TIER3_WINDOW_SECONDS`** | Tier 3 时间窗口 — APT检测 | `900` (推荐 300–7200) |

> 📖 时间窗口配置详见 [TIME_WINDOW_CONFIGURATION.md](docs/guides/TIME_WINDOW_CONFIGURATION.md)

## 📚 API文档

服务运行后可访问：

| 服务 | 地址 |
|------|------|
| 数据摄取 API | `http://localhost:8080/swagger-ui.html` |
| 告警管理 API | `http://localhost:8082/swagger-ui.html` |
| 威胁评估 API | `http://localhost:8083/swagger-ui.html` |
| 客户管理 API | `http://localhost:8084/swagger-ui.html` |
| 威胁情报 API | `http://localhost:8085/swagger-ui.html` |
| ML检测 API | `http://localhost:8086/docs` |
| Flink Web UI | `http://localhost:8081` |
| API 网关 | `http://localhost:8888` |

## 📚 文档中心

### 🎯 核心文档 (按使用频率排序)
- **[📚 文档索引](docs/DOCUMENTATION_INDEX.md)** ⭐ 导航起点
- **[下一步研发工作计划](docs/NEXT_DEVELOPMENT_PLAN.md)** ⭐ 最新
- **[调试与测试指南](docs/debugging_and_testing_guide.md)** ⭐ 必读
- **[快速参考卡片](docs/QUICK_REFERENCE.md)**
- **[使用指南](USAGE_GUIDE.md)**
- **[开发规范](.github/copilot-instructions.md)**

### 快速工具脚本
```bash
./scripts/tools/full_restart.sh          # 完整重启 (代码修改后必做)
./scripts/tools/check_persistence.sh     # 检查数据持久化状态
./scripts/tools/tail_logs.sh             # 实时查看多服务日志
python3 scripts/test/e2e_mvp_test.py     # 运行E2E测试
```

## 🛠️ 开发

### 环境要求

- Ubuntu 24.04.3 LTS (或WSL2) · OpenJDK 21 LTS · Maven 3.8.7
- Docker Engine 20.10+ · kubectl 1.25+ (K8s部署)

### 构建与测试

```bash
mvn clean install                    # 构建所有服务
mvn test                             # 运行所有测试
mvn verify                           # 运行集成测试
mvn test jacoco:report               # 覆盖率报告
```

## Troubleshooting

### ⚠️ 重要提醒

**代码修改后必须重新构建Docker镜像!** 容器不会自动加载新代码。

```bash
# 推荐使用自动化脚本
./scripts/tools/full_restart.sh

# 或手动执行
mvn clean package -DskipTests && \
cd docker && docker compose build --no-cache && docker compose up -d
```

### 常见问题

详细排查请参考 **[调试与测试指南](docs/debugging_and_testing_guide.md)**

| 问题 | 解决方案 |
|------|---------|
| Kafka消费者未启动 | 检查配置文件优先级 (application.properties vs application-docker.yml) |
| 数据未持久化 | 验证JSONB字段注解 (`@JdbcTypeCode(SqlTypes.JSON)`) |
| 容器行为未改变 | 使用 `--no-cache` 重新构建镜像 |
| E2E测试失败 | 确认数据库表名 (threat_alerts vs threat_assessments) |
| 端口冲突 | 检查端口 8080–8084, 8888 是否被占用 |
| Connection Reset | `python3 scripts/tools/bulk_ingest_logs.py --count 5 --workers 4 --batch-size 25` |

### Getting Help

- **📖 查看文档**: [调试与测试指南](docs/debugging_and_testing_guide.md)
- **📝 查看日志**: `docker-compose logs -f <service-name>`
- **🔍 检查状态**: `./scripts/tools/check_persistence.sh`
- **💬 GitHub Issues**: 报告新问题或查看已知问题

## Contributing

1. Fork → 2. Feature branch → 3. Changes + Tests → 4. Pull request

### Development Workflow

1. **Local**: Docker Compose → 2. **Test**: Unit + Integration → 3. **Review**: PR → 4. **Deploy**: K8s

## Roadmap

- [x] 数据摄取与实时流处理
- [x] 多维度威胁评分算法
- [x] 多通道告警通知系统
- [x] 数据库持久化层
- [x] 客户管理与多租户隔离
- [x] API Gateway完善 (JWT RBAC + K8s清单)
- [x] Config Server实现 (native backend, Docker, K8s)
- [x] V2哨兵数据支持 (MQTT + JSON，EMQX Broker)
- [x] 网段权重配置系统 (CRUD API + 评分集成)
- [x] 网络拓扑与心跳持久化 (设备清单、拓扑快照、主机发现)
- [x] 高级威胁情报集成 (TIRE 11款插件)
- [x] 机器学习威胁检测 (PyTorch自编码器 + BiGRU + ONNX Runtime)
- [x] Web管理仪表板 (React SPA 9大页面)
- [x] 多区域部署支持 (K8s Overlays + MirrorMaker2)
- [x] RBAC 权限体系与多租户层级
- [x] LLM 智能验证与 TIRE 插件管理系统

## License

This project is licensed under the MIT License - see the LICENSE file for details.

---

*最后更新: 2026-03-30*
*系统版本: v3.0*
*部署状态: 全部核心服务 (11/11) 完成并可部署，18+ Pods 运行于 K3s 单节点，支持多区域部署*
