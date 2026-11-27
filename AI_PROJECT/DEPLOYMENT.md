# 正式部署

## 安装

以下为 3.1.39 安装示例；执行前确认 `records/tests/2026-10-30-release-3.1.39.md` 中的正式交付状态。本次构建不执行生产升级。

```bash
sha256sum -c SHA256SUMS
tar -xzf threat-detection-system-3.1.39-linux-x86_64.tar.gz
cd threat-detection-system-3.1.39-linux-x86_64
sudo ./install.sh
```

根 `install.sh` 是发布包中唯一的安装脚本，并直接包含完整安装、升级、回滚、卸载和 purge 实现；部署操作不需要进入 `scripts/`。

禁止从源码树直接部署，也不要求操作员复制 `.env.example`。真实 `.env` 自动生成并保存到 `/opt/threat-detection-system/.env`。

若目标机没有 Docker，安装器会安装 Docker，并安全写入三个国内 Docker Hub 镜像源；已有 `daemon.json` 会先备份并合并其他配置。若目标机在运行安装器前已经安装 Docker，正常拉取时不显示镜像源提示；只有基础镜像经过全部重试仍失败后，安装器才输出可复制执行的镜像源切换命令，并且仍不会自行修改全局 Docker 配置或重启 Docker。镜像源配置属于宿主机全局状态，`--purge` 不删除。

`flink:1.18.1-scala_2.12-java17` 是已知可能被部分 Docker Hub 加速器卡住的例外。安装器在每轮官方标签失败后会自动拉取 DaoCloud 完整地址，并验证 RepoDigest 为发布固定摘要；校验通过后重新标记为 Compose 使用的官方标签并继续本次安装。无需人工预拉取，也无需再次执行安装脚本。仅在企业内网需要替换直连地址时才设置 `FLINK_FALLBACK_IMAGE`，替代仓库必须提供完全相同的镜像摘要，否则安装器拒绝使用。

安装器会在镜像拉取和安装事务开始前验证 Compose 发布端口。默认 `8083/tcp` 属于 Threat Assessment，EMQX WebSocket 使用宿主 `18084/tcp`；如端口在配置内重复，或已被非本项目容器/宿主进程占用，安装器会列出冲突并退出，不停止现有项目服务。

Alert Management `8082` 和 Customer Management `8084` 只通过 Compose `expose` 提供给容器内网，不发布到宿主机。运维和前端调用必须走 `8888` API Gateway；网关及 Customer Management 到通知配置接口的服务间调用使用安装器生成的 `ALERT_INTERNAL_SERVICE_TOKEN`，不得在浏览器、命令输出或日志中暴露该值。

## 验证

```bash
sudo systemctl is-active threat-detection-compose.service
sudo systemctl is-enabled threat-detection-compose.service
sudo tdsdiag --self-test
sudo systemctl status threat-detection-runtime-watchdog.timer --no-pager
sudo /opt/threat-detection-system/scripts/runtime-watchdog.sh --check
cd /opt/threat-detection-system
sudo docker compose --env-file .env -f docker/docker-compose.yml ps
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8888/actuator/health
timeout 5 bash -c 'exec 3<>/dev/tcp/127.0.0.1/9080'
curl -fsS http://127.0.0.1:8888/api/v1/system/readiness
curl -fsS http://127.0.0.1:8888/api/v1/auth/bootstrap/status
curl -fsS http://127.0.0.1:3000/
ss -ltn | grep -E ':(8083|18084)\\b'
```

全新安装的 Web 登录页会要求创建首个超级管理员；系统不再提供默认账号密码。安装器会验证用户、客户、设备、攻击、告警和评估业务表为空，角色、标签、权重等系统字典不计入业务数据。

## 客户端数据服务器配置

- HTTP 接入：`http://<server-ip>:8080`
- V1 Syslog：`<server-ip>:9080/tcp`
- V2 MQTT：`<server-ip>:1883`

具体选择取决于客户端当前 uploadd/日志协议配置，不能把控制面 Broker 的 `8883` 直接当成本平台数据入口。

## 升级与回滚

新包解压后再次运行根 `install.sh`。安装器读取 `.installed_version`，旧安装缺失该标记时回退读取安装目录内的 `VERSION`；确认目标版本较新后自动覆盖应用文件，不要求先卸载或确认。安装器备份旧文件、保留 `.env` 和 named volumes；关键步骤失败时自动恢复旧文件并重新启动旧 stack。持久数据库结构升级仍受 `ISSUES.md` 中迁移限制约束。

正常覆盖升级只执行 `sudo ./install.sh`。不得把 `--purge --yes` 作为升级前置步骤；该参数仅用于永久清空配置、凭据、数据卷和备份。

## 清除

```bash
sudo /opt/threat-detection-system/install.sh --purge --dry-run
sudo /opt/threat-detection-system/install.sh --purge
```

`--purge` 删除本项目容器、镜像、卷、数据库、`.env`、生成秘密、systemd unit、安装文件和备份；APT/Docker 及操作员上传的发布包保留。
