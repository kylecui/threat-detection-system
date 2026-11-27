# 3.1.33 情报、AI 与通知运行时修复

日期：2026-10-22

## 目标

- 消除 AbuseIPDB 等外部源向 PostgreSQL `inet` 列写入字符串时的失败。
- 保持订阅源列表顺序稳定。
- 让平台保存的 AI 配置在 TIRE 报告生成时立即生效。
- 让通知严重级别、静默时段、频率限制、真实测试、失败重试与短信供应商选择进入真实投递链路。
- 将数据库中的 LLM API Key 和 SMTP 密码由明文改为 AES-256-GCM 密文。

## 实现摘要

- Threat Intelligence 实体写入使用 `CAST(? AS inet)`，并增加真实 PostgreSQL 集成测试。
- Feed Repository 增加 internal-first、ID-ordered 查询，服务层统一使用该顺序。
- API Gateway 提供受 `TIRE_INTERNAL_SERVICE_TOKEN` 保护的内部有效配置接口；TIRE 按请求上下文动态读取并缓存。
- LLM 连接测试改为实际最小生成请求；全局和多供应商 API Key 在写入前加密。
- Alert Management 增加统一通知策略、原子频率额度、真实通道测试、定时重试和短信路由；SMTP 密码写入前加密，停用配置不再发送。
- Customer Management 增加通知测试代理，Web 配置页补充策略字段并展示真实测试结果。
- Compose 和安装环境增加并保留加密密钥、内部服务令牌、短信供应商凭据；新增 49 号数据库迁移。

## 兼容性

- 保持现有公开路由、业务模型和前端入口不变。
- TIRE 网关不可达时保留有界旧值；集成已启用且无有效缓存时执行 fail-closed，仅生成模板报告，不使用本地或环境 LLM 密钥，防止绕过不可见的 `admin_locked` 策略。只有未启用运行时桥接的旧版独立部署保留本地解析兼容路径。
- 旧数据库明文凭据由应用启动迁移，迁移期读取兼容明文。
- 旧版安装直接运行根目录 `install.sh`，49 号迁移只执行一次并保留原数据卷与 `.env`。

## 安全说明

- 内部接口返回 API Key，但只在 Compose 内网通过随机长令牌访问，响应声明 `no-store`，日志不记录密钥。
- `CONFIG_ENCRYPTION_KEY` 是恢复密文所必需的主密钥，必须与数据库备份一起安全托管但分开存储。
- 当前不提供透明主密钥轮换；轮换需离线解密/重加密计划。

## 通知配置接口边界加固

- Alert Management 为 `/api/notification-config/**` 增加 `ALERT_INTERNAL_SERVICE_TOKEN` 服务端鉴权；错误、缺失或重复令牌头不能进入 Controller。
- API Gateway 在 JWT/RBAC 通过后覆盖注入内部令牌；Customer Management 的真实通知测试代理以服务身份携带令牌。
- `/api/notification-config/**` 在网关 RBAC 中对所有方法执行 `SUPER_ADMIN` 限制，避免非平台管理员读取跨客户配置或触发真实投递。
- Compose 将 Alert Management 8082 和 Customer Management 8084 改为仅容器网络可见，阻止通过宿主机端口绕过 8888 网关。
- 发布环境生成器自动生成、校验并在升级时保留该令牌；令牌不进入前端构建或 API 响应。
