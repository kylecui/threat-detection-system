# 3.1.18 Docker 运行时一致性与表单弹窗整改

## 现场问题

- 客户端能够到达威胁检测系统主机，但连接 `192.168.2.29:9080` 被拒绝，V1 Syslog 显示未连接。
- Docker API 将 Logstash 标记为 `running`，但其记录的宿主 PID 已不存在，`docker top` 无进程，containerd task 实际为 `STOPPED`。
- 相同异常同时出现在另一个服务；原安装验收和 `tdsdiag` 仅依据 Docker/Compose 表面状态，无法识别这种“幽灵运行容器”。
- 创建/编辑客户弹窗固定为 600px 单列表单，字段较多时比例狭长、必须滚动，较长输入内容也不便核对。

## 修改

- 新增 `scripts/runtime-watchdog.sh`，同时核对 Docker 状态、宿主 PID `/proc` 和 containerd task。
- 新增 systemd watchdog service/timer，每分钟检查一次；只重建状态失同步的 Compose 服务，绝不执行 `down -v`，保留数据库、Logstash持久化队列和其他命名卷。
- Compose 开机启动后执行一次恢复检查；安装器等待服务时也核对真实 PID/containerd 状态。
- `tdsdiag` 将运行时一致性和 `9080/tcp` 实际可连接性列为关键项，诊断包增加有限的 Docker/containerd 状态，不采集秘密和业务数据。
- 安装验收增加 V1 Syslog TCP 监听检查；探针直接连接 Logstash 容器 IP，绕过可能产生假成功的宿主 docker-proxy，避免 Logstash 实际未接入却报告安装成功。
- 创建/编辑客户弹窗调整为 920px 响应式双列表单；常规桌面尽量不滚动，小屏幕保留最大高度和滚动回退，字段宽度统一。

## 安全边界

- 自动恢复仅处理“Docker 报告 running，但 PID 不存在或 containerd task 非 RUNNING”的明确状态失同步。
- 普通 `unhealthy`、业务配置错误或依赖未就绪不会被无限强制重建。
- 自动恢复不会重启 Docker/containerd，不会影响同机其他项目；无法定向恢复时记录失败并由 `tdsdiag` 返回关键故障。
- 卸载和 purge 会停止并删除 watchdog timer/service；命名卷是否删除仍完全遵循原 `--uninstall`/`--purge` 语义。
