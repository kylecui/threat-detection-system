# Kubernetes 部署配置

本目录包含威胁检测系统的完整Kubernetes部署配置，支持多环境部署，包括所有微服务组件。

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
│   ├── threat-assessment.yaml # 威胁评估服务
│   ├── alert-management.yaml  # 告警管理服务
│   ├── api-gateway.yaml     # API网关服务
│   ├── config-server.yaml   # 配置服务器
│   ├── kustomization.yaml   # Kustomize基础配置
│   └── configmap.yaml       # 共享配置
├── overlays/               # 环境特定配置
│   ├── development/        # 开发环境
│   │   ├── kustomization.yaml
│   │   ├── replica-patch.yaml
│   │   └── resource-patch.yaml
│   └── production/         # 生产环境
│       ├── kustomization.yaml
│       ├── replica-patch.yaml
│       ├── resource-patch.yaml
│       ├── security-patch.yaml
│       └── ingress-patch.yaml
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
kubectl logs -n threat-detection-dev deployment/api-gateway
kubectl logs -n threat-detection-dev deployment/config-server
```

### 部署到生产环境

```bash
# 部署到生产环境
kubectl apply -k k8s/overlays/production

# 查看部署状态
kubectl get pods -n threat-detection-prod
```

## 服务详情

### 基础配置 (base/)

- **namespace.yaml**: 定义threat-detection命名空间
- **zookeeper.yaml**: Zookeeper单节点部署，用于Kafka协调
- **kafka.yaml**: Kafka单节点部署，包含健康检查和持久卷
- **kafka-topic-init.yaml**: 初始化Kafka主题的Job，创建所有必需的主题
- **data-ingestion.yaml**: 数据采集服务部署，支持批量处理和高可靠性
- **stream-processing.yaml**: Apache Flink流处理服务部署 (JobManager + TaskManager)
- **threat-assessment.yaml**: 威胁评估服务部署，包含PostgreSQL持久化
- **alert-management.yaml**: 告警管理服务部署，支持多通道通知
- **api-gateway.yaml**: API网关服务部署，提供统一API访问
- **config-server.yaml**: 配置服务器部署，支持Git-based配置管理
- **emqx.yaml**: EMQX MQTT broker部署，V2哨兵MQTT数据接入
- **configmap.yaml**: 共享配置，包括环境变量和应用设置

### 环境覆盖 (overlays/)

#### 开发环境 (development/)
- 单副本部署
- 较低的资源限制
- 调试日志级别
- 开发版镜像标签
- 端口转发用于本地访问

#### 生产环境 (production/)
- 多副本部署 (高可用)
- 更高的资源限制
- 生产日志级别
- 安全上下文配置
- Ingress配置用于外部访问
- 正式版镜像标签

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| data-ingestion | 8080 | REST API和健康检查 |
| stream-processing | 8081 | Flink Web UI |
| alert-management | 8082 | 告警管理API |
| threat-assessment | 8083 | 威胁评估API |
| customer-management | 8084 | 客户管理API |
| api-gateway | 8888 | 统一API网关 |
| config-server | 8899 | 配置服务器API |
| emqx | 1883 | MQTT TCP (V2哨兵数据接入) |
| emqx | 18083 | EMQX Dashboard |
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
kubectl logs -f deployment/api-gateway -n threat-detection-dev
kubectl logs -f deployment/config-server -n threat-detection-dev
```

### 健康检查

```bash
# 检查服务健康状态
kubectl exec -it <pod-name> -n threat-detection-dev -- curl http://localhost:8080/actuator/health

# 检查Kafka主题
kubectl exec -it <kafka-pod> -n threat-detection-dev -- kafka-topics --bootstrap-server localhost:9092 --list

# 检查数据库连接
kubectl exec -it <threat-assessment-pod> -n threat-detection-dev -- curl http://localhost:8083/actuator/health/db
```

### 端口转发 (用于本地访问)

```bash
# 转发API网关到本地
kubectl port-forward -n threat-detection-dev svc/api-gateway 8888:8888

# 转发Flink Web UI到本地
kubectl port-forward -n threat-detection-dev svc/stream-processing 8081:8081

# 转发配置服务器到本地
kubectl port-forward -n threat-detection-dev svc/config-server 8899:8899
```

## 数据持久化

### PostgreSQL配置

生产环境包含PostgreSQL部署：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
spec:
  template:
    spec:
      containers:
      - name: postgres
        image: postgres:15
        env:
        - name: POSTGRES_DB
          value: threat_detection
        - name: POSTGRES_USER
          value: threat_user
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: password
        volumeMounts:
        - name: postgres-storage
          mountPath: /var/lib/postgresql/data
      volumes:
      - name: postgres-storage
        persistentVolumeClaim:
          claimName: postgres-pvc
