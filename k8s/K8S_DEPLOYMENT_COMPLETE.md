# Kubernetes 一键部署完整指南

## 🎯 项目状态

**K8s配置完整性**: ✅ **100%** (13/13 服务)

所有服务都已配置K8s YAML，支持一键部署！

---

## 📋 服务清单

| # | 服务名称 | 类型 | 副本数 | 端口 | 状态 |
|---|---------|------|--------|------|------|
| 1 | namespace | Namespace | - | - | ✅ |
| 2 | postgres | StatefulSet | 1 | 5432 | ✅ |
| 3 | redis | Deployment | 1 | 6379 | ✅ |
| 4 | zookeeper | StatefulSet | 1 | 2181 | ✅ |
| 5 | kafka | StatefulSet | 1 | 9092 | ✅ |
| 6 | kafka-topic-init | Job | - | - | ✅ |
| 7 | logstash | Deployment | 2 | 9080, 9600 | ✅ |
| 8 | data-ingestion | Deployment | 2 | 8080 | ✅ |
| 9 | stream-processing (JobManager) | Deployment | 1 | 8081, 6123 | ✅ |
| 10 | taskmanager | Deployment | 2 | 6122 | ✅ NEW |
| 11 | customer-management | Deployment | 2 | 8081 | ✅ |
| 12 | threat-assessment | Deployment | 2 | 8082 | ✅ |
| 13 | alert-management | Deployment | 2 | 8083 | ✅ |

**总计**: 13个服务，~30个Pod (取决于环境)

---

## 🚀 快速部署 (3步)

### 第1步: 构建Docker镜像

```bash
# 批量构建所有服务镜像
./scripts/build-all-images.sh

# 或单独构建
cd services/data-ingestion
mvn package -DskipTests
docker build -t threat-detection/data-ingestion:latest .
```

**预期输出**:
```
✅ Maven构建成功
🐳 构建镜像: threat-detection/data-ingestion:latest
🐳 构建镜像: threat-detection/customer-management:latest
🐳 构建镜像: threat-detection/threat-assessment:latest
🐳 构建镜像: threat-detection/alert-management:latest
📊 成功: 4, 失败: 0
```

### 第2步: 一键部署到K8s

```bash
# 使用自动化脚本
./scripts/k8s-deploy.sh

# 选择环境:
# 1) Development (开发环境) - 资源少,单副本
# 2) Production (生产环境) - 高可用,多副本
# 3) Base (基础配置) - 默认配置
```

**脚本功能**:
- ✅ 自动检查kubectl和k8s连接
- ✅ 自动选择部署环境
- ✅ 检查Docker镜像是否已构建
- ✅ 应用Kustomize配置
- ✅ 等待所有Pod启动就绪
- ✅ 显示部署信息和访问方式
- ✅ 提供后续操作指南

### 第3步: 验证部署

```bash
# 查看所有Pod状态
kubectl get pods -n threat-detection

# 查看服务
kubectl get svc -n threat-detection

# 查看存储
kubectl get pvc -n threat-detection
```

---

## 📦 部署环境对比

### Development (开发环境)

**资源配置**:
- PostgreSQL: 2Gi存储
- Redis: 128Mi-256Mi内存
- Logstash: 1副本, 256Mi-512Mi
- 业务服务: 1副本, 256Mi-512Mi
- TaskManager: 1副本, 2 slots

**特点**:
- ✅ 资源消耗低 (适合本地开发)
- ✅ 快速启动
- ✅ NodePort服务 (方便本地访问)
- ✅ DEBUG日志级别
- ✅ 默认密码 (dev_password_123)

**部署命令**:
```bash
kubectl apply -k k8s/overlays/development
```

**总资源需求**: ~4GB内存, ~2 CPU cores

### Production (生产环境)

**资源配置**:
- PostgreSQL: 50Gi存储, 1Gi-2Gi内存
- Redis: 512Mi-1Gi内存
- Logstash: 3副本, 1Gi-2Gi
- 业务服务: 3副本, 1Gi-2Gi
- TaskManager: 3副本, 8 slots

