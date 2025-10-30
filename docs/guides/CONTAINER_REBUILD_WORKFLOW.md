# 容器重建标准流程

**版本**: 1.0  
**日期**: 2025-10-24  
**重要性**: ⚠️ **所有代码修改后必须遵循此流程**

---

## 🎯 核心原则

**每次代码修改后，必须完整执行以下三个步骤，缺一不可**

### ⚠️ **特别提醒：ClassNotFoundException 排查要点**

**当出现 `ClassNotFoundException` 时，首先检查 `stream-processing` 和 `taskmanager` 两个容器的 JAR 文件版本！**

**为什么容易被忽略**：
- `stream-processing` 容器包含 JAR 文件的构建逻辑
- `taskmanager` 容器（Flink TaskManager）负责实际执行，但 JAR 文件需要从 `stream-processing` 镜像复制
- 修改代码后只重建 `stream-processing` 容器，`taskmanager` 仍使用旧 JAR 文件

**快速检查命令**：
```bash
# 检查两个关键容器的 JAR 文件时间戳
docker exec stream-processing ls -lh /opt/flink/lib/stream-processing.jar
docker exec taskmanager ls -lh /opt/flink/lib/stream-processing.jar

# 如果时间戳不同，说明需要重建 taskmanager
```

---

## 📋 标准流程

### 第一步：重新编译 Maven 项目

```bash
cd ~/threat-detection-system/services/<service-name>
mvn clean package -DskipTests
```

**示例**（以 threat-assessment 为例）:
```bash
cd ~/threat-detection-system/services/threat-assessment
mvn clean package -DskipTests
```

**说明**:
- `clean`: 清理旧的编译产物
- `package`: 打包生成新的 JAR 文件
- `-DskipTests`: 跳过单元测试（加快编译速度）
- **必须在服务目录执行**，不要在项目根目录

**验证**:
```bash
# 检查 JAR 文件是否生成
ls -lh ~/threat-detection-system/services/threat-assessment/target/*.jar

# 预期输出:
# threat-assessment-service-1.0.0.jar
# threat-assessment-service-1.0.0.jar.original
```

---

### 第二步：重构容器（完整重建）

```bash
cd ~/threat-detection-system/docker
docker compose down -v <service-name>
docker compose build --no-cache <service-name>
docker compose up -d --force-recreate <service-name>
```

**示例**（以 threat-assessment 为例）:
```bash
cd ~/threat-detection-system/docker
docker compose down -v threat-assessment
docker compose build --no-cache threat-assessment
docker compose up -d --force-recreate threat-assessment
```

**命令解释**:

| 命令 | 参数 | 作用 | 为什么必须 |
|------|------|------|-----------|
| `docker compose down -v` | `-v` | 停止容器并删除卷 | 清理旧数据，避免缓存问题 |
| `docker compose build` | `--no-cache` | 从头构建镜像，不使用缓存 | 确保代码更改完全生效 |
| `docker compose up -d` | `--force-recreate` | 强制重新创建容器 | 确保使用新镜像 |
| | `-d` | 后台运行 | 不阻塞终端 |

**⚠️ 常见错误**:
- ❌ 只执行 `docker compose restart` → 不会加载新代码
- ❌ 忘记 `--no-cache` → 可能使用旧的 Docker 层缓存
- ❌ 忘记 `-v` → 旧的数据卷可能干扰测试

---

### 第三步：检查容器状态

```bash
cd ~/threat-detection-system/docker
docker compose ps
```

**预期输出**:
```
NAME                        STATUS          PORTS
threat-assessment-service   Up 10 seconds   0.0.0.0:8083->8083/tcp
kafka                       Up 5 minutes    0.0.0.0:9092->9092/tcp
postgres                    Up 5 minutes    0.0.0.0:5432->5432/tcp
...
```

**验证清单**:
- [ ] 服务状态为 `Up`（不是 `Restarting` 或 `Exited`）
- [ ] 运行时间较短（说明是新创建的容器）
- [ ] 端口映射正确

**如果容器未启动，检查日志**:
```bash
docker logs threat-assessment-service --tail 50
```

---

## 🔄 完整流程示例

### 场景：修改了 ThreatScoreCalculator.java

```bash
# 1. 重新编译
cd ~/threat-detection-system/services/threat-assessment
mvn clean package -DskipTests

# 等待编译完成（通常 10-30 秒）
# 预期输出: [INFO] BUILD SUCCESS

# 2. 重构容器
cd ~/threat-detection-system/docker
docker compose down -v threat-assessment
docker compose build --no-cache threat-assessment
docker compose up -d --force-recreate threat-assessment

# 等待容器启动（通常 5-10 秒）

# 3. 检查容器状态
docker compose ps

# 预期输出: threat-assessment-service   Up X seconds

# 4. 验证服务就绪
docker logs threat-assessment-service --tail 20

# 预期日志:
# Started ThreatAssessmentApplication in X.XXX seconds
```

