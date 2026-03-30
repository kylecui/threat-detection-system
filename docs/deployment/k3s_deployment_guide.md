# K3s 部署指南与故障排查 FAQ (K3s Deployment Guide & Troubleshooting FAQ)

本指南涵盖了威胁检测系统的 K3s 环境安装、配置、部署及日常运维操作，并汇总了实际部署过程中遇到的常见问题及解决方案。

This guide covers the K3s environment installation, configuration, deployment, and daily operations for the Threat Detection System, summarizing real-world issues and solutions encountered during deployment.

---

## 第1部分：前置条件 (Section 1: Prerequisites)

在开始部署之前，请确保服务器满足以下硬件和软件要求：

- **操作系统 (OS)**: Ubuntu 24.04 LTS
- **硬件配置 (Hardware)**: 8 cores, 16GB+ RAM (32GB recommended), 100GB+ disk
- **Docker Engine**: 已安装并配置中国区镜像加速：
  ```json
  {
    "registry-mirrors": [
      "https://docker.m.daocloud.io",
      "https://hub-mirror.c.163.com",
      "https://mirror.baidubce.com"
    ]
  }
  ```
- **K3s 版本**: v1.34+

---

## 第2部分：K3s 安装与配置 (Section 2: K3s Installation & Configuration)

### 安装 K3s (Install K3s)
使用 Docker 作为容器运行时安装 K3s：
```bash
curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--docker" sh -
```

### 配置 Kubelet 驱逐阈值 (Configure Kubelet Eviction Threshold)
编辑 `/etc/rancher/k3s/config.yaml`，设置磁盘压力驱逐阈值以防止因默认阈值（15%）过高导致 Pod 被意外驱逐：
```yaml
kubelet-arg:
  - "eviction-hard=nodefs.available<10%,imagefs.available<10%"
```

### 重启服务 (Restart Service)
应用配置后重启 K3s：
```bash
sudo systemctl restart k3s
```

---

## 第3部分：命名空间与存储设置 (Section 3: Namespace & Storage Setup)

### 创建命名空间 (Create Namespace)
```bash
sudo kubectl create namespace threat-detection
```

### 持久化卷配置 (Persistent Volume Configuration)
为 PostgreSQL 创建 PV（使用 hostPath `/data/postgres`）：
```yaml
apiVersion: v1
kind: PersistentVolume
metadata:
  name: postgres-pv
spec:
  capacity:
    storage: 10Gi
  accessModes:
    - ReadWriteOnce
  hostPath:
    path: /data/postgres
```

---

## 第4部分：构建与部署所有服务 (Section 4: Build & Deploy All Services)

### 构建顺序 (Build Order)
1. **config-server**: 必须首先构建，因为其他服务依赖其配置。
2. **Java Services**: 使用 Docker 多阶段构建（multi-stage builds）。
3. **Python Services**: TIRE, ML Detection。
4. **Frontend**: 前端界面。

### 镜像导入模式 (Image Import Pattern)
由于 K3s 使用内部镜像仓库，需将本地构建的 Docker 镜像导入到 K3s 运行时：
```bash
sudo docker save <image>:latest | sudo k3s ctr images import -
```

### 应用清单 (Apply Manifests)
使用 Kustomize 或直接应用 YAML 文件：
```bash
sudo kubectl apply -k k8s/base/ -n threat-detection
# 或者
sudo kubectl apply -f <file>.yaml -n threat-detection
```

### 等待 Pod 启动 (Wait for Pods)
```bash
sudo kubectl get pods -n threat-detection -w
```

---

## 第5部分：服务端口参考 (Section 5: Service Port Reference)

| 服务 (Service) | 内部端口 (Internal Port) | 节点端口 (NodePort) |
| :--- | :--- | :--- |
| Frontend / API Gateway | 3000 / 8888 | 30080 |
| Logstash (syslog V1) | 9080 | 32318 |
| EMQX MQTT | 1883 | - |
| Kafka | 9092 | - |
| PostgreSQL | 5432 | - |
| Redis | 6379 | - |

---

## 第6部分：部署验证 (Section 6: Verification)

### 检查 Pod 状态 (Check Pod Status)
```bash
sudo kubectl get pods -n threat-detection
```
**预期结果 (Expected)**: 18 个 Pod 全部处于 `Running 1/1` 状态。

### Pod 列表 (Pod List)
1. alert-management
2. api-gateway
3. config-server
4. customer-management
5. data-ingestion
6. emqx
7. frontend
8. kafka
9. logstash
10. ml-detection
11. postgres-0
12. redis
13. stream-processing-jobmanager
14. stream-processing-taskmanager
15. threat-assessment
16. threat-intelligence
17. tire
18. zookeeper

### 访问 UI (Access UI)
- **URL**: `http://<server-ip>:30080`
- **Login**: `admin` / `admin123`

---

## 第7部分：常用运维操作 (Section 7: Common Operations)

- **重新构建并部署单个服务 (Rebuild & Redeploy Single Service)**:
  ```bash
  # 构建并导入镜像，然后删除 Pod 触发重启
  sudo docker build -t <service>:latest .
  sudo docker save <service>:latest | sudo k3s ctr images import -
  sudo kubectl delete pod -n threat-detection -l app=<name>
  ```
- **查看日志 (View Logs)**:
  ```bash
  sudo kubectl logs -n threat-detection <pod> --tail=100 -f
  ```
- **进入 Pod 终端 (Exec into Pod)**:
  ```bash
  sudo kubectl exec -n threat-detection -it <pod> -- /bin/bash
  ```
- **数据库访问 (Database Access)**:
  ```bash
  sudo kubectl exec -n threat-detection postgres-0 -- psql -U threat_user -d threat_detection
  ```