```

### Redis缓存 (可选)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
spec:
  template:
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
```

## 配置管理

### Config Server集成

所有服务都配置为从Config Server获取配置：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: data-ingestion
spec:
  template:
    spec:
      containers:
      - name: data-ingestion
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: SPRING_CLOUD_CONFIG_URI
          value: "http://config-server:8899"
```

### 环境特定配置

- **development**: 使用开发环境的配置profile
- **production**: 使用生产环境的配置profile，包括加密属性

## 安全配置

### 生产环境安全特性

- **Security Context**: 非root用户运行容器
- **Network Policies**: 限制服务间通信
- **Secrets管理**: 使用Kubernetes Secrets存储敏感数据
- **RBAC**: 角色-based访问控制
- **Pod Security Standards**: 强制执行安全标准

### Ingress配置

生产环境包含Ingress配置：

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: threat-detection-ingress
spec:
  tls:
  - hosts:
    - api.threat-detection.com
    secretName: tls-secret
  rules:
  - host: api.threat-detection.com
    http:
      paths:
      - path: /api/v1
        pathType: Prefix
        backend:
          service:
            name: api-gateway
            port:
              number: 8888
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

4. **配置服务器问题**
   ```bash
   kubectl logs -f deployment/config-server -n <namespace>
   # 检查Git仓库访问权限
   ```

5. **数据库连接问题**
   ```bash
   kubectl exec -it <postgres-pod> -n <namespace> -- psql -U threat_user -d threat_detection -c "SELECT 1;"
   ```

### 调试技巧

```bash
# 查看所有命名空间的资源
kubectl get all --all-namespaces | grep threat-detection

# 查看事件
kubectl get events -n threat-detection-dev --sort-by=.metadata.creationTimestamp

# 进入容器调试
kubectl exec -it <pod-name> -n threat-detection-dev -- /bin/bash

# 查看资源使用情况
kubectl top pods -n threat-detection-dev
```

## 扩展配置

### 添加新服务

1. 在`base/`目录创建新的YAML文件
2. 在`base/kustomization.yaml`的`resources`中添加新文件
3. 为不同环境创建相应的patch文件
4. 更新Config Server配置

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

### 自定义资源

对于复杂的服务，可以使用自定义资源：

```yaml
apiVersion: threat-detection.io/v1
kind: ThreatDetectionService
metadata:
  name: custom-service
spec:
  replicas: 3
  image: my-custom-image:latest
  config:
    kafkaTopic: custom-events
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
      run: kubectl rollout status deployment/api-gateway -n threat-detection-dev --timeout=300s

    - name: Run integration tests
      run: |
        kubectl port-forward -n threat-detection-dev svc/api-gateway 8888:8888 &
        sleep 10
        curl http://localhost:8888/actuator/health
```

### ArgoCD配置

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: threat-detection
spec:
  project: default
  source:
    repoURL: https://github.com/your-org/threat-detection-system
    path: k8s/overlays/production
    targetRevision: HEAD
  destination:
    server: https://kubernetes.default.svc
    namespace: threat-detection-prod
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
```

## 最佳实践

1. **命名空间隔离**: 不同环境使用不同命名空间
2. **资源限制**: 为所有容器设置适当的资源请求和限制
3. **健康检查**: 配置readiness和liveness探针
4. **安全上下文**: 生产环境使用非root用户运行
5. **标签管理**: 使用一致的标签策略
6. **版本控制**: 镜像使用明确的版本标签
7. **监控集成**: 集成Prometheus和Grafana进行监控
8. **日志聚合**: 使用EFK栈进行集中日志管理

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

### 升级策略

1. **蓝绿部署**: 创建新版本的deployment，切换流量
2. **滚动更新**: 逐步替换旧版本的pods
3. **金丝雀部署**: 先部署少量新版本，验证后再全量部署

## 性能优化

### 资源配置

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: data-ingestion
spec:
  template:
    spec:
      containers:
      - name: data-ingestion
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
```

### JVM调优

```yaml
env:
- name: JAVA_OPTS
  value: "-Xmx800m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### Kafka调优

```yaml
env:
- name: KAFKA_HEAP_OPTS
  value: "-Xmx1g -Xms1g"
```

## 故障恢复

### Pod重启策略

```yaml
spec:
  template:
    spec:
      restartPolicy: Always
      containers:
      - name: app
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
```

### 自动恢复

- **Config Server**: 如果配置服务器失败，其他服务会继续使用缓存的配置
- **Kafka**: 使用持久卷确保数据不丢失
- **数据库**: 使用主从复制确保高可用性

---

*最后更新时间：2025年10月10日*
*Kubernetes版本支持：1.25+*
*包含所有微服务：数据摄取、流处理、威胁评估、告警管理、API网关、配置服务器*