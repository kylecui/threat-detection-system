# INTERACTION-009 Ubuntu 复验与打包记录

日期：2026-10-28  
构建机：`jzzn@192.168.100.9`（Ubuntu 24.04 x86_64）  
源码版本：`3.1.38`

## 复验结论

ConfigCascading 测试 Harness 的组件模块与启动入口拆分已经消除原始 Fast Refresh warning：

- Windows 正式 `npm run lint`：通过，0 warning。
- Ubuntu Node 22 干净依赖环境 `npm run lint`：通过，0 warning。
- Ubuntu `npx tsc --noEmit`：通过。
- Ubuntu `npm run typecheck:e2e`：通过。
- Ubuntu Vitest：16 个文件、61 项测试通过。
- Ubuntu生产构建：通过。
- Interaction、表格和 E2E 运行脚本契约：55/55 通过，无跳过。

没有修改 ESLint 规则、`--max-warnings 0`、ignore 或生产路由。ConfigCascading 仍然只保留源码和测试 Harness，没有开放生产入口。

## 打包结果

3.1.38 已在同一 Ubuntu 隔离目录完成发布契约和正式打包，产物回传到 `outputs/v3.1.38/`：

- 归档：`threat-detection-system-3.1.38-linux-x86_64.tar.gz`
- 大小：887,128 字节
- SHA-256：`87ee1e1d15ab9e92b719c732e280570b44f234d12a2efd314fa8fbcfa3f03959`

Harness 位于 `frontend/e2e/`，正式归档检查确认不包含该目录，因此本修复没有扩大生产包内容。

## 关联记录

完整构建环境、命令结果、归档验证和未执行项见：

`AI_PROJECT/records/tests/2026-10-28-release-3.1.38.md`
