# INTERACTION-010 Ubuntu 复验与打包记录

日期：2026-10-28  
构建机：`jzzn@192.168.100.9`（Ubuntu 24.04 x86_64）  
源码版本：`3.1.38`

## 已完成验证

- Ubuntu 前端 lint、两项类型检查、61 项 Vitest 和生产构建全部通过。
- `tests.test_e2e_runtime_scripts` 在同时具备 Bash、Python 3 和 Node 22 的环境中 15/15 通过。
- Interaction、表格和 E2E 运行脚本契约合计 55/55 通过，无跳过。
- 发布契约 24/24 通过。
- 3.1.38 正式归档已生成、校验并回传本机 `outputs/v3.1.38/`。

## 初次复验时尚未完成的真实浏览器验收

目标镜像为 `mcr.microsoft.com/playwright:v1.59.1-noble`。构建机清理项目旧临时目录及未使用的旧版项目构建镜像后已有约 5.1 GiB 可用空间，满足脚本最低要求，但镜像仍未取得：

1. 首次拉取下载镜像层时，连接官方 CDN `150.171.70.10:443` 被对端重置。
2. 后续有限重试持续 360 秒未完成。
3. 最终使用 `docker image inspect` 检查，固定镜像明确不存在。

因此以下命令没有执行，也没有被其他浏览器版本或单 worker 结果替代：

- Overview `--workers=4 --repeat-each=3` 定向回归；
- responsive-layout、overview-trend、table-interactions、api-mocks 组合回归；
- 完整 4-worker Playwright 回归。

## 初次复验结论

010 的代码同步方案和运行脚本契约已经验证，3.1.38 也已按用户要求打包；但 Ubuntu 固定浏览器环境的真实 4-worker 验收仍未完成，不能仅凭本次打包将 `INTERACTION-010` 标记为完全关闭。后续需要从可联网的 x86_64 Linux 机器按 `prepare_playwright_image.sh save` 生成离线镜像归档，传入构建机后执行 `load` 和三组回归。

正式包信息：

- `threat-detection-system-3.1.38-linux-x86_64.tar.gz`
- 887,128 字节
- SHA-256：`87ee1e1d15ab9e92b719c732e280570b44f234d12a2efd314fa8fbcfa3f03959`

完整记录见：`AI_PROJECT/records/tests/2026-10-28-release-3.1.38.md`。

## 后续：固定镜像已离线导入

2026-10-28 用户提供了 `playwright-v1.59.1-noble.tar`，已通过 SSH 流式导入 `192.168.100.9`：

- 本地 tar 大小：913,926,144 字节。
- 本地 tar SHA-256：`7c39cf4bcb66b054dabfa1eed415ff8b233de1e4e6dd71e8936b8cc8e27f0386`。
- 镜像标签：`mcr.microsoft.com/playwright:v1.59.1-noble`。
- 镜像 ID：`sha256:b0ab6f3cb99aa7803adbc14d9027ec1785fc6e433b97e134e0f8fe61683b6b53`。
- 平台：Linux amd64。
- 一次性容器验证存在 `chromium-1217`、`chromium_headless_shell-1217`，与项目锁定版本一致。
- 构建机导入后已保留按脚本计算不少于 4 GiB 的可用空间；没有删除 Docker 数据卷或 Flink/Kafka/PostgreSQL/EMQX 等运行镜像。

这解决了“Ubuntu 浏览器运行时缺失”问题，但 Overview 定向、组合和完整 4-worker 回归仍需实际执行并保存报告后，才能更新本问题最终验收结论。

## 后续：三组真实 4-worker 回归已执行

2026-10-29 UTC 已使用上述固定镜像在 `192.168.100.9` 实际完成三组回归：

- Overview 定向重复：5/9 通过、4 失败。
- 四个关键 spec 组合：24/30 通过、6 失败。
- 完整回归：119/163 通过、44 失败。

三次运行的 `exit_status` 均为 1。报告显示浏览器运行时已正常工作，但 Overview 冷启动同步、多个页面的数据就绪等待、390px Overview 移动卡片契约和 StrictMode `findDOMNode` 控制台警告门禁仍有问题。因此 `INTERACTION-010` 的最终结论更新为 **不通过，不能关闭**。

详细失败分类、报告路径、结果文件哈希和整改建议见同目录：`04_Ubuntu真实Playwright复验报告.md`。三份完整报告已回传本机 `outputs/e2e-reports/20261029T020727Z/`、`20261029T020944Z/`、`20261029T021345Z/`。
