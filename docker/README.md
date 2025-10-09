# Docker 开发环境

本项目使用Docker Compose作为主要的开发环境，支持快速启动和调试威胁检测系统。

## 快速开始

### 前置要求

- Docker Desktop 4.0+ 或 Docker Engine 20.10+
- Docker Compose v2.0+
- 至少4GB可用内存
- 至少2GB可用磁盘空间

### 启动开发环境

```bash
# 克隆项目
git clone <repository-url>
cd threat-detection-system

# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 验证部署

```bash
# 检查服务健康状态
curl http://localhost:8080/actuator/health

# 检查Kafka主题
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# 访问Flink Web UI
open http://localhost:8081
```

## 服务架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Data Ingestion │ -> │     Kafka       │ -> │ Stream Processing│
│    (Spring)     │    │   (Message)     │    │    (Flink)       │
│                 │    │                 │    │                 │
│ Port: 8080      │    │ Port: 9092      │    │ Port: 8081      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │   Zookeeper     │
                    │   (Coordination)│
                    │                 │
                    │ Port: 2181     │
                    └─────────────────┘
```

## 服务详情

### Data Ingestion Service
- **技术栈**: Spring Boot 3.1+, OpenJDK 21
- **端口**: 8080
- **功能**: REST API数据采集，Kafka生产者
- **健康检查**: `http://localhost:8080/actuator/health`

### Kafka
- **版本**: 3.4+
- **端口**: 9092 (内部), 9094 (外部)
- **功能**: 消息队列，事件流处理
- **主题**: threat-events, processed-threats

### Stream Processing (Flink)
- **版本**: 1.17+
- **端口**: 8081 (JobManager Web UI)
- **组件**:
  - JobManager: 协调和调度
  - TaskManager: 执行任务
- **功能**: 实时威胁检测和聚合

### Zookeeper
- **版本**: 3.8+
- **端口**: 2181
- **功能**: Kafka集群协调

## 开发工作流

### 代码修改和重启

```bash
# 修改代码后重新构建
docker-compose build data-ingestion

# 重启服务
docker-compose up -d data-ingestion

# 查看重启日志
docker-compose logs -f data-ingestion
```

### 调试模式

```bash
# 以调试模式启动 (JDWP端口5005)
docker-compose -f docker-compose.debug.yml up -d

# 连接IDE调试器到localhost:5005
```

### 日志监控

```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f data-ingestion

# 实时日志过滤
docker-compose logs -f | grep "ERROR\|WARN"
```

## 数据管理

### Kafka主题管理

```bash
# 列出所有主题
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# 创建新主题
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --create --topic test-topic --partitions 3 --replication-factor 1

# 查看主题详情
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic threat-events
```

### 数据清理

```bash
# 停止服务
docker-compose down

# 删除所有数据卷
docker volume prune -f

# 重新启动 (数据重置)
docker-compose up -d
```

## 性能监控

### 资源使用情况

```bash
# 查看容器资源使用
docker stats

# 查看磁盘使用
docker system df

# 查看网络流量
docker network ls
docker network inspect threat-detection-system_default
```

### 应用指标

```bash
# Spring Boot Actuator指标
curl http://localhost:8080/actuator/metrics

# JVM信息
curl http://localhost:8080/actuator/info

# Flink作业状态
curl http://localhost:8081/jobs/overview
```

## 故障排除

### 常见问题

1. **端口冲突**
   ```bash
   # 检查端口占用
   netstat -tulpn | grep :8080

   # 修改docker-compose.yml中的端口映射
   ```

2. **内存不足**
   ```bash
   # 增加Docker内存分配 (Docker Desktop设置)
   # 或减少服务资源限制
   ```

3. **服务启动失败**
   ```bash
   # 查看详细日志
   docker-compose logs <service-name>

   # 检查依赖服务状态
   docker-compose ps
   ```

4. **Kafka连接问题**
   ```bash
   # 测试连接
   docker-compose exec kafka kafka-console-producer --bootstrap-server localhost:9092 --topic test

   # 检查Zookeeper状态
   docker-compose exec zookeeper zkCli.sh ls /
   ```

### 调试技巧

```bash
# 进入容器shell
docker-compose exec data-ingestion bash

# 查看容器环境变量
docker-compose exec data-ingestion env

# 检查网络连接
docker-compose exec data-ingestion ping kafka

# 查看Java进程
docker-compose exec data-ingestion jps -l
```

## 扩展开发

### 添加新服务

1. 在`docker-compose.yml`中添加服务定义
2. 配置依赖关系和网络
3. 添加健康检查
4. 更新文档

### 环境变量配置

```yaml
# 在docker-compose.yml中添加
services:
  my-service:
    environment:
      - SPRING_PROFILES_ACTIVE=development
      - LOG_LEVEL=DEBUG
```

### 卷挂载 (代码热重载)

```yaml
services:
  data-ingestion:
    volumes:
      - ./services/data-ingestion/target:/app/target
      - ./services/data-ingestion/src:/app/src
```

## CI/CD集成

### GitHub Actions 示例

```yaml
name: Docker Build and Test
on: [push]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3

    - name: Build and start services
      run: docker-compose up -d --build

    - name: Wait for services
      run: |
        timeout 300 bash -c 'until curl -f http://localhost:8080/actuator/health; do sleep 5; done'

    - name: Run tests
      run: docker-compose exec -T data-ingestion ./mvnw test

    - name: Stop services
      run: docker-compose down
```

## 最佳实践

1. **资源管理**: 为所有服务设置内存和CPU限制
2. **健康检查**: 配置适当的健康检查间隔
3. **日志轮转**: 避免日志文件过大
4. **网络隔离**: 使用内部网络，只暴露必要端口
5. **数据持久化**: 重要数据使用命名卷
6. **安全**: 不要在生产中使用root用户

## 维护指南

### 版本更新

```bash
# 更新镜像版本
docker-compose pull

# 重新构建自定义服务
docker-compose build --no-cache

# 滚动更新
docker-compose up -d --no-deps <service-name>
```

### 备份和恢复

```bash
# 备份数据卷
docker run --rm -v threat-detection-system_kafka-data:/data -v $(pwd):/backup alpine tar czf /backup/kafka-backup.tar.gz -C /data .

# 恢复数据卷
docker run --rm -v threat-detection-system_kafka-data:/data -v $(pwd):/backup alpine tar xzf /backup/kafka-backup.tar.gz -C /data
```

### 清理环境

```bash
# 停止并删除所有容器
docker-compose down

# 删除镜像
docker-compose down --rmi all

# 删除卷
docker volume rm $(docker volume ls -q | grep threat-detection-system)

# 完全清理
docker system prune -a --volumes
```