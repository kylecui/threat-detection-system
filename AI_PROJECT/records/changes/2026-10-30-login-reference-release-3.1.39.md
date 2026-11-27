# 3.1.39 登录参考样式与发布交付

日期：2026-10-30（Asia/Shanghai）。来源：用户要求完成 018/019 复查、更新文档并在 `192.168.100.9` 构建打包回传。

## 实际变更

- 017：浅灰蓝背景，浅蓝品牌区/白色表单双栏，桌面约 780px、53/47 比例，窄屏上下布局。保持登录、首次管理员创建、检查失败重试、会话到期与主题偏好边界。
- 018：`frontend/src/pages/Login/index.tsx` 局部 `colorPrimaryHover` 为 `#066b84`；不修改全局工作台色板。
- 019：同组件显隐键盘处理先阻止默认行为，再忽略 `event.repeat`；初始 Space/Enter 仍走原点击与焦点恢复。
- 版本元数据：`VERSION`、`.env.example`、`frontend/.env.production`、Compose 的本项目镜像及 Vite 默认版本均为 `3.1.39`；不改第三方依赖版本。
- 打包器原白名单遗漏了前端同目录测试，补充排除 `__tests__`、`.test.ts(x)`、`.spec.ts(x)`，对应更新 `tests/test_release_contract.py`。构建/验证用开发快照仍保留测试，生产包才排除。
- 更新 README、项目主索引/概况/需求/部署/开发日志/问题清单、用户手册及发布流程；历史 017–019 阶段文档与原证据不改写。

## 兼容性与边界

018/019 生产修改总共两行；此前独立复核认证、路由、API 和主题等十个兼容文件与原交付一致。本轮只另改发布元数据和打包范围，不改变业务逻辑、权限、接口或安装器逻辑。此次授权为构建、打包与回传，不包括生产升级，不承诺未执行的全栈安装验证。

## 文件范围清单

| 类型 | 文件或证据目录 |
| --- | --- |
| 017–019 生产视觉/交互（与 3.1.38 比较） | `frontend/src/pages/Login/index.tsx`、`frontend/src/index.css`、`frontend/src/i18n/locales/zh-CN.json`、`en-US.json` |
| 发布版本/包内说明 | `VERSION`、`.env.example`、`frontend/.env.production`、`docker/docker-compose.yml`、`README.md` |
| 打包范围与契约 | `scripts/pack_release.py`、`tests/test_release_contract.py` |
| 登录验证/复查 | `frontend/e2e/login.spec.ts`、`login-contrast.spec.ts`、`login-key-repeat.spec.ts`；018/019 的 `03_复查验收报告.md` 和 `evidence/03-review/` |
| 当前研发说明 | `AI_PROJECT/INDEX.md`、`PROJECT.md`、`DEPLOYMENT.md`、`REQUIREMENTS.md`、`DEVELOPMENT_LOG.md`、`ISSUES.md`、`RELEASE_PROCESS.md`；`docs/USER_MANUAL.md` |
| 新建记录 | 本文、`records/tests/2026-10-30-release-3.1.39.md` |
| 构建/回传证据（不进生产包） | `outputs/build-reports/v3.1.39/`，含快照、两轮构建、Ubuntu 原始报告、辅助核验脚本和本地校验结果 |
| 正式交付 | `outputs/v3.1.39/` 下 tar.gz、`SHA256SUMS`、`PACKAGE_CONTENTS.txt`、`RELEASE_NOTES.md` |

上述清单包含本版本纳入的 017–019 既有修复和本轮发布元数据/文档调整，不将既有修复冒称为本轮重新修改。相较上一包没有后端源码或安装器变更。

## 关联记录

- `Interaction/017_登录页与客户端双栏参考样式不一致/01_问题分析与修复方案.md`、`02_修复结果.md`、`03_复查验收报告.md`（历史原结论保留）。
- `Interaction/018_登录主按钮悬停文字对比度不足/03_复查验收报告.md`：通过。
- `Interaction/019_密码显隐按钮长按空格重复切换/03_复查验收报告.md`：通过。
- 本次实际构建与交付：`../tests/2026-10-30-release-3.1.39.md`。