---

## 🚀 快速脚本（推荐）

为了提高效率，可以使用以下一键脚本：

### 创建脚本
```bash
cat > ~/threat-detection-system/scripts/rebuild_service.sh << 'EOF'
#!/bin/bash

# 容器重建标准脚本
# 用法: ./rebuild_service.sh <service-name>
# 示例: ./rebuild_service.sh threat-assessment

set -e  # 遇到错误立即退出

SERVICE_NAME=$1

if [ -z "$SERVICE_NAME" ]; then
    echo "错误: 请提供服务名称"
    echo "用法: ./rebuild_service.sh <service-name>"
    echo "示例: ./rebuild_service.sh threat-assessment"
    exit 1
fi

echo "==================================================
容器重建流程: $SERVICE_NAME
==================================================
"

# 第一步：重新编译
echo "[1/3] 重新编译 Maven 项目..."
cd ~/threat-detection-system/services/$SERVICE_NAME
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ 编译失败，终止流程"
    exit 1
fi
echo "✅ 编译成功"
echo ""

# 第二步：重构容器
echo "[2/3] 重构 Docker 容器..."
cd ~/threat-detection-system/docker

echo "  → 停止并删除容器..."
docker compose down -v $SERVICE_NAME

echo "  → 重新构建镜像（无缓存）..."
docker compose build --no-cache $SERVICE_NAME

echo "  → 启动新容器..."
docker compose up -d --force-recreate $SERVICE_NAME

if [ $? -ne 0 ]; then
    echo "❌ 容器启动失败，检查日志"
    docker logs ${SERVICE_NAME}-service --tail 50
    exit 1
fi
echo "✅ 容器重构成功"
echo ""

# 第三步：检查容器状态
echo "[3/3] 检查容器状态..."
sleep 3  # 等待容器完全启动
docker compose ps | grep $SERVICE_NAME

echo ""
echo "==================================================
重建完成！
==================================================

下一步:
1. 查看日志: docker logs ${SERVICE_NAME}-service --tail 30
2. 运行测试: cd ~/threat-detection-system/scripts && bash test_xxx.sh
"
EOF

chmod +x ~/threat-detection-system/scripts/rebuild_service.sh
```

### 使用脚本
```bash
# 重建 threat-assessment 服务
cd ~/threat-detection-system/scripts
./rebuild_service.sh threat-assessment

# 重建其他服务
./rebuild_service.sh data-ingestion
./rebuild_service.sh stream-processing
./rebuild_service.sh alert-management
```

---

## 📝 AI 助手工作规范

### 代码修改后的标准回复模板

```markdown
已完成代码修改：
- 文件: services/threat-assessment/src/main/java/com/threatdetection/xxx.java
- 修改: [简要说明]

现在重建容器并测试：
```

### 必须执行的命令序列

```bash
# 1. 编译
cd ~/threat-detection-system/services/threat-assessment && mvn clean package -DskipTests

# 2. 重构容器
cd ~/threat-detection-system/docker && \
docker compose down -v threat-assessment && \
docker compose build --no-cache threat-assessment && \
docker compose up -d --force-recreate threat-assessment

# 3. 检查状态
docker compose ps

# 4. 查看日志
docker logs threat-assessment-service --tail 30

# 5. 运行测试
cd ~/threat-detection-system/scripts && bash test_v4_phase1_integration.sh
```

### ⚠️ 禁止的操作

- ❌ **禁止使用** `docker compose restart` 代替完整重建
- ❌ **禁止跳过** `mvn clean package` 步骤
- ❌ **禁止省略** `--no-cache` 参数
- ❌ **禁止忘记** 验证容器状态

---

## 🔍 故障排查

### 问题1: 代码修改不生效

**症状**: 修改了代码，但容器中运行的还是旧代码

**原因**: 
- 忘记重新编译
- 使用了 Docker 缓存
- 使用了 `docker compose restart` 而非完整重建

**解决方案**:
```bash
# 强制完整重建
cd ~/threat-detection-system/services/threat-assessment
mvn clean package -DskipTests

cd ~/threat-detection-system/docker
docker compose down -v threat-assessment
docker compose build --no-cache threat-assessment
docker compose up -d --force-recreate threat-assessment

# 验证 JAR 文件时间戳
docker exec threat-assessment-service ls -lh /app/*.jar
```

---

### 问题2: 容器启动失败

**症状**: `docker compose ps` 显示 `Exited` 或 `Restarting`

**排查步骤**:
```bash
# 1. 查看容器日志
docker logs threat-assessment-service --tail 100

# 2. 检查 JAR 文件是否存在
docker exec threat-assessment-service ls -lh /app/

# 3. 检查 Java 版本
docker exec threat-assessment-service java -version

# 4. 检查依赖服务（Kafka, PostgreSQL）
docker compose ps
```

