# 离线部署指南 (Offline Deployment Guide)

> **适用版本**: v3.1.9+  
> **最后更新**: 2026-04-09

本指南介绍如何在**无外网**环境下，将威胁检测系统部署到一台全新的 K3s 服务器上。

---

## 目录

1. [部署架构](#部署架构)
2. [前置条件](#前置条件)
3. [部署物料清单](#部署物料清单)
4. [一键部署 (推荐)](#一键部署-推荐)
5. [分步部署](#分步部署)
6. [从源码重新构建](#从源码重新构建)
7. [部署验证](#部署验证)
8. [常见问题](#常见问题)

---

## 部署架构

```
                    ┌─────────────────────────────────┐
                    │         目标部署机器              │
                    │  ┌───────────┐  ┌─────────────┐  │
USB/SCP ──────────► │  │ K3s       │  │ Docker      │  │
(images.tar.gz +    │  │ containerd│  │ (构建用)     │  │
 源码)              │  └─────┬─────┘  └──────┬──────┘  │
                    │        │               │         │
                    │    k3s ctr import   docker build  │
                    │        │               │         │
                    │        ▼               ▼         │
                    │  ┌───────────────────────────┐   │
                    │  │   K8s Pods (18个)          │   │
                    │  │   7 基础设施 + 11 应用服务  │   │
                    │  └───────────────────────────┘   │
                    └─────────────────────────────────┘
```

## 前置条件

### 硬件要求

| 项目 | 最低配置 | 推荐配置 |
|------|---------|---------|
| CPU  | 4 核    | 8 核    |
| 内存 | 16 GB   | 32 GB   |
| 磁盘 | 60 GB 可用 | 100 GB 可用 |

### 软件要求

| 软件 | 版本 | 说明 |
|------|------|------|
| 操作系统 | Ubuntu 22.04 / 24.04 LTS | 其他 Linux 发行版也可 |
| K3s | v1.25+ | 轻量级 Kubernetes |
| Docker | 20.10+ | 仅从源码构建时需要 |

### 安装 K3s (国内源)

```bash
# 使用国内镜像安装 K3s
curl -sfL https://rancher.cn/k3s/k3s-install.sh | INSTALL_K3S_MIRROR=cn sh -

# 验证安装
sudo kubectl get nodes
# 输出: NAME   STATUS   ROLES                  AGE   VERSION
#        xxx    Ready    control-plane,master   1m    v1.xx.x+k3s1
```

### 安装 Docker (仅从源码构建时需要)

```bash
# 如需从源码重新构建应用镜像才安装 Docker
sudo apt-get update
sudo apt-get install -y docker.io
sudo systemctl enable docker && sudo systemctl start docker
```

---

## 部署物料清单

在**有网络**的机器上准备以下文件：

| 文件 | 大小 | 说明 |
|------|------|------|
| `threat-detection-images.tar.gz` | ~4-6 GB | 所有容器镜像 (运行时 + 构建时基础镜像) |
| 项目源码目录 | ~200 MB | `git clone` 或 zip 打包 |

### 生成镜像包

在有网络的机器 (或当前 lab 服务器) 上执行：

```bash
# 导出全部镜像 (运行时 + 构建时基础镜像)
sudo bash scripts/k3s-export-images.sh

# 仅导出运行时镜像 (不含构建时基础镜像，体积更小)
sudo bash scripts/k3s-export-images.sh --runtime-only

# 输出: threat-detection-images.tar.gz
```

### 传输到目标机器

```bash
# 方式一: SCP (内网传输)
scp threat-detection-images.tar.gz user@目标机器:/home/user/

# 方式二: USB 拷贝
cp threat-detection-images.tar.gz /media/usb/

# 方式三: rsync (大文件更可靠，支持断点续传)
rsync -avP threat-detection-images.tar.gz user@目标机器:/home/user/
```

---

## 一键部署 (推荐)

将镜像包放到项目根目录下，执行：

```bash
cd threat-detection-system

# 将镜像包放到项目根目录
cp /path/to/threat-detection-images.tar.gz .

# 一键部署
sudo bash scripts/k3s-offline-deploy.sh
```

整个过程约 5-10 分钟，脚本会自动：
1. 导入所有镜像到 K3s containerd
2. 按正确顺序部署 18 个 Pod
3. 等待所有服务就绪
4. 输出验证信息

### 高级选项

```bash
# 指定镜像包路径
sudo bash scripts/k3s-offline-deploy.sh --archive /data/images.tar.gz

# 从源码重新构建应用镜像后部署 (需要 Docker)
sudo bash scripts/k3s-offline-deploy.sh --rebuild

# 镜像已导入，只执行 K8s 部署
sudo bash scripts/k3s-offline-deploy.sh --skip-import
```

---

## 分步部署

如果一键部署遇到问题，可以分步排查：

### Step 1: 导入镜像

```bash
sudo bash scripts/k3s-import-images.sh threat-detection-images.tar.gz
```

验证镜像已导入：
```bash
sudo crictl images | grep -v rancher
```

应看到 ~27 个镜像 (7 基础设施 + 11 应用 + 9 构建基础镜像)。

### Step 2: (可选) 从源码构建应用镜像

如果需要修改代码后重新构建：

```bash
# 构建全部 11 个应用镜像
sudo bash scripts/k3s-build-images.sh

# 构建指定服务
sudo bash scripts/k3s-build-images.sh alert-management api-gateway
```

> **注意**: 构建时基础镜像 (maven, python, node 等) 已从镜像包导入到 Docker，因此构建过程不需要联网拉取基础镜像。Maven/pip/npm 依赖缓存已内置在 Dockerfile 中 (使用国内镜像源)。

### Step 3: 部署到 K8s

```bash
sudo bash scripts/k3s-deploy.sh
```

部署脚本会按以下顺序启动服务：

| 阶段 | 服务 | 说明 |
|------|------|------|
| 0a | 镜像预检 | 检查所有必需镜像是否存在 |
| 0b | 清理旧部署 | 删除旧的 threat-detection namespace |
| 1 | Namespace + PG | 创建命名空间，部署 PostgreSQL |
| 2 | 基础设施 | Redis, Zookeeper, EMQX |
| 3 | Kafka | 依赖 Zookeeper |
| 4 | 初始化任务 | Kafka Topic 创建, PG Schema 初始化 |
| 5 | 核心服务 | Config Server, Logstash |
| 6 | 应用服务 | 9 个业务微服务 + 前端 |
| 7 | Flink | 流处理 (JobManager + TaskManager) |
| 8 | 状态检查 | 输出所有 Pod 状态 |

---

## 从源码重新构建

构建流程已做以下**国内源优化**，即使网络不佳也能顺利构建：

| 技术栈 | 国内源 | 配置位置 |
|--------|--------|---------|
| Maven | maven.aliyun.com | `maven-settings.xml` (项目根目录) |
| npm | registry.npmmirror.com | `frontend/Dockerfile` |
| pip | mirrors.aliyun.com | `services/ml-detection/Dockerfile`, `services/tire/Dockerfile` |

### 完整重建流程

```bash
# 1. 导入构建基础镜像 (从镜像包)
sudo bash scripts/k3s-import-images.sh threat-detection-images.tar.gz

# 2. 构建全部应用镜像
sudo bash scripts/k3s-build-images.sh

# 3. 部署
sudo bash scripts/k3s-deploy.sh
```

或使用一键命令：
```bash
sudo bash scripts/k3s-offline-deploy.sh --rebuild
```

### 单个服务重建

修改代码后，只需重建受影响的服务：

```bash
# 重建单个服务
sudo bash scripts/k3s-build-images.sh alert-management

# 滚动重启 (Pod 会使用新镜像)
sudo kubectl rollout restart deployment/alert-management -n threat-detection

# 等待就绪
sudo kubectl rollout status deployment/alert-management -n threat-detection
```

---

## 部署验证

### 1. Pod 状态检查

```bash
sudo kubectl get pods -n threat-detection
```

预期输出: 18 个 Pod 全部 `1/1 Running`，2 个 Job `Completed`。

```
NAME                                    READY   STATUS      RESTARTS   AGE
alert-management-xxx                    1/1     Running     0          5m
api-gateway-xxx                         1/1     Running     0          5m
config-server-xxx                       1/1     Running     0          6m
customer-management-xxx                 1/1     Running     0          5m
data-ingestion-xxx                      1/1     Running     0          5m
emqx-0                                 1/1     Running     0          8m
frontend-xxx                           1/1     Running     0          5m
kafka-0                                1/1     Running     0          7m
logstash-xxx                           1/1     Running     0          6m
ml-detection-xxx                       1/1     Running     0          5m
postgres-0                             1/1     Running     0          8m
redis-xxx                              1/1     Running     0          8m
stream-processing-jobmanager-xxx       1/1     Running     0          4m
stream-processing-taskmanager-xxx      1/1     Running     0          4m
threat-assessment-xxx                  1/1     Running     0          5m
threat-intelligence-xxx                1/1     Running     0          5m
tire-xxx                               1/1     Running     0          5m
zookeeper-0                            1/1     Running     0          8m
kafka-topic-init-manual-xxx            0/1     Completed   0          7m
postgres-schema-apply-xxx              0/1     Completed   0          7m
```

### 2. Web 界面访问

打开浏览器访问: `http://<服务器IP>:30080`

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | admin123 | 超级管理员 |
| demo_admin | admin123 | 租户管理员 |

### 3. 数据流测试

#### V1 Syslog 测试

```bash
TIMESTAMP=$(date +%s) && echo "syslog_version=1.0,dev_serial=9d262111f2476d34,log_type=1,sub_type=4,attack_mac=aa:bb:cc:dd:ee:10,attack_ip=192.168.1.50,response_ip=10.0.1.1,response_port=3389,line_id=1,Iface_type=1,Vlan_id=0,log_time=${TIMESTAMP}" | nc -q1 localhost 32318
```

#### V2 MQTT 测试 (需要 mosquitto_pub)

```bash
# 安装 mosquitto 客户端
sudo apt-get install -y mosquitto-clients

# 发送测试数据
TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%S+00:00) && mosquitto_pub \
  -h localhost -p 31883 \
  -t 'jz/9d262111f2476d34/logs/attack' \
  -m "{\"v\":2,\"device_id\":\"9d262111f2476d34\",\"seq\":2001,\"ts\":\"${TIMESTAMP}\",\"type\":\"attack\",\"data\":{\"src_ip\":\"10.0.1.200\",\"src_mac\":\"aa:bb:cc:11:22:33\",\"guard_ip\":\"10.0.1.50\",\"dst_port\":8080,\"ifindex\":1,\"vlan_id\":100,\"ethertype\":2048,\"ip_proto\":6}}"
```

发送后，在 Web 界面的 **Dashboard** 和 **Alerts** 页面应能看到新产生的告警。

---

## 常见问题

### Q: 镜像导入后 Pod 仍然 ImagePullBackOff？

**A**: K8s 默认会尝试从远程拉取镜像。本系统所有自建镜像已设置 `imagePullPolicy: Never`，第三方镜像设置为 `IfNotPresent`。如果仍出现此错误：

```bash
# 检查镜像是否确实存在
sudo crictl images | grep <镜像名>

# 手动导入单个镜像
docker save <镜像名> -o /tmp/img.tar && sudo k3s ctr images import /tmp/img.tar
```

### Q: 磁盘空间不足怎么办？

**A**: 
```bash
# 查看磁盘使用
df -h

# 清理 Docker 缓存 (释放构建缓存)
sudo docker system prune -af

# 清理已完成的 Job Pod
sudo kubectl delete pods -n threat-detection --field-selector status.phase=Succeeded

# 清理 K3s 不再使用的镜像
sudo k3s crictl rmi --prune
```

### Q: 某个 Pod 一直 CrashLoopBackOff？

**A**:
```bash
# 查看 Pod 日志
sudo kubectl logs -n threat-detection <pod名> --tail=50

# 查看 Pod 事件
sudo kubectl describe pod -n threat-detection <pod名>

# 常见原因:
# - Config Server 未就绪 → 等待或手动重启: kubectl rollout restart deployment/<服务名> -n threat-detection
# - 数据库连接失败 → 检查 PostgreSQL Pod 是否 Running
# - Kafka 未就绪 → 检查 Kafka 和 Zookeeper 状态
```

### Q: 如何更新单个服务？

**A**:
```bash
# 修改代码后
sudo bash scripts/k3s-build-images.sh <服务名>
sudo kubectl rollout restart deployment/<服务名> -n threat-detection
```

### Q: K3s 安装失败 (国内网络)？

**A**:
```bash
# 方法一: 使用国内安装脚本
curl -sfL https://rancher.cn/k3s/k3s-install.sh | INSTALL_K3S_MIRROR=cn sh -

# 方法二: 离线安装
# 1. 在有网络的机器下载 k3s 二进制
wget https://github.com/k3s-io/k3s/releases/download/v1.30.0+k3s1/k3s
# 2. 拷贝到目标机器
chmod +x k3s && sudo mv k3s /usr/local/bin/
# 3. 安装
curl -sfL https://get.k3s.io | INSTALL_K3S_SKIP_DOWNLOAD=true sh -
```

### Q: 部署过程中断了，如何恢复？

**A**: 部署脚本是幂等的，可以直接重新执行：
```bash
# 重新执行部署 (会清理旧部署)
sudo bash scripts/k3s-deploy.sh

# 或跳过镜像导入直接部署
sudo bash scripts/k3s-offline-deploy.sh --skip-import
```

---

## 联系方式

遇到部署问题请联系开发团队，并附上以下信息：
```bash
# 收集诊断信息
sudo kubectl get pods -n threat-detection -o wide
sudo kubectl get events -n threat-detection --sort-by='.lastTimestamp' | tail -20
sudo df -h
sudo free -h
```
