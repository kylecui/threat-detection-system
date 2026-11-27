# 版本发布与打包流程

## 输出结构

```text
outputs/v<version>/
├── threat-detection-system-<version>-linux-x86_64.tar.gz
├── SHA256SUMS
├── PACKAGE_CONTENTS.txt
└── RELEASE_NOTES.md
```

归档只允许一个顶层目录 `threat-detection-system-<version>-linux-x86_64/`。

## 包含范围

- 根 `install.sh`、`VERSION`、`.env.example`、README 和 `MANIFEST.sha256`。
- Docker Compose 及其实际挂载的数据库初始化 SQL。
- Java/Python 服务、前端、Logstash 和 config-repo 的生产构建源码。
- systemd unit、自动环境生成器和 `tdsdiag`。

## 排除范围

- `.env`、证书、私钥、数据库、日志、备份和真实凭据。
- `AI_PROJECT/`、`outputs/`、`docs/`、`k8s/`。
- 测试/e2e、`node_modules`、`target`、`dist`、缓存和历史 `.bak/.old/.NOUSE` 文件。
- 一次性远程脚本、截图和开发辅助资料。

## 构建与验证

正式版本在 `192.168.100.9` 构建。每次发布前先把 `VERSION` 升到新版本号，不得覆盖已经存在的 `outputs/v<version>/` 归档：

执行顺序：先完成下文全部前端质量门禁，再运行以下发布契约/集成测试与打包。`test_release_contract.py`、PostgreSQL 和 EMQX 测试会调用打包器生成候选归档，不能用这些测试绕过前端门禁。新版本在隔离构建目录验证期间可重建同一个未交付候选包；已交付归档不得覆盖。

共享构建机运行 PostgreSQL/EMQX 测试时，除测试自身的唯一 Compose project，还应设置唯一 `TDS_DOCKER_NETWORK`，避免使用生产网络。不在共享机执行正式 `install.sh`（包括会停止已有安装的 `--no-start`）来代替隔离环境安装验收。未执行的完整安装或外部联通测试必须在发布记录明确披露。

```bash
VERSION=$(cat VERSION)
python3 -m unittest tests/test_release_contract.py
TDS_DOCKER_NETWORK="tds-pg-check-$$" TDS_RUN_POSTGRES_INIT_TEST=1 python3 -m unittest tests/test_postgres_init.py
TDS_DOCKER_NETWORK="tds-emqx-check-$$" TDS_RUN_EMQX_HEALTH_TEST=1 python3 -m unittest tests/test_emqx_health.py
python3 scripts/pack_release.py
cd outputs/v$VERSION
sha256sum -c SHA256SUMS
gzip -t threat-detection-system-$VERSION-linux-x86_64.tar.gz
```

随后解压临时副本，验证包内 manifest、仅存在根目录一个 `install.sh`、脚本执行权限、shell 语法、自动环境生成、Compose 配置、发布端口唯一性、数据库迁移账本、Docker/containerd 运行时一致性、V1 Syslog 监听、空卷 PostgreSQL 初始化链、空业务数据、EMQX 单机健康探针、业务 readiness、客户分页预热、Kafka 分区和容器稳定窗口，并使用包内源码完成 Docker 构建和安装验收。最终仅将 `outputs/v<version>/` 下载回本地。

## 前端质量门禁（打包前必须通过）

INTERACTION-009/010 起，前端门禁在打包前执行，任何一项失败都不得打包：

```bash
cd frontend
npm ci
npm run lint            # 0 error、0 warning（--max-warnings 0）
npx tsc --noEmit
npm run typecheck:e2e
npm test
npm run build
cd ..
scripts/testing/run_frontend_e2e_ubuntu.sh            # 完整 E2E，默认 4 workers
```

浏览器 E2E 在固定版本的官方 Playwright 镜像中运行，镜像版本必须与 `frontend/package-lock.json` 锁定的 Playwright 一致（当前 `mcr.microsoft.com/playwright:v1.59.1-noble`），不在构建机上在线下载浏览器：

1. 在可联网的 x86_64 Linux 机器执行 `scripts/testing/prepare_playwright_image.sh save <目录>`，生成 `playwright-v<版本>-noble.tar.gz`、`.sha256` 和 `.meta`。
2. 将三个文件放到构建机 `/home/jzzn/e2e-images/`（不得放入 `outputs/v<version>/` 或生产归档）。
3. 构建机执行 `scripts/testing/prepare_playwright_image.sh load`：先检查磁盘余量（默认至少 4 GiB），再校验 SHA256、`docker load` 并核对镜像 ID。脚本不会自动 prune 或删除任何镜像、卷和目录。
4. `scripts/testing/run_frontend_e2e_ubuntu.sh [Playwright 参数]` 在容器内按锁文件安装依赖、核对浏览器 revision 后运行测试。未指定 `--workers` 时为 4；容器内不设置 `CI`，因此不会出现 CI 单 worker 或自动重试。报告写入 `outputs/e2e-reports/<UTC 时间>/`（`run-info.txt` 含镜像 ID 和退出码），脚本退出码即测试结果。
5. 构建机访问官方 npm 源受限时，设置 `TDS_E2E_NPM_REGISTRY=<可达的源>`。
