# 3.1.7 标准发布与安装整改

日期：2026-09-15

## 新增

- `VERSION`、`.env.example`、根 `install.sh`。
- `scripts/install.sh`、`prepare_release_env.py`、`tdsdiag.sh`、`pack_release.py`。
- `deploy/systemd/threat-detection-compose.service`。
- `tests/test_release_contract.py`。
- `AI_PROJECT/` 和 `outputs/v3.1.7/` 标准结构。

## 修改

- Compose 使用安装器生成的 PostgreSQL 密码和 JWT 密钥。
- Stream Processing 显式接收 DB URL、用户和密码。
- 挂载 39–41 号数据库初始化脚本。
- 去除固定容器名；本地构建镜像使用项目名和版本标签。
- Java 生产镜像构建不再依赖被排除的测试源码。
- Java 镜像启用国内 Maven 源时先创建 `/root/.m2`，避免全新构建机复制 settings 失败。
- ML 镜像启用国内源时同时切换 Debian APT 和 PyPI 到阿里云镜像，避免 APT 阶段长期停滞。
- ML 镜像改用 `requirements-runtime.txt` 和固定的 PyTorch 2.2.2 CPU wheel，不再安装 pytest/httpx 等开发测试依赖。
- 安装器显式设置 `COMPOSE_BAKE=false`，不再因可选 buildx 缺失产生 Bake warning。
- 正式安装目录固定为 `/opt/threat-detection-system`，移除与 systemd/诊断路径契约不一致的自定义 `--target` 参数。
- 升级时强制采用新包的 `RELEASE_VERSION` 和新构建时间，同时继续保留已生成秘密与操作员配置。
- README 统一为发布包校验、解压和根安装入口。

## 安全与兼容性

- `.env` 权限为 `0600`，不进入包或诊断包。
- 升级保留已有秘密和 named volumes。
- 失败回滚不采集应用负载日志或秘密内容。
- EMQX 匿名 1883、默认管理员和已有数据卷迁移限制已登记为后续问题，没有在本次越界改变协议行为。
