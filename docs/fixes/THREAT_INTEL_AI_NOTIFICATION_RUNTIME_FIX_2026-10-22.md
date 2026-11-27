# 威胁情报、AI 与通知运行时修复

日期：2026-10-22  
版本：3.1.33

## 修复范围

### 1. 外部情报源写入失败

- 根因是 Hibernate 将 Java `String` 直接绑定到 PostgreSQL `inet` 列，PostgreSQL 不会隐式把 `varchar` 转为 `inet`。
- `ThreatIndicator.iocInet` 的新增和更新统一使用 `CAST(? AS inet)`；单个创建、更新及订阅源批量导入走同一实体映射。
- 非 IP 类型继续将 `ioc_inet` 保存为 `NULL`，不改变 `ioc_value` 原始值。
- 新增基于 PostgreSQL 15 Testcontainers 的集成测试，覆盖 IPv4、IPv6、CIDR、非 IP IOC、更新和 bulk upsert。

### 2. 订阅源顺序跳动

- 原列表依赖数据库未声明顺序，启用或拉取后的刷新可能改变行位置。
- 后端查询固定为 `INTERNAL` 源优先，其余按不可变 `id` 升序；前端无需维护第二套排序状态。

### 3. 平台 AI 配置未被 TIRE 使用

- API Gateway 新增 `GET /internal/v1/runtime-config/llm/effective`，仅接受 `X-Internal-Service-Token`。
- 接口按用户自有配置、管理员锁定/分配配置、系统配置的既有优先级解析实际有效的 API Key、模型和 Base URL。
- TIRE 在生成报告前动态读取配置，按用户/客户/租户上下文短时缓存；网关短暂不可用时只在限定时间内使用最后一次成功值。若集成已启用但网关不可用且没有有效缓存，则按 fail-closed 策略禁用本次 LLM 增强并生成模板报告，不再回退本地/环境密钥，避免绕过未知的 `admin_locked` 策略。
- 仅当运行时接口和内部服务令牌均未配置（旧版独立 TIRE 部署）时，才继续使用本地管理数据库/环境变量兼容路径；只配置其中一项视为错误并执行 fail-closed。本地解析器也会显式决定环境密钥是否可用，报告层不得二次隐式回退。
- 配置指纹参与报告缓存键，修改模型、地址或密钥后不会误用旧报告。
- 连接测试现在发送最小 `/chat/completions` 请求，验证所选模型确实能够生成内容；`/models` 仅作为可选补充。

### 4. 通知配置保存后未按策略生效

- 告警分发改为读取客户配置中的最低严重级别和允许严重级别清单，不再对已配置客户硬编码 `CRITICAL`。
- 支持跨午夜静默时段和时区；启用频率限制时，以 PostgreSQL 原子 upsert 预占每小时投递额度。
- 邮件、短信、Slack、Webhook 按启用状态和有效目标分别入队。
- 配置测试执行真实同步投递并返回每个通道/目标的成功或失败结果，不再返回模拟成功。
- 失败通知进入 `RETRYING`，在 `next_retry_at` 到期后由定时任务重试，达到上限后转为 `FAILED`。
- SMTP 默认配置只从激活记录中选择；停用配置不会继续被缓存或用于连接测试。
- 短信通过 `SMS_PROVIDER=twilio|aliyun` 选择实现，Compose 显式传递两类供应商凭据。

### 5. 凭据静态加密

- LLM API Key 与 SMTP 密码使用 AES-256-GCM 保存，格式为 `enc:v1:<iv>:<ciphertext+tag>`。
- 安装器首次安装自动生成 32 字节 `CONFIG_ENCRYPTION_KEY`，升级保留已有值；TIRE 内部接口令牌同样自动生成并保留。
- 应用启动后会把旧版明文 LLM/SMTP 凭据迁移为密文；迁移期仍可读取明文，避免原地升级中断业务。
- API 响应继续只返回掩码，不返回密文或明文；日志不打印令牌和 API Key。
- 备份和恢复必须同时保留 `/opt/threat-detection-system/.env` 中的 `CONFIG_ENCRYPTION_KEY`。丢失该密钥后，已有密文不可恢复，需要重新录入凭据。

## 数据库与部署

- 新增 `49-notification-runtime-reliability.sql`，为 `notifications` 增加 `customer_id`、`next_retry_at` 及重试/客户索引，并登记迁移账本。
- 全新安装由 PostgreSQL 初始化目录执行；旧版本直接运行 `sudo ./install.sh` 时由迁移账本补执行，无需 purge。
- `.env.example`、环境生成器和 Compose 已同步 `CONFIG_ENCRYPTION_KEY`、`TIRE_INTERNAL_SERVICE_TOKEN` 与短信供应商变量。

## 运维检查

1. AI 配置保存后先执行“连接测试”，再生成一份新报告或选择重新生成；TIRE 不会对历史已缓存报告强制重算。
2. 通知测试会真实发送消息，测试前确认收件人、Webhook 和第三方配额。
3. 订阅源拉取完成后检查“状态、指标数量、最后拉取时间”；数据库中的 IP 主机可能规范化显示为 `/32` 或 `/128`，这是 PostgreSQL `inet` 的正常文本形式。
4. 密钥轮换不能只替换 `CONFIG_ENCRYPTION_KEY`。必须先用旧密钥解密、再用新密钥重加密全部凭据；当前版本不提供在线自动轮换。

## 发布产物

- 本地目录：`outputs/v3.1.33/`
- 归档：`threat-detection-system-3.1.33-linux-x86_64.tar.gz`
- SHA-256：`9c565166031eec2e40af46d04b8d9add1747607bf4ed82e0fe7c96a78b257ebd`
- 构建与验证记录：`AI_PROJECT/records/tests/2026-10-22-release-3.1.33.md`

## 通知配置接口边界加固

- 根因：API Gateway 已执行 JWT/RBAC，但 Alert Management 的 `/api/notification-config/**` 原先信任网络位置；Compose 同时把 8082、8084 发布到宿主机，调用者可以绕过网关，或经 Customer Management 的真实通知测试代理触发发送。
- Alert Management 现在对整组通知配置接口强制校验独立的 `X-Internal-Service-Token`，令牌使用常量时间比较；缺失、错误或重复令牌头统一返回 401。健康检查与告警消费链路不受影响。
- API Gateway 在完成 JWT/RBAC 后以覆盖方式写入内部令牌，避免客户端夹带同名头；Customer Management 转调真实通知测试时也由服务端附带同一令牌。
- API Gateway 将 `/api/notification-config/**` 的所有 HTTP 方法限制为 `SUPER_ADMIN`；普通客户、客户管理员和租户管理员无法读取全量客户通知配置，也不能触发 SMTP 或真实通知测试。
- Compose 不再向宿主机发布 8082/8084，两个服务改为 Docker 内网 `expose`；Web 前端和外部 API 继续统一访问 8888 网关，现有公开路由不变。
- 安装器通过 `.env.example` 和 `prepare_release_env.py` 首次生成 96 字符的 `ALERT_INTERNAL_SERVICE_TOKEN`，升级时保留原值且不打印秘密值。该令牌必须与运行环境配置一起备份，不得发送给浏览器或写入日志。
