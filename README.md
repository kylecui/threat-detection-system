# 🚀 威胁检测系统 (Threat Detection System)

一个现代化的、可扩展的威胁检测系统，基于云原生技术构建。

## 🏗️ 系统架构

本系统由以下微服务组成：

- **✅ 数据摄取服务 (Data Ingestion Service)**: 接收rsyslog/logstash日志并发布到Kafka
- **✅ 流处理服务 (Stream Processing Service)**: 使用Apache Flink进行实时威胁检测
- **✅ 威胁评估服务 (Threat Assessment Service)**: 处理威胁评分和风险评估
- **🟡 告警管理服务 (Alert Management Service)**: 管理告警和通知 (计划中)
- **🟡 API网关 (API Gateway)**: 集中式API管理和路由 (计划中)
- **🟡 配置服务器 (Config Server)**: 集中式配置管理 (计划中)

## 🛠️ 技术栈

- **后端**: Spring Boot 3.1.5 (OpenJDK 21 LTS)
- **事件流**: Apache Kafka 3.4+
- **流处理**: Apache Flink 1.17+
- **容器化**: Docker + Docker Compose
- **编排**: Kubernetes + Kustomize
- **构建工具**: Maven 3.8.7
- **数据库**: PostgreSQL 15
- **缓存**: Redis (可选)
- **开发环境**: Ubuntu 24.04.3 LTS (WSL2)

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

# 检查服务健康状态
curl http://localhost:8080/actuator/health  # 数据摄取服务
curl http://localhost:8081/overview         # Flink Web UI
curl http://localhost:8083/api/v1/assessment/health  # 威胁评估服务

# 查看日志
docker-compose logs -f
```

### 生产环境 (Kubernetes)

```bash
# 部署到开发环境
kubectl apply -k k8s/overlays/development

# 部署到生产环境
kubectl apply -k k8s/overlays/production

# 检查部署状态
kubectl get pods -n threat-detection-dev
```

## 📊 数据流

1. **日志摄取**: 日志通过rsyslog:9080 → 数据摄取服务
2. **事件发布**: 结构化事件发布到Kafka主题
3. **实时处理**: Apache Flink实时处理威胁评分
4. **威胁评估**: 威胁评估服务评估风险等级
5. **告警生成**: 告警管理服务处理通知 (计划中)
6. **API访问**: API网关提供统一访问所有服务 (计划中)

## ✨ 核心特性

- **✅ 实时威胁检测**: 持续监控，亚秒级延迟
- **✅ 可扩展架构**: 微服务支持水平扩展
- **✅ 事件驱动处理**: 基于Kafka的事件流
- **✅ 多环境支持**: 开发和生产环境配置
- **✅ 健康监控**: 全面的健康检查和指标
- **✅ 离线数据导入**: 历史数据处理和重放功能
- **✅ 高可靠性批量摄取**: 连接池、重试逻辑和自动恢复
- **✅ 增强威胁评分**: 多维度算法，包含端口多样性和设备覆盖奖励
- **✅ 数据库持久化**: PostgreSQL存储威胁评估结果
- **✅ 端到端集成**: 完整的数据管道验证

## 🧮 威胁评分算法

系统使用复杂的威胁评分算法，具有增强的多维度分析：

```
threatScore = (attackCount × uniqueIps × uniquePorts) × timeWeight × ipWeight × portWeight × deviceWeight
```

其中：
- `portWeight`: 基于端口多样性的风险权重 (1.0-2.0)
- `deviceWeight`: 多设备覆盖奖励 (1.0-1.5)
- `timeWeight`: 时间衰减因子
- `ipWeight`: IP多样性放大
- `attackCount`: 攻击尝试频率
- `uniqueIps`: 不同源IP数量
- `uniquePorts`: 不同目标端口数量

**最新增强功能：**
- 端口多样性分析，检测复杂攻击模式
- 多设备覆盖奖励，分布式攻击检测
- 可配置时间窗口 (默认: 30秒聚合，2分钟评分)
- 复杂威胁模式的准确性提升

## 📁 项目结构

```
threat-detection-system/
├── docker/                 # Docker开发环境
│   ├── docker-compose.yml  # 服务编排配置
│   ├── init-db.sql        # 数据库初始化脚本
│   └── README.md          # Docker设置指南
├── k8s/                   # Kubernetes部署配置
│   ├── base/              # 基础配置
│   ├── overlays/          # 环境特定覆盖
│   │   ├── development/   # 开发环境
│   │   └── production/    # 生产环境
│   └── README.md          # K8s部署指南
├── services/              # 微服务源码
│   ├── data-ingestion/    # ✅ 数据摄取服务
│   │   └── data-ingestion-service/
│   ├── stream-processing/ # ✅ Flink流处理服务
│   ├── threat-assessment/ # ✅ 威胁评估服务
│   ├── alert-management/  # 🟡 告警管理服务 (计划中)
│   ├── api-gateway/       # 🟡 API网关服务 (计划中)
│   └── config-server/     # 🟡 配置服务器 (计划中)
├── infrastructure/        # 基础设施配置
├── scripts/               # 有组织的工具脚本
│   ├── test/              # 测试脚本和工具
│   ├── tools/             # 生产就绪工具脚本
│   ├── utils/             # 辅助工具和开发工具
│   ├── init-kafka.sh      # Kafka初始化脚本
│   ├── Dockerfile         # 脚本的Docker镜像
│   └── README.md          # 脚本使用指南
├── tmp/                   # 临时文件目录
│   ├── error_logs/        # 错误日志
│   ├── real_test_logs/    # 真实测试日志
│   └── test_logs/         # 测试日志文件
└── docs/                  # 文档
    ├── cloud_native_architecture.md
    ├── complete_development_plan_2025.md
    ├── log_format_analysis.md
    ├── next_action_plan_2025.md
    └── optimization_summary.md
