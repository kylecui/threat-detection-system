# Kubernetes 一键部署指南

## 前置条件

1. **Kubernetes集群** (版本 >= 1.25)
   - Minikube (本地测试)
   - Kind (本地测试)
   - GKE/EKS/AKS (生产环境)

2. **kubectl** 已安装并配置

3. **存储类** (StorageClass)
   - 确保集群有默认StorageClass
   - 或者手动创建: `kubectl apply -f k8s/storage/`

4. **Docker镜像** 已构建
   ```bash
   # 构建所有服务镜像
   ./scripts/build-all-images.sh
   ```

## 快速部署

### 方式1: 使用 kubectl + kustomize (推荐)

```bash
# 1. 部署到开发环境
kubectl apply -k k8s/overlays/development

# 2. 部署到生产环境
kubectl apply -k k8s/overlays/production

# 3. 查看部署状态
kubectl get pods -n threat-detection

# 4. 查看服务
kubectl get svc -n threat-detection
```

### 方式2: 直接部署基础配置

```bash
# 部署所有资源
kubectl apply -k k8s/base/

# 验证部署
kubectl get all -n threat-detection
```

## 服务列表

| 服务名称 | 类型 | 端口 | 说明 |
|---------|------|------|------|
| postgres | StatefulSet | 5432 | PostgreSQL数据库 |
| redis | Deployment | 6379 | Redis缓存 |
| zookeeper | StatefulSet | 2181 | Zookeeper协调 |
| kafka | StatefulSet | 9092 | Kafka消息队列 |
| logstash | Deployment | 9080, 9600 | 日志收集 (LoadBalancer) |
| data-ingestion | Deployment | 8080 | 数据摄入服务 |
| stream-processing | Deployment | - | Flink流处理 |
| customer-management | Deployment | 8081 | 客户管理 |
| threat-assessment | Deployment | 8082 | 威胁评估 |
| alert-management | Deployment | 8083 | 告警管理 |

## 访问服务

### 1. Logstash (接收rsyslog)

```bash
# 获取LoadBalancer外部IP
kubectl get svc logstash -n threat-detection

# 配置设备rsyslog指向该IP:9080
```

### 2. API服务 (通过Port-Forward)

```bash
# Customer Management API
kubectl port-forward -n threat-detection svc/customer-management 8081:8081

# Threat Assessment API
kubectl port-forward -n threat-detection svc/threat-assessment 8082:8082

# Alert Management API
kubectl port-forward -n threat-detection svc/alert-management 8083:8083
```

### 3. 监控和调试

```bash
# 查看Pod日志
kubectl logs -n threat-detection -l app=logstash -f

# 进入Pod
kubectl exec -it -n threat-detection postgres-0 -- bash

# 查看Kafka topics
kubectl exec -it -n threat-detection kafka-0 -- kafka-topics --list --bootstrap-server localhost:9092
```

## 配置说明

### 1. PostgreSQL密码

默认密码在 `k8s/base/postgres.yaml` 的Secret中定义：
- 用户名: `threat_user`
- 密码: `threat_password_k8s_change_me` ⚠️ 生产环境请修改

### 2. 资源限制

每个服务都配置了资源请求和限制：
- Requests: 保证的最小资源
- Limits: 最大可用资源

生产环境请根据实际负载调整。

### 3. 持久化存储

以下服务使用持久化存储：
- PostgreSQL: 10Gi (StatefulSet PVC)
- Redis: EmptyDir (可选配置PVC)

## 扩缩容

```bash
# 扩展Customer Management服务副本数
kubectl scale deployment customer-management -n threat-detection --replicas=5

# 扩展Kafka分区（需要手动调整）
kubectl exec -it -n threat-detection kafka-0 -- kafka-topics --alter \
  --topic attack-events --partitions 8 --bootstrap-server localhost:9092
```

## 升级部署

```bash
# 1. 构建新镜像
docker build -t threat-detection/customer-management:v2.0 services/customer-management/

# 2. 更新镜像
kubectl set image deployment/customer-management \
  customer-management=threat-detection/customer-management:v2.0 \
  -n threat-detection

# 3. 查看滚动更新状态
kubectl rollout status deployment/customer-management -n threat-detection

# 4. 回滚（如果需要）
kubectl rollout undo deployment/customer-management -n threat-detection
```

## 卸载

```bash
# 删除所有资源
kubectl delete -k k8s/base/

# 或删除命名空间（会删除所有内容）
kubectl delete namespace threat-detection
```

## 故障排查

### Pod启动失败

```bash
# 查看Pod状态
kubectl describe pod <pod-name> -n threat-detection

# 查看日志
kubectl logs <pod-name> -n threat-detection --previous
```

### 服务无法访问

```bash
# 检查Service
kubectl get svc -n threat-detection

# 检查Endpoints
kubectl get endpoints -n threat-detection

# 测试服务连接
kubectl run test-pod --rm -it --image=busybox -n threat-detection -- sh
# 在Pod内: nc -zv postgres 5432
```

### 持久化存储问题

```bash
# 查看PVC状态
kubectl get pvc -n threat-detection

# 查看PV
kubectl get pv

# 检查StorageClass
kubectl get storageclass
```

## 性能优化

### 1. 启用HPA (水平自动扩缩容)

```bash
kubectl autoscale deployment customer-management \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n threat-detection
```

### 2. 配置Pod亲和性

编辑 `k8s/base/*.yaml`，添加：

```yaml
spec:
  template:
    spec:
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - customer-management
              topologyKey: kubernetes.io/hostname
```

## 监控集成

### Prometheus + Grafana

```bash
# 安装Prometheus Operator
kubectl apply -f https://raw.githubusercontent.com/prometheus-operator/prometheus-operator/main/bundle.yaml

# 部署ServiceMonitor
kubectl apply -f k8s/monitoring/servicemonitor.yaml
```

## 下一步

1. ✅ 配置Ingress暴露API服务
2. ✅ 集成Cert-Manager实现自动HTTPS
3. ✅ 配置网络策略(NetworkPolicy)
4. ✅ 实施RBAC权限控制
5. ✅ 集成日志聚合(ELK/Loki)

## 参考文档

- [Kubernetes官方文档](https://kubernetes.io/docs/)
- [Kustomize文档](https://kustomize.io/)
- [项目架构文档](../../docs/cloud_native_architecture.md)