**特点**:
- ✅ 高可用 (多副本)
- ✅ Pod反亲和性 (分散到不同节点)
- ✅ PodDisruptionBudget (确保最小可用数)
- ✅ 生产级资源限制
- ✅ LoadBalancer服务
- ✅ INFO日志级别
- ⚠️ **需要修改默认密码**

**部署命令**:
```bash
# ⚠️ 修改密码
kubectl create secret generic postgres-secret \
  --from-literal=POSTGRES_DB=threat_detection_prod \
  --from-literal=POSTGRES_USER=postgres \
  --from-literal=POSTGRES_PASSWORD=<STRONG_PASSWORD> \
  -n threat-detection

# 部署
kubectl apply -k k8s/overlays/production
```

**总资源需求**: ~24GB内存, ~12 CPU cores

---

## 🔧 手动部署步骤

如果不使用自动化脚本，可以手动部署:

### 1. 创建命名空间
```bash
kubectl apply -f k8s/base/namespace.yaml
```

### 2. 部署基础设施层
```bash
kubectl apply -f k8s/base/postgres.yaml
kubectl apply -f k8s/base/redis.yaml
kubectl apply -f k8s/base/zookeeper.yaml

# 等待就绪
kubectl wait --for=condition=ready pod -l app=postgres -n threat-detection --timeout=300s
```

### 3. 部署消息队列
```bash
kubectl apply -f k8s/base/kafka.yaml
kubectl apply -f k8s/base/kafka-topic-init.yaml

# 等待就绪
kubectl wait --for=condition=ready pod -l app=kafka -n threat-detection --timeout=300s
```

### 4. 部署日志收集
```bash
kubectl apply -f k8s/base/logstash.yaml

# 等待就绪
kubectl wait --for=condition=ready pod -l app=logstash -n threat-detection --timeout=300s
```

### 5. 部署流处理
```bash
kubectl apply -f k8s/base/stream-processing.yaml
kubectl apply -f k8s/base/taskmanager.yaml

# 等待就绪
kubectl wait --for=condition=ready pod -l app=stream-processing -n threat-detection --timeout=300s
```

### 6. 部署业务服务
```bash
kubectl apply -f k8s/base/data-ingestion.yaml
kubectl apply -f k8s/base/customer-management.yaml
kubectl apply -f k8s/base/threat-assessment.yaml
kubectl apply -f k8s/base/alert-management.yaml
```

---

## 🌐 访问服务

### Logstash (外部rsyslog接入)

**获取LoadBalancer外部IP**:
```bash
kubectl get svc logstash -n threat-detection

# 输出:
# NAME       TYPE           EXTERNAL-IP      PORT(S)
# logstash   LoadBalancer   35.123.45.67     9080:30080/TCP
```

**配置设备rsyslog**:
```bash
# /etc/rsyslog.d/50-threat-detection.conf
*.* @@35.123.45.67:9080
```

### API服务 (通过Port-Forward)

```bash
# Customer Management
kubectl port-forward -n threat-detection svc/customer-management 8081:8081
# 访问: http://localhost:8081/actuator/health

# Threat Assessment
kubectl port-forward -n threat-detection svc/threat-assessment 8082:8082
# 访问: http://localhost:8082/actuator/health

# Alert Management
kubectl port-forward -n threat-detection svc/alert-management 8083:8083
# 访问: http://localhost:8083/actuator/health
```

### Flink Dashboard (JobManager)

```bash
kubectl port-forward -n threat-detection svc/stream-processing 8081:8081
# 访问: http://localhost:8081
```

### Kafka (内部访问)

```bash
# 进入Kafka Pod
kubectl exec -it -n threat-detection kafka-0 -- bash

# 列出Topics
kafka-topics --list --bootstrap-server localhost:9092

# 消费消息
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic attack-events --from-beginning
```

---

## 📊 监控命令

### 查看Pod状态
```bash
# 所有Pod
kubectl get pods -n threat-detection -o wide

# 持续监控
kubectl get pods -n threat-detection -w

# 查看特定服务
kubectl get pods -n threat-detection -l app=logstash
```

