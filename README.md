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
   - 构建部署 → `docs/build/`

📄 **快速导航**: [DOCS_NAVIGATION.md](./DOCS_NAVIGATION.md)

---

## 🏗️ 系统架构

本系统由以下微服务组成：

| 服务 | 状态 | 端口 | 说明 |
|------|------|------|------|
| **数据摄取服务** (Data Ingestion) | ✅ 完成 | 8080 | 接收rsyslog/logstash日志并发布到Kafka，支持批量处理 |
| **流处理服务** (Stream Processing) | ✅ 完成 | 8081 (Flink UI) | Apache Flink实时威胁检测，多维度评分算法 |
| **告警管理服务** (Alert Management) | ✅ 完成 | 8082 | 多通道通知 (Email/SMS/Webhook/Slack/Teams)，智能去重和升级 |
| **威胁评估服务** (Threat Assessment) | ✅ 完成 | 8083 | 威胁评分和风险评估，历史趋势分析 |
| **客户管理服务** (Customer Management) | ✅ 完成 | 8084 | 客户CRUD、设备绑定、通知配置 |
| **API网关** (API Gateway) | ✅ 完成 | 8888 | 统一入口，路由管理、熔断降级，含单元测试和K8s清单 |
| **配置服务器** (Config Server) | 🟡 部分实现 | 8899 | Spring Cloud Config Server (native backend)，含Dockerfile和K8s清单，缺少单元测试 |

## 🛠️ 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 后端 | Spring Boot | 3.1.5 (OpenJDK 21 LTS) |
| 事件流 | Apache Kafka | 3.4+ |
| 流处理 | Apache Flink | 1.17+ |
| 容器化 | Docker + Docker Compose | — |
| 编排 | Kubernetes + Kustomize | — |
| 构建工具 | Maven | 3.8.7 |
| 数据库 | PostgreSQL | 15 |
| 缓存 | Redis | 可选 |

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
设备syslog → rsyslog:9080 → Data Ingestion → Kafka (attack-events)
                                                  ↓
                                           Flink Stream Processing
                                                  ↓
                                           Kafka (threat-alerts)
                                                  ↓
                               Threat Assessment ← → PostgreSQL
                                                  ↓
                                           Alert Management → Email/SMS/Slack/Webhook/Teams
```

1. **日志摄取**: rsyslog:9080 → 数据摄取服务，支持单条和批量处理
2. **事件发布**: 结构化事件发布到Kafka主题 (`attack-events`, `status-events`)
3. **实时处理**: Apache Flink多维度威胁评分 (30s/5min/15min 三级时间窗口)
4. **威胁评估**: 风险等级评估 + 历史趋势分析 → PostgreSQL持久化
5. **告警通知**: 多通道通知 + 智能去重 + 升级策略

## 🧮 威胁评分算法

```
threatScore = (attackCount × uniqueIps × uniquePorts) × timeWeight × ipWeight × portWeight × deviceWeight
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

**威胁等级**: INFO (<10) · LOW (10–50) · MEDIUM (50–100) · HIGH (100–200) · CRITICAL (>200)

## 📁 项目结构

```
threat-detection-system/
├── docker/                 # Docker开发环境
│   ├── docker-compose.yml  # 服务编排配置
│   ├── *.sql              # 数据库初始化脚本 (01-17)
│   └── README.md
├── k8s/                   # Kubernetes部署配置
│   ├── base/              # 基础配置
│   └── overlays/          # 环境覆盖 (development, production)
├── services/              # 微服务源码
│   ├── data-ingestion/    # ✅ 数据摄取服务 (批量处理、高可靠性)
│   ├── stream-processing/ # ✅ Flink流处理服务 (增强威胁评分)
│   ├── threat-assessment/ # ✅ 威胁评估服务 (风险评估、趋势分析)
│   ├── alert-management/  # ✅ 告警管理服务 (多通道通知、智能去重)
│   ├── customer-management/ # ✅ 客户管理服务 (CRUD、设备绑定)
│   ├── api-gateway/       # ✅ API网关 (路由、熔断、测试、K8s)
│   └── config-server/     # 🟡 配置服务器 (native backend, 缺少测试)
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
- [x] API Gateway完善 (测试 + K8s清单)
- [x] Config Server实现 (native backend, Docker, K8s)
- [ ] V2哨兵数据支持 (MQTT + JSON)
- [ ] 网段权重配置系统
- [ ] 高级威胁情报集成
- [ ] 机器学习威胁检测
- [ ] Web管理仪表板
- [ ] 多区域部署支持

## License

This project is licensed under the MIT License - see the LICENSE file for details.

---

*最后更新: 2026-03-26*
*系统版本: v2.1*
*部署状态: 核心服务 (6/7) 完成并可部署，配置服务器部分实现 (缺少测试)*