- **强制重启 Pod (Restart a Pod)**:
  ```bash
  sudo kubectl delete pod -n threat-detection -l app=<name> --force --grace-period=0
  ```

---

## 第8部分：故障排查 FAQ (Section 8: Troubleshooting FAQ)

### FAQ 1: 镜像拉取错误 (ErrImagePull/ImagePullBackOff)
- **原因 (Cause)**: K3s 无法从 Docker Hub 拉取镜像（受限于网络环境），或镜像未正确导入 K3s。
- **修复 (Fix)**: 在本地构建镜像，使用 `sudo docker save ... | sudo k3s ctr images import -` 导入，并在清单中设置 `imagePullPolicy: Never`。

### FAQ 2: Java 兼容性错误 (Java Compatibility Errors)
- **原因 (Cause)**: 使用了 Java 17 基础镜像，但代码要求 Java 21。
- **修复 (Fix)**: 在 Dockerfile 中统一使用 `eclipse-temurin:21-jre-jammy`。

### FAQ 3: Flink 连接器未找到 (Flink Connector Not Found)
- **原因 (Cause)**: Flink 类路径中缺少 `kafka-connector` JAR 包。
- **修复 (Fix)**: 在 Maven shade 插件配置中包含 `flink-connector-kafka`，并验证生成的 fat JAR。

### FAQ 4: YAML 缩进错误 (YAML Indentation Errors)
- **原因 (Cause)**: K8s 清单文件缩进不正确。
- **修复 (Fix)**: 应用前使用 `kubectl apply --dry-run=client -f file.yaml` 进行校验。

### FAQ 5: Kafka 环境变量问题 (Kafka Environment Variable Issues)
- **原因 (Cause)**: KRaft 模式与 ZooKeeper 模式的环境变量名称混淆。
- **修复 (Fix)**: 确保使用正确的变量名，如 `KAFKA_ADVERTISED_LISTENERS`, `KAFKA_LISTENER_SECURITY_PROTOCOL_MAP` 等。

### FAQ 6: PostgreSQL 认证失败 (PostgreSQL Authentication Failed)
- **原因 (Cause)**: 使用了错误的用户名。数据库用户应为 `threat_user` 而非 `postgres`。
- **修复 (Fix)**: 始终使用 `-U threat_user -d threat_detection` 进行连接。

### FAQ 7: 缺失 SQL 表 (Missing SQL Tables)
- **原因 (Cause)**: 初始化 SQL 脚本未执行或执行顺序错误。
- **修复 (Fix)**: 脚本编号为 01-33。如需手动运行：`psql -U threat_user -d threat_detection -c "$(cat file.sql)"`。

### FAQ 8: 磁盘压力污点 (Disk Pressure Taint - Pods Pending/Evicted)
- **原因 (Cause)**: K3s 默认驱逐阈值为 15%，磁盘占用稍高即触发。
- **修复 (Fix)**: 在 `/etc/rancher/k3s/config.yaml` 中将阈值设为 10%，重启 K3s 并删除已驱逐的 Pod。

### FAQ 9: API 路由返回 404 (API Returns 404 for Routes)
- **原因 (Cause)**: Spring Cloud Gateway 配置了服务发现 URI，但 K3s 环境中未部署 Eureka。
- **修复 (Fix)**: 在网关配置中使用直接 HTTP URI：`uri: http://service-name:port`。

### FAQ 10: Kafka 重启后数据丢失 (Kafka Data Lost After Pod Restart)
- **原因 (Cause)**: Kafka 使用了 `emptyDir` 存储，数据是临时性的。
- **修复 (Fix)**: 生产环境应使用基于 hostPath 或云存储的 PVC。开发环境可接受数据丢失。

### FAQ 11: ML 模型未加载 (ML Model Not Loaded)
- **原因 (Cause)**: 容器内缺少 ONNX 模型文件。
- **修复 (Fix)**: 先训练模型（参考 ML 训练指南），模型文件应在构建时打包进 Docker 镜像。

### FAQ 12: docker system prune 删除了 K3s 镜像 (docker system prune Deletes K3s Images)
- **原因 (Cause)**: `docker prune` 会移除所有未使用的镜像，包括已导入 K3s 的镜像。
- **修复 (Fix)**: 严禁在 K3s 节点运行 `docker system prune -a`。请有选择地使用 `docker image prune`。

### FAQ 13: stream-processing 使用 :1.0 标签而非 :latest (stream-processing Uses Tag :1.0 Not :latest)
- **原因 (Cause)**: Flink 作业使用了不同的镜像标签约定。
- **修复 (Fix)**: 构建时指定标签 `threat-detection/stream-processing:1.0` 并导入该特定标签。

### FAQ 14: 部署后出现 502/504 错误 (502/504 Errors After Deploy)
- **原因 (Cause)**: Spring Boot 服务启动通常需要 30-60 秒。
- **修复 (Fix)**: 等待 Pod 状态变为 `READY 1/1`，并检查就绪探针（readiness probes）。

### FAQ 15: BCrypt 密码哈希不匹配 (BCrypt Password Hash Mismatch)
- **原因 (Cause)**: BCrypt 哈希是单向的；“众所周知”的示例哈希不一定对应你的明文密码。
- **修复 (Fix)**: 使用 Python 生成哈希：`python3 -c "import bcrypt; print(bcrypt.hashpw(b'your_password', bcrypt.gensalt()).decode())"`。

### FAQ 16: 通过 stdin 管道传输 SQL 失败 (SQL Piped via stdin Silently Fails)
- **原因 (Cause)**: `echo "SQL" | kubectl exec ... -- psql` 有时会静默失败。
- **修复 (Fix)**: 直接使用 `-c` 参数：`kubectl exec ... -- psql -U threat_user -d threat_detection -c "SQL_HERE"`。
