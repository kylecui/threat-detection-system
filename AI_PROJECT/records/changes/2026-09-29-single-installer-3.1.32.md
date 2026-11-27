# 3.1.32 单一安装脚本

## 问题

源码和发布包同时存在根 `install.sh` 与 `scripts/install.sh`。虽然前者只是转发器，但文件名相同，容易让操作员误认为存在两套安装方式，也曾因内部脚本执行权限异常造成安装失败。

## 修改

- 将事务安装器完整实现移动到发布包根 `install.sh`。
- 删除 `scripts/install.sh`，`scripts/` 仅保留环境生成、诊断、打包和运行时看门狗辅助工具。
- 更新打包器，不再收集或设置内部同名脚本权限。
- 更新发布契约，要求归档中名为 `install.sh` 的文件只能有根目录一个。
- 同步 README、部署、模块、需求和发布流程文档。

## 兼容性

正式操作命令不变，仍为 `sudo ./install.sh`。安装到 `/opt/threat-detection-system/install.sh` 后，升级、`--uninstall`、`--purge` 和 `--purge --dry-run` 路径也不变。
