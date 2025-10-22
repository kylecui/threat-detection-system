````markdown
# ⚡ 快速参考 - 开发者必备速查表

**最后更新**: 2025-10-20  
**用途**: 快速查找常用命令、文件路径、API端点、常见问题  
**更新频率**: 每周一更新

---

## 🚀 5分钟快速启动

### 启动开发环境
```bash
cd /home/kylecui/threat-detection-system/docker
docker-compose up -d
# 等待60秒服务启动完成

# 验证所有服务
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

### 运行测试
```bash
cd /home/kylecui/threat-detection-system/scripts
bash integration_test_responsibility_separation.sh
# 预期: 16/16测试通过
```

### 查看日志
```bash
# 查看所有服务日志
cd docker && docker-compose logs -f

# 查看特定服务
docker logs -f customer-management-service
docker logs -f alert-management-service
docker logs -f data-ingestion-service
```

---

## 🎯 最常用命令

### Docker操作
```bash
# 启动所有服务
cd docker && docker-compose up -d

# 停止所有服务
docker-compose down

# 重启特定服务
docker restart customer-management-service

# 查看容器状态
docker ps
docker ps -a

# 进入容器
docker exec -it postgres psql -U threat_user -d threat_detection
docker exec -it customer-management-service bash

# 查看日志
docker logs -f customer-management-service --tail 50

# 删除镜像并重新构建
docker-compose build --no-cache && docker-compose up -d
```

### 数据库操作
```bash
# 连接数据库
docker exec -it postgres psql -U threat_user -d threat_detection

# SQL常用命令
\dt                    # 查看所有表
\d customers           # 查看表结构
SELECT * FROM customers LIMIT 5;  # 查询数据
DELETE FROM customers WHERE customer_id LIKE 'test%';  # 删除测试数据
```

### 测试相关
```bash
# 运行集成测试
cd scripts && bash integration_test_responsibility_separation.sh

# 运行单元测试
cd services/customer-management && mvn test

# 清理测试数据
docker exec postgres psql -U threat_user -d threat_detection -c \
  "DELETE FROM device_customer_mapping WHERE dev_serial LIKE '%test%';
   DELETE FROM customer_notification_configs WHERE customer_id LIKE '%test%';
   DELETE FROM customers WHERE customer_id LIKE '%test%';"
```

### Maven操作
```bash
# 编译所有服务
mvn clean compile

# 打包所有服务
mvn clean package -DskipTests

# 编译特定服务
cd services/customer-management && mvn clean package

# 运行特定服务
java -jar services/customer-management/target/customer-management-*.jar
```

---

## 📂 关键文件路径速查

### 配置文件
```
根目录: /home/kylecui/threat-detection-system/

Docker相关:
  docker-compose.yml          # 服务编排配置
  docker/01-init-db.sql       # 数据库初始化脚本
  docker/02-attack-events-storage.sql
  docker/03-port-weights.sql
  docker/04-alert-management-tables.sql
  docker/05-notification-config-tables.sql

K8s相关:
  k8s/base/                   # 基础配置
  k8s/overlays/development/   # 开发环境配置
  k8s/overlays/production/    # 生产环境配置

文档相关:
  DEVELOPMENT_CHEATSHEET.md                    # ⭐ 开发必读
  CODE_AUDIT_REPORT.md                         # 代码审查
  NORMALIZATION_REPORT.md                      # 规范化报告
  PROJECT_UNDERSTANDING_SUMMARY_2025-10-20.md  # 项目理解
  COMPREHENSIVE_DEVELOPMENT_TEST_PLAN_2025-10-20.md  # 开发计划
  README.md                                    # 项目概述
  USAGE_GUIDE.md                               # 使用指南
```

### 服务源码路径
```
services/
  ├── customer-management/
  │   ├── src/main/java/.../customer/dto/
  │   ├── src/main/resources/application.yml
  │   └── src/test/
  │
  ├── alert-management/
  │   ├── src/main/java/.../alert/
  │   └── src/main/resources/application.yml
  │
  ├── data-ingestion/
  ├── stream-processing/
  ├── threat-assessment/
  ├── api-gateway/
  └── config-server/
```

### 前端路径
```
frontend/
  ├── src/
  │   ├── pages/              # 页面组件
  │   ├── components/         # 通用组件
  │   ├── services/           # API服务层
  │   └── types/              # TypeScript类型
  ├── public/                 # 静态资源
  ├── Dockerfile              # Docker构建
  ├── nginx.conf              # Nginx配置
  └── vite.config.ts          # Vite配置