```

## 🛠️ 开发环境

### 环境要求

- **操作系统**: Ubuntu 24.04.3 LTS (或Windows上的WSL2)
- **Java**: OpenJDK 21 LTS
- **构建工具**: Maven 3.8.7
- **容器运行时**: Docker Desktop 4.0+ 或 Docker Engine 20.10+
- **Kubernetes**: kubectl 1.25+ (用于k8s部署)

### 构建服务

```bash
# 构建所有服务
mvn clean install

# 构建特定服务
cd services/data-ingestion
mvn clean package

# 构建威胁评估服务
cd services/threat-assessment
mvn clean compile
```

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行集成测试
mvn verify

# 带覆盖率的测试
mvn test jacoco:report
```

## 🚀 部署选项

### 选项1: Docker开发环境

适合本地开发和测试。详见 `docker/README.md`。

**优点**: 启动快速，易于调试，支持热重载
**缺点**: 单节点，资源限制

### 选项2: Kubernetes生产环境

适合生产部署，具有高可用性。详见 `k8s/README.md`。

**优点**: 可扩展，多节点，生产就绪
**缺点**: 资源需求高，设置复杂

## 📚 API文档

服务运行后可访问：

- **数据摄取API**: `http://localhost:8080/swagger-ui.html`
- **威胁评估API**: `http://localhost:8083/swagger-ui.html`
- **Flink Web UI**: `http://localhost:8081`

## 📊 监控

### 健康检查

```bash
# 服务健康状态
curl http://localhost:8080/actuator/health  # 数据摄取服务
curl http://localhost:8081/overview         # Flink作业状态
curl http://localhost:8083/api/v1/assessment/health  # 威胁评估服务

# 指标数据
curl http://localhost:8080/actuator/metrics
curl http://localhost:8083/actuator/metrics

# Flink作业状态
curl http://localhost:8081/jobs/overview
```

### 日志查看

```bash
# Docker日志
docker-compose logs -f data-ingestion
docker-compose logs -f threat-assessment

# Kubernetes日志
kubectl logs -f deployment/data-ingestion -n threat-detection-dev
kubectl logs -f deployment/threat-assessment -n threat-detection-dev
```

### 数据库查询

```bash
# 连接到PostgreSQL
docker-compose exec postgres psql -U threat_user -d threat_detection

# 查看威胁评估结果
SELECT * FROM threat_assessments ORDER BY risk_score DESC LIMIT 10;

# 查看威胁告警统计
SELECT COUNT(*) as total_alerts FROM threat_alerts;
```

## 配置

### 环境变量

| 变量 | 描述 | 默认值 |
|------|------|--------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka代理地址 | `localhost:9092` |
| `SPRING_PROFILES_ACTIVE` | Spring激活配置文件 | `development` |
| `FLINK_JOBMANAGER_RPC_ADDRESS` | Flink JobManager地址 | `stream-processing` |

### 配置文件

- `docker-compose.yml`: Docker服务定义
- `k8s/base/`: Kubernetes基础配置
- `k8s/overlays/`: 环境特定覆盖配置

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

### Development Workflow

1. **Local Development**: Use Docker Compose for fast iteration
2. **Testing**: Run unit and integration tests
3. **Code Review**: Submit PR with detailed description
4. **CI/CD**: Automated testing and deployment
5. **Production**: Deploy via Kubernetes manifests

## Troubleshooting

### Common Issues

1. **Port Conflicts**: Check if ports 8080-8082 are available
2. **Memory Issues**: Ensure Docker has at least 4GB RAM allocated
3. **Kafka Connection**: Verify Zookeeper is running before Kafka
4. **Flink Jobs**: Check JobManager logs for submission errors
5. **Connection Reset Errors**: Use the enhanced bulk ingestion script with built-in connection pooling:
   ```bash
   python3 scripts/tools/bulk_ingest_logs.py --count 5 --workers 4 --batch-size 25
   ```
6. **Bulk Ingestion Failures**: Monitor connection pool refresh logs and ensure batch sizes are optimized

### Getting Help

- Check service logs: `docker-compose logs <service-name>`
- Review documentation in `docs/` directory
- Check GitHub Issues for known problems

## Roadmap

- [ ] Complete Threat Assessment Service implementation
- [ ] Add Alert Management Service
- [ ] Implement API Gateway with authentication
- [ ] Add comprehensive monitoring and alerting
- [ ] Implement data persistence layer
- [ ] Add machine learning-based threat detection
- [ ] Create web-based management dashboard

## License

This project is licensed under the MIT License - see the LICENSE file for details.