### 查看日志
```bash
# Logstash日志
kubectl logs -n threat-detection -l app=logstash -f --tail=100

# 业务服务日志
kubectl logs -n threat-detection -l app=threat-assessment -f

# 所有容器日志
kubectl logs -n threat-detection <pod-name> --all-containers=true
```

### 查看资源使用
```bash
# Pod资源使用
kubectl top pods -n threat-detection

# Node资源使用
kubectl top nodes
```

### 查看事件
```bash
# 命名空间事件
kubectl get events -n threat-detection --sort-by='.lastTimestamp'

# 特定Pod事件
kubectl describe pod <pod-name> -n threat-detection
```

---

## 🔄 运维操作

### 扩缩容

```bash
# 扩展业务服务
kubectl scale deployment threat-assessment -n threat-detection --replicas=5

# 扩展TaskManager
kubectl scale deployment taskmanager -n threat-detection --replicas=4

# 自动扩缩容 (HPA)
kubectl autoscale deployment threat-assessment \
  -n threat-detection \
  --cpu-percent=70 \
  --min=2 \
  --max=10
```

### 滚动更新

```bash
# 更新镜像
kubectl set image deployment/threat-assessment \
  threat-assessment=threat-detection/threat-assessment:v1.1.0 \
  -n threat-detection

# 查看更新状态
kubectl rollout status deployment/threat-assessment -n threat-detection

# 查看历史
kubectl rollout history deployment/threat-assessment -n threat-detection

# 回滚
kubectl rollout undo deployment/threat-assessment -n threat-detection
```

### 重启服务

```bash
# 重启Deployment (滚动重启)
kubectl rollout restart deployment/logstash -n threat-detection

# 删除Pod (自动重建)
kubectl delete pod <pod-name> -n threat-detection
```

### 修改配置

```bash
# 编辑ConfigMap
kubectl edit configmap logstash-config -n threat-detection

# 编辑Secret
kubectl edit secret postgres-secret -n threat-detection

# 重启相关服务使配置生效
kubectl rollout restart deployment/logstash -n threat-detection
```

---

## 🛠️ 故障排查

### Pod启动失败

```bash
# 查看Pod详情
kubectl describe pod <pod-name> -n threat-detection

# 查看日志
kubectl logs <pod-name> -n threat-detection --previous

# 常见问题:
# 1. ImagePullBackOff - 镜像不存在,运行 build-all-images.sh
# 2. CrashLoopBackOff - 应用启动失败,检查日志
# 3. Pending - 资源不足,检查 kubectl describe node
```

### 服务无法访问

```bash
# 检查Service
kubectl get svc -n threat-detection
kubectl describe svc <service-name> -n threat-detection

# 检查Endpoints
kubectl get endpoints -n threat-detection

# 测试内部连接
kubectl run test-pod --rm -it --image=busybox -n threat-detection -- sh
# 在Pod内: nc -zv <service-name> <port>
```

### 存储问题

```bash
# 查看PVC状态
kubectl get pvc -n threat-detection
kubectl describe pvc <pvc-name> -n threat-detection

# 查看PV
kubectl get pv

# 常见问题:
# 1. PVC Pending - StorageClass不存在或无可用PV
# 2. 创建默认StorageClass:
kubectl apply -f - <<EOF
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: local-storage
  annotations:
    storageclass.kubernetes.io/is-default-class: "true"
provisioner: kubernetes.io/no-provisioner
volumeBindingMode: WaitForFirstConsumer
EOF
```

### 性能问题

```bash
# 查看资源使用
kubectl top pods -n threat-detection

# 查看资源限制
kubectl describe pod <pod-name> -n threat-detection | grep -A 5 "Limits:"

# 调整资源限制 (编辑Deployment)
kubectl edit deployment <deployment-name> -n threat-detection
```

---

## 🗑️ 卸载

### 完全卸载

```bash
# 删除所有资源
kubectl delete -k k8s/overlays/production  # 或 development

# 或使用base
kubectl delete -k k8s/base

# 删除PVC (⚠️ 会删除所有数据)
kubectl delete pvc --all -n threat-detection

# 删除命名空间
kubectl delete namespace threat-detection
```

