# 3.1.11 发布包可执行权限修复

## 现象

3.1.10 发布包在 Linux 解压后，根 `install.sh` 和 `scripts/install.sh` 均为 `0666`。操作员只对根入口执行 `chmod +x` 后，根入口转交内部脚本时返回 `Permission denied`。

## 根因

打包器先在 Windows 文件系统调用 `chmod`，随后使用宿主 `stat` 结果生成 tar header。Windows 文件系统没有保存所需 POSIX 执行位，导致归档中的脚本模式为 `0666`。

## 修改

- 打包器直接为 tar header 指定权限，不再依赖打包宿主：
  - 目录：`0755`
  - `install.sh`、`scripts/install.sh`、`scripts/prepare_release_env.py`、`scripts/tdsdiag.sh`：`0755`
  - 其他普通文件：`0644`
- 根 `install.sh` 使用 `exec bash scripts/install.sh` 转交实现，增加内部执行位异常时的兼容保护。
- 发布测试直接读取 tar member mode，并检查 Linux 解压后的执行权限。

## 安装入口说明

操作员始终只执行发布包根目录的 `sudo ./install.sh`。`scripts/install.sh` 是随包携带的内部实现，不是第二套安装方式。