```

### 脚本路径
```
scripts/
  ├── test/
  │   ├── e2e_mvp_test.py
  │   ├── unit_test_mvp.py
  │   ├── integration_test_*.sh
  │   └── test_*.sh
  │
  ├── tools/
  │   ├── full_restart.sh
  │   ├── bulk_ingest_logs.py
  │   └── check_persistence.sh
  │
  └── README.md
```

---

## 🌐 API端点速查

### 基础信息
| 服务 | 端口 | 基础URL | 健康检查 |
|------|------|---------|---------|
| Customer-Management | 8084 | `http://localhost:8084` | `/actuator/health` |
| Alert-Management | 8082 | `http://localhost:8082` | `/actuator/health` |
| Data-Ingestion | 8080 | `http://localhost:8080` | `/api/v1/logs/health` |
| Threat-Assessment | 8083 | `http://localhost:8083` | `/api/v1/assessment/health` |
| API-Gateway | 8888 | `http://localhost:8888` | `/actuator/health` |
| Kafka | 9092 | `localhost:9092` | - |
| PostgreSQL | 5432 | `localhost:5432` | - |

### 常用API (Customer-Management, Port 8084)

```bash
# 创建客户
POST /api/v1/customers
{
  "customer_id": "test-001",
  "name": "Test Company",
  "email": "test@example.com",
  "subscription_tier": "PROFESSIONAL",
  "max_devices": 100
}

# 查询客户
GET /api/v1/customers/{customerId}

# 列出所有客户
GET /api/v1/customers

# 更新客户
PATCH /api/v1/customers/{customerId}
{
  "name": "Updated Name",
  "email": "new@example.com"
}

# 删除客户
DELETE /api/v1/customers/{customerId}
```

### 常用API (Alert-Management, Port 8082)

```bash
# 查询所有告警
GET /api/v1/alerts?page=0&size=10

# 查询特定告警
GET /api/v1/alerts/{id}

# 解决告警
POST /api/v1/alerts/{id}/resolve
{
  "resolution": "已处理",
  "resolvedBy": "admin"
}

# 升级告警
POST /api/v1/alerts/{id}/escalate
{
  "escalationLevel": 2,
  "reason": "需要紧急处理"
}
```

### 常用API (Data-Ingestion, Port 8080)

```bash
# 提交日志
POST /api/v1/logs/ingest
Content-Type: text/plain

syslog format log data here...

# 批量提交日志
POST /api/v1/logs/batch-ingest
Content-Type: text/plain

multiple log lines
separated by newlines

# 查询摄取统计
GET /api/v1/logs/stats

# 查询健康状态
GET /api/v1/logs/health
```

---

## 🔧 常见问题速查

### 问题1: 容器无法启动
**症状**: `docker-compose up` 失败  
**解决**:
```bash
# 查看详细错误
docker-compose logs customer-management

# 查看文件权限
ls -la docker/

# 重新构建镜像
docker-compose build --no-cache

# 删除旧数据卷重新启动
docker-compose down -v
docker-compose up -d
```

### 问题2: 数据库连接失败
**症状**: `Connection refused: postgres:5432`  
**解决**:
```bash
# 检查PostgreSQL容器状态
docker ps | grep postgres

# 查看PostgreSQL日志
docker logs postgres

# 测试连接
docker exec postgres psql -U threat_user -d threat_detection -c "SELECT 1"

# 等待PostgreSQL完全启动
sleep 30 && docker-compose up -d
```

### 问题3: API返回404
**症状**: 请求返回 `{"error": "Not Found", "status": 404}`  
**解决**:
```bash
# 检查服务是否启动
curl http://localhost:8084/actuator/health

# 检查路径是否正确
# ✅ 正确: GET /api/v1/customers
# ❌ 错误: GET /customers

# 查看Controller日志
docker logs customer-management-service | grep "mapping"
```

### 问题4: PATCH请求返回200但数据未更新
**症状**: 
- API返回HTTP 200 OK
- 日志显示"Successfully saved"
- 但数据库没有变化  

**解决**:
```bash
# 检查请求体字段使用snake_case
# ✅ 正确: {"email_enabled": false}
# ❌ 错误: {"emailEnabled": false}

# 验证DTO有@JsonProperty注解
grep "@JsonProperty" services/customer-management/src/main/java/.../dto/*.java

# 检查Jackson配置
find services/customer-management -name "JacksonConfig.java"

# 重新构建容器（可能是旧代码）
docker-compose build --no-cache customer-management
docker-compose up -d customer-management
```

