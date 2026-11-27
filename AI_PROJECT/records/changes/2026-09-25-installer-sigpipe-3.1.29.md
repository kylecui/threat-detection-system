# 3.1.29 安装验收 SIGPIPE 修复

## 发现的问题

3.1.28 在多容器主机完成镜像构建并通过 systemd 启动 Compose 后，安装器仍返回 `rc=141`，随后把一次实际成功的启动误判为安装失败并执行回滚。

## 找到的原因

安装器和运行时 watchdog 使用 `ctr -n moby tasks list | awk` 核对 containerd task。旧逻辑在找到目标容器后立即 `exit`；当主机任务较多、`ctr` 仍在写入时，管道读端提前关闭，`ctr` 收到 `SIGPIPE` 并返回 141。脚本启用了 `set -o pipefail`，因此整个命令替换失败并进入事务回滚路径。Compose、systemd 和业务容器本身并未失败。

## 修改内容

- 安装器和 watchdog 的任务匹配器改为完整读取列表，只记录目标行，读取结束后再输出状态。
- 保留 Docker 状态、PID、`/proc`、containerd task 和容器健康检查的原有严格验收。
- 新增 4096 条模拟 task 输出的 `pipefail` 回归测试，并静态禁止旧的 `print ...; exit` 写法。
- 发布版本升级为 3.1.29，统一 `VERSION`、`.env.example`、Compose 默认镜像标签和发布文档。

## 影响边界

本次不修改业务服务、数据库结构、消息接入、前端、端口和数据协议；只修复安装验收及运行时 watchdog 的状态读取方式。
