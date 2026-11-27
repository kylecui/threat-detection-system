# 3.1.10 Docker 国内镜像配置策略

## 背景

3.1.9 已将 14 个基础/运行时镜像改为事务前顺序预拉取，但全新目标机仍因 Docker Hub 解析或访问超时，在第一个 `postgres:15-alpine` 镜像处失败。APT 国内源可用不代表 Docker daemon 能访问 Docker Hub。

## 修改

- 安装器启动时记录 Docker 是否已存在，避免安装依赖后丢失环境来源信息。
- 若 Docker 由本次安装器新装，自动配置以下镜像：
  - `https://docker.xuanyuan.me`
  - `https://docker.1ms.run`
  - `https://docker.m.daocloud.io`
- 自动配置采用 JSON 合并，保留已有合法 daemon 选项；修改前创建时间戳备份，写入后执行 `dockerd --validate`。
- Docker 重启失败时恢复原配置；配置不合法时不替换原文件。
- 若 Docker 在安装前已存在，安装器不修改全局配置、不重启 Docker，只输出可复制执行的备份、写入、校验、重启和拉取验证命令。
- 预拉取失败时明确提示已有 Docker 环境先应用前述镜像源命令再重试。

## 不变范围

- 不修改 Compose 业务拓扑、服务源码、数据库结构、端口、数据入口和自动生成秘密。
- 镜像预拉取仍发生在旧安装停止和文件同步之前。
- `--purge` 不卸载 Docker，也不删除宿主机全局 `daemon.json`。