**常见错误**:
- `ClassNotFoundException`: JAR 文件不完整 → 重新编译
- `Connection refused`: 依赖服务未启动 → 启动 Kafka/PostgreSQL
- `Port already in use`: 端口冲突 → 检查端口占用

---

### 问题3: 测试失败但容器正常

**症状**: 容器运行正常，但集成测试失败

**排查步骤**:
```bash
# 1. 检查服务健康状态
curl http://localhost:8083/actuator/health

# 2. 检查 Kafka 连接
docker exec threat-assessment-service nc -zv kafka 9092

# 3. 检查数据库连接
docker exec threat-assessment-service nc -zv postgres 5432

# 4. 查看详细日志
docker logs threat-assessment-service -f

# 5. 手动测试 API
curl -X POST http://localhost:8083/api/v1/assessment/evaluate \
  -H "Content-Type: application/json" \
  -d '{"customer_id":"default", ...}'
```

---

### 问题4: ClassNotFoundException - JAR文件版本不一致

**症状**: 出现 `ClassNotFoundException` 或 `NoClassDefFoundError`

**原因**: 
- `stream-processing` 服务代码已修改，但容器中的 JAR 文件未更新
- `taskmanager` 容器（Flink TaskManager）仍在使用旧版本的 JAR 文件
- Docker 缓存导致镜像未重新构建

**⚠️ 特别注意**: `taskmanager` 容器容易被忽略！

**排查步骤**:
```bash
# 1. 检查所有相关容器的 JAR 文件版本
echo "=== 检查 stream-processing 容器 ==="
docker exec stream-processing ls -lh /opt/flink/lib/stream-processing.jar

echo "=== 检查 taskmanager 容器 ==="
docker exec taskmanager ls -lh /opt/flink/lib/stream-processing.jar

echo "=== 检查 jobmanager 容器 ==="
docker exec jobmanager ls -lh /opt/flink/lib/stream-processing.jar

# 2. 比较文件修改时间（应该都是最新的）
# 预期：所有容器的 JAR 文件时间戳应该相同且是最近的

# 3. 如果时间戳不同，强制重建所有 Flink 容器
cd ~/threat-detection-system/docker
docker compose down -v stream-processing taskmanager jobmanager
docker compose build --no-cache stream-processing taskmanager jobmanager
docker compose up -d --force-recreate stream-processing taskmanager jobmanager

# 4. 验证重建结果
docker compose ps | grep -E "(stream-processing|taskmanager|jobmanager)"
```

**Flink 集群重建命令**:
```bash
# 停止所有 Flink 相关服务
docker compose down -v stream-processing taskmanager jobmanager

# 重新构建（注意：stream-processing 镜像包含 JAR 文件）
docker compose build --no-cache stream-processing taskmanager jobmanager

# 重新启动
docker compose up -d --force-recreate stream-processing taskmanager jobmanager

# 等待服务启动
sleep 10

# 检查状态
docker compose ps
```

**验证方法**:
```bash
# 检查 Flink Web UI 是否可访问
curl -s http://localhost:8081/overview | grep -o '"state":"RUNNING"'

# 检查作业状态
curl -s http://localhost:8081/jobs | jq '.jobs[0].state'

# 查看详细日志
docker logs stream-processing --tail 50
docker logs taskmanager --tail 50
```

**常见场景**:
- 修改了 `APTTemporalAccumulator.java` 后出现类找不到错误
- 添加了新的依赖包但容器中没有
- 重构了包结构但旧的类路径仍在缓存中

**预防措施**:
- 每次修改 `stream-processing` 代码后，必须重建所有 Flink 容器
- 不要只重建 `stream-processing` 容器，`taskmanager` 和 `jobmanager` 也需要重建
- 使用 `--no-cache` 确保 Docker 镜像完全重新构建

---

- **Docker Compose 配置**: `docker/docker-compose.yml`
- **Dockerfile**: `services/threat-assessment/Dockerfile`
- **集成测试脚本**: `scripts/test_v4_phase1_integration.sh`
- **故障排查指南**: `docs/guides/TROUBLESHOOTING.md`

---

## ✅ 检查清单

在提交代码或报告"完成"之前，确保：

- [ ] 已执行 `mvn clean package -DskipTests`
- [ ] 已执行 `docker compose down -v <service>`
- [ ] 已执行 `docker compose build --no-cache <service>`
- [ ] 已执行 `docker compose up -d --force-recreate <service>`
- [ ] 已执行 `docker compose ps` 验证容器状态
- [ ] 已查看容器日志确认服务启动成功
- [ ] 已运行集成测试验证功能正常

---

**强制执行**: 所有 AI 助手必须在代码修改后执行此流程，无例外  
**审核要求**: 代码审查时检查是否遵循此流程  
**文档状态**: ✅ 强制执行标准，所有开发人员必须遵守
