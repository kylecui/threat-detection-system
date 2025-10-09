# Kubernetes 部署配置

本目录包含威胁检测系统的Kubernetes部署配置，支持多环境部署。

## 目录结构

```
k8s/
├── base/                    # 基础配置
│   ├── namespace.yaml       # 命名空间定义
│   ├── zookeeper.yaml       # Zookeeper部署和服务
│   ├── kafka.yaml          # Kafka部署和服务
│   ├── kafka-topic-init.yaml # Kafka主题初始化Job
│   ├── data-ingestion.yaml  # 数据采集服务
│   ├── stream-processing.yaml # 流处理服务
│   └── kustomization.yaml   # Kustomize基础配置
├── overlays/               # 环境特定配置
│   ├── development/        # 开发环境
│   │   ├── kustomization.yaml
│   │   ├── replica-patch.yaml
│   │   └── resource-patch.yaml
│   └── production/         # 生产环境
│       ├── kustomization.yaml
│       ├── replica-patch.yaml
│       ├── resource-patch.yaml
│       └── security-patch.yaml
└── helm/                   # Helm Chart (预留)
```

## 快速开始

### 前置要求

- Kubernetes 1.25+
- kubectl 1.25+
- kustomize 4.0+

### 部署到开发环境

```bash
# 部署到开发环境
kubectl apply -k k8s/overlays/development

# 查看部署状态
kubectl get pods -n threat-detection-dev
kubectl get services -n threat-detection-dev

# 查看日志
kubectl logs -n threat-detection-dev deployment/data-ingestion
kubectl logs -n threat-detection-dev deployment/stream-processing-jobmanager
```

### 部署到生产环境

```bash
# 部署到生产环境
kubectl apply -k k8s/overlays/production

# 查看部署状态
kubectl get pods -n threat-detection-prod
```

## 配置说明

### 基础配置 (base/)

- **namespace.yaml**: 定义threat-detection命名空间
- **zookeeper.yaml**: Zookeeper单节点部署
- **kafka.yaml**: Kafka单节点部署，包含健康检查
- **kafka-topic-init.yaml**: 初始化Kafka主题的Job
- **data-ingestion.yaml**: 数据采集服务部署
- **stream-processing.yaml**: Flink流处理服务部署 (JobManager + TaskManager)

### 环境覆盖 (overlays/)

#### 开发环境 (development/)
- 单副本部署
- 较低的资源限制
- 调试日志级别
- 开发版镜像标签

#### 生产环境 (production/)
- 多副本部署 (高可用)
- 更高的资源限制
- 生产日志级别
- 安全上下文配置
- 正式版镜像标签

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| data-ingestion | 8080 | REST API和健康检查 |
| stream-processing | 8081 | Flink Web UI |
| kafka | 9092 | Kafka客户端连接 |
| zookeeper | 2181 | Zookeeper客户端连接 |

## 监控和调试

### 查看服务状态

```bash
# 查看所有资源
kubectl get all -n threat-detection-dev

# 查看Pod详情
kubectl describe pod <pod-name> -n threat-detection-dev

# 查看服务日志
kubectl logs -f deployment/data-ingestion -n threat-detection-dev
```

### 健康检查

```bash
# 检查服务健康状态
kubectl exec -it <pod-name> -n threat-detection-dev -- curl http://localhost:8080/actuator/health

# 检查Kafka主题
kubectl exec -it <kafka-pod> -n threat-detection-dev -- kafka-topics --bootstrap-server localhost:9092 --list
```

### 端口转发 (用于本地访问)

```bash
# 转发data-ingestion服务到本地
kubectl port-forward -n threat-detection-dev svc/data-ingestion 8080:8080

# 转发Flink Web UI到本地
kubectl port-forward -n threat-detection-dev svc/stream-processing 8081:8081
```

## 故障排除

### 常见问题

1. **Pod启动失败**
   ```bash
   kubectl describe pod <pod-name> -n <namespace>
   kubectl logs <pod-name> -n <namespace>
   ```

2. **服务无法访问**
   ```bash
   kubectl get endpoints -n <namespace>
   kubectl describe service <service-name> -n <namespace>
   ```

3. **Kafka连接问题**
   ```bash
   kubectl exec -it <kafka-pod> -n <namespace> -- kafka-topics --bootstrap-server localhost:9092 --list
   ```

### 清理部署

```bash
# 删除开发环境
kubectl delete -k k8s/overlays/development

# 删除生产环境
kubectl delete -k k8s/overlays/production

# 删除命名空间 (会删除所有资源)
kubectl delete namespace threat-detection-dev
kubectl delete namespace threat-detection-prod
```

## CI/CD集成

### GitHub Actions 示例

```yaml
name: Deploy to Kubernetes
on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3

    - name: Configure kubectl
      uses: azure/k8s-set-context@v2
      with:
        kubeconfig: ${{ secrets.KUBE_CONFIG }}

    - name: Deploy to development
      run: kubectl apply -k k8s/overlays/development

    - name: Wait for rollout
      run: kubectl rollout status deployment/data-ingestion -n threat-detection-dev --timeout=300s
```

## 扩展配置

### 添加新服务

1. 在`base/`目录创建新的YAML文件
2. 在`base/kustomization.yaml`的`resources`中添加新文件
3. 为不同环境创建相应的patch文件

### 添加ConfigMap/Secret

在`kustomization.yaml`中添加：

```yaml
configMapGenerator:
  - name: my-config
    literals:
      - KEY=VALUE

secretGenerator:
  - name: my-secret
    literals:
      - password=secretvalue
```

## 最佳实践

1. **命名空间隔离**: 不同环境使用不同命名空间
2. **资源限制**: 为所有容器设置适当的资源请求和限制
3. **健康检查**: 配置readiness和liveness探针
4. **安全上下文**: 生产环境使用非root用户运行
5. **标签管理**: 使用一致的标签策略
6. **版本控制**: 镜像使用明确的版本标签

## 维护指南

### 版本更新

1. 更新镜像版本标签
2. 修改`kustomization.yaml`中的`images`部分
3. 运行滚动更新

### 扩缩容

```bash
# 调整副本数
kubectl scale deployment data-ingestion --replicas=5 -n threat-detection-prod

# 自动扩缩容 (需要Metrics Server)
kubectl autoscale deployment data-ingestion --cpu-percent=70 --min=3 --max=10 -n threat-detection-prod
```

### 备份和恢复

目前配置主要用于无状态服务。对于有状态数据，请配置持久卷和备份策略。