### 问题5: Kafka消费者不启动
**症状**: Stream Processing没有消费Kafka消息  
**解决**:
```bash
# 检查Kafka broker是否运行
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# 检查Topic是否存在
docker exec kafka kafka-topics --describe --bootstrap-server localhost:9092 --topic attack-events

# 查看消费者日志
docker logs stream-processing

# 检查配置文件优先级
# Spring Boot配置加载顺序:
# 1. application.properties (最低优先级)
# 2. application-{profile}.yml
# 3. 环境变量 (最高优先级)
```

### 问题6: 测试脚本使用错误的字段名称
**症状**: 测试失败，JSON反序列化错误  
**解决**:
```bash
# ✅ 正确的字段命名 (snake_case):
curl -X PATCH "http://localhost:8084/api/v1/customers/test-001/notification-config" \
  -H "Content-Type: application/json" \
  -d '{
    "email_enabled": true,
    "min_severity_level": "HIGH"
  }'

# ❌ 错误的字段命名 (camelCase):
# curl ... -d '{"emailEnabled": true, "minSeverityLevel": "HIGH"}'

# 修复测试脚本
cd scripts
grep -l "emailEnabled\|customerId" *.sh | while read f; do
  sed -i 's/emailEnabled/email_enabled/g' "$f"
  sed -i 's/customerId/customer_id/g' "$f"
  sed -i 's/devSerial/dev_serial/g' "$f"
done
```

---

## 📊 系统检查清单

### 启动前检查
```bash
# ✅ 检查项
[ ] Docker已安装: docker --version
[ ] Docker Compose已安装: docker-compose --version
[ ] Java已安装: java -version
[ ] Maven已安装: mvn -version
[ ] 磁盘空间充足 (>5GB): df -h
[ ] 端口未被占用: lsof -i :5432,8080,8082,8083,8084,9092
```

### 运行后检查
```bash
# ✅ 服务状态检查
[ ] PostgreSQL健康: docker exec postgres pg_isready
[ ] Kafka健康: docker exec kafka kafka-topics --list --bootstrap-server localhost:9092
[ ] Customer-Management健康: curl http://localhost:8084/actuator/health
[ ] Alert-Management健康: curl http://localhost:8082/actuator/health
[ ] 数据库表已创建: docker exec postgres psql -U threat_user -d threat_detection -c "\dt"
```

### 开发前检查
```bash
# ✅ 代码检查
[ ] 查看了DEVELOPMENT_CHEATSHEET.md
[ ] 了解命名规范 (snake_case/camelCase/kebab-case)
[ ] 确认JSON字段使用@JsonProperty注解
[ ] 查看了相关API文档
[ ] 清理了测试数据
```

### 提交前检查
```bash
# ✅ 质量检查
[ ] 代码已编译: mvn clean compile
[ ] 单元测试通过: mvn test
[ ] 集成测试通过: bash scripts/integration_test_responsibility_separation.sh
[ ] 无ERROR/WARN日志: docker logs [service] | grep -E "ERROR|WARN"
[ ] API文档已更新
[ ] 数据库migration已编写
```

---

## 🎓 快速学习资源

### 必读文档
1. **DEVELOPMENT_CHEATSHEET.md** - 最重要! (9章节)
   - 命名规范
   - 容器清单
   - API清单
   - 故障排查

2. **PROJECT_UNDERSTANDING_SUMMARY_2025-10-20.md** - 项目整体理解
   - 蜜罐机制
   - 架构设计
   - 现状分析

3. **CODE_AUDIT_REPORT.md** - 代码质量
   - P0问题 (已修复)
   - 最佳实践

### 参考命令速记

```bash
# 最常用的5条命令
1. docker-compose up -d           # 启动
2. docker logs -f [service]       # 看日志
3. docker exec postgres psql ...  # 查数据
4. bash integration_test...       # 跑测试
5. mvn clean package -DskipTests  # 打包

# 最常见的3个问题
1. 端口占用? lsof -i :8084
2. 数据库连不上? docker exec postgres pg_isready
3. 消息队列坏了? docker logs kafka
```

---

## 📞 获取帮助

### 问题排查流程
1. **查看日志**: `docker logs -f [service-name]`
2. **查CHEATSHEET**: DEVELOPMENT_CHEATSHEET.md 第6章
3. **查看错误**: 在ERROR/WARN日志中搜索关键字
4. **检查配置**: 确认环境变量、配置文件正确
5. **搜索文档**: 在相关API文档中查找

### 常用搜索关键字
- 错误日志关键字: "ERROR", "Exception", "Failed"
- 连接问题: "refused", "Connection reset", "timeout"
- 配置问题: "property not found", "invalid config"
- 数据问题: "constraint violation", "unique key"

---

**最后更新**: 2025-10-20  
**下次更新**: 2025-10-27  
**维护者**: 开发团队

````