### 部分卸载 (保留数据)

```bash
# 只删除应用,保留PostgreSQL和PVC
kubectl delete deployment,service,configmap -l app.kubernetes.io/component=microservice -n threat-detection
```

---

## 📈 性能优化

### 1. 资源优化

```yaml
# 根据实际使用调整资源
resources:
  requests:
    memory: "实际使用 × 1.2"
    cpu: "实际使用 × 1.2"
  limits:
    memory: "requests × 2"
    cpu: "requests × 4"
```

### 2. 水平扩展

```bash
# 基于CPU自动扩缩容
kubectl autoscale deployment threat-assessment \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n threat-detection
```

### 3. 节点亲和性

```yaml
# 将计算密集型服务调度到高性能节点
affinity:
  nodeAffinity:
    requiredDuringSchedulingIgnoredDuringExecution:
      nodeSelectorTerms:
      - matchExpressions:
        - key: node-type
          operator: In
          values:
          - high-performance
```

---

## 🔐 安全加固

### 1. 修改默认密码

```bash
# 生产环境必须修改PostgreSQL密码
kubectl create secret generic postgres-secret \
  --from-literal=POSTGRES_DB=threat_detection_prod \
  --from-literal=POSTGRES_USER=postgres \
  --from-literal=POSTGRES_PASSWORD=$(openssl rand -base64 32) \
  -n threat-detection \
  --dry-run=client -o yaml | kubectl apply -f -
```

### 2. NetworkPolicy (网络隔离)

```bash
# 限制只有特定服务可以访问PostgreSQL
kubectl apply -f k8s/security/networkpolicy.yaml
```

### 3. RBAC (权限控制)

```bash
# 创建只读用户
kubectl apply -f k8s/security/rbac.yaml
```

---

## 📚 下一步

### 已完成 ✅
- [x] 所有13个服务的K8s配置
- [x] Development和Production overlays
- [x] 一键部署脚本
- [x] 镜像批量构建脚本
- [x] 完整部署文档

### 待完成 🔄
- [ ] Ingress配置 (统一访问入口)
- [ ] Cert-Manager (自动HTTPS证书)
- [ ] NetworkPolicy (网络安全策略)
- [ ] RBAC (细粒度权限控制)
- [ ] Prometheus + Grafana (监控告警)
- [ ] ELK Stack (日志聚合)
- [ ] CI/CD Pipeline (自动化部署)

---

## 💡 常用命令速查

```bash
# 🚀 快速部署
./scripts/build-all-images.sh    # 构建镜像
./scripts/k8s-deploy.sh          # 一键部署

# 📊 查看状态
kubectl get all -n threat-detection
kubectl get pods -n threat-detection -w

# 📝 查看日志
kubectl logs -n threat-detection -l app=logstash -f

# 🔧 调试
kubectl exec -it <pod-name> -n threat-detection -- bash
kubectl describe pod <pod-name> -n threat-detection

# 🔄 更新
kubectl rollout restart deployment/<name> -n threat-detection
kubectl rollout status deployment/<name> -n threat-detection

# 📈 扩缩容
kubectl scale deployment/<name> --replicas=5 -n threat-detection

# 🗑️ 卸载
kubectl delete -k k8s/overlays/production
```

---

## 🎉 总结

恭喜！您的威胁检测系统现在支持**K8s一键部署**：

1. **100%配置完整** - 所有13个服务都有K8s配置
2. **多环境支持** - Development/Production overlays
3. **自动化脚本** - 镜像构建和部署全自动
4. **生产就绪** - 高可用、健康检查、资源限制
5. **完整文档** - 部署、运维、故障排查全覆盖

**下次部署只需3条命令**:
```bash
./scripts/build-all-images.sh     # 一次性构建所有镜像
./scripts/k8s-deploy.sh           # 一键部署
kubectl get pods -n threat-detection  # 验证部署
```

🚀 **开始您的K8s之旅吧！**
