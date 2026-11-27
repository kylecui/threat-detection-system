# 架构说明

## 3.1.34 会话、告警与情报有效性

- API Gateway 使用稳定的 401 认证错误码区分令牌过期/无效和普通 403 权限不足；Web 统一终止过期会话并清理租户/客户范围，避免局部接口错误把页面留在无效登录态。
- Threat Assessment 的所有等级仍保留；Alert Management 以可配置最低等级决定哪些结果进入处置队列，保存后再由客户通知策略决定是否及通过何种通道投递。
- Threat Intelligence 区分本地写入源和外部轮询源。订阅源启停控制来源指标是否参与有效查询；轮询是幂等刷新，只有显式目击操作修改 sighting count。
- Web 表格统一使用定宽技术标识、右对齐制表数字、居中语义状态和固定操作热区；次要列按断点隐藏，避免用横向滚动代替信息层级。

## 3.1.30 总览、分析窗口与配置运行时

- 总览继续使用单个 `overview-snapshot` 接口；24 小时按小时聚合，7 天和 30 天按自然日聚合并补齐空桶，避免同一日期被多个小时点映射后形成竖线。后端使用 Caffeine 15 秒缓存并同步同键装载，前端再合并同作用域/时间范围的并发请求和提供 10 秒短缓存。
- 总览范围统计、趋势、端口、无端口协议、Top 攻击者和最近威胁都受相同 `since` 限制；最近威胁按规范化 MAC 每个只取最新一条，避免一个高频 MAC 占满列表。长周期查询由 47 号迁移提供组合索引。
- Flink 产生的 30 秒、5 分钟和 15 分钟窗口在消息中携带窗口边界与检测层级；Threat Assessment 按客户、MAC、层级和窗口起点幂等保存，46 号迁移提供唯一约束。Web 可显示和筛选三个分析窗口。
- 有效 TCP/UDP 端口限定为 1–65535；ARP、ICMP、ICMPv6、GRE、ESP、AH、端口异常和其他无端口事件单独统计，不再伪装成端口 0。只有 MAC 使用专用蓝色等宽标签，IP、端口、序列号和服务标识使用普通文本。
- `tire` 作为第 18 个常驻 Compose 服务提供威胁情报运行时，安装器自动生成独立 Fernet/会话密钥并纳入健康检查。Threat Intelligence feed 从 `system_config` 读取密钥，缺少密钥时记录明确失败原因；管道健康同时展示 TIRE。
- ML 训练按 Tier 展示真实进度，并兼容仅有一个分组的训练集；通知配置会将满足严重度规则的告警分发至已启用且有有效接收目标的邮件、短信、Slack 和 Webhook 通道。

## 3.1.29 Docker/containerd 任务状态读取

安装验收和运行时 watchdog 都通过 `ctr -n moby tasks list` 核对 Compose 容器对应的 containerd task。任务匹配器必须完整读取命令输出，不能在找到目标行后提前关闭管道；否则启用 `pipefail` 时，上游 `ctr` 会在多容器主机上收到 `SIGPIPE`（退出码 141），并被事务安装器误判为业务启动失败。真实的 PID 缺失、task 缺失或非 `RUNNING` 状态仍按原有逻辑判为故障。

## 3.1.28 原始事件端口聚合与标识展示

- Threat Assessment 的端口 Top 15 直接查询 `attack_events`，按 `customer_id` 或 `tenant_id`、时间范围和规范化 `response_port` 在 PostgreSQL 分组；不再解析 `threat_assessments.port_list`，因此图表“攻击次数”与原始攻击日志条数含义一致。
- 查询只把 1–65535 的值纳入端口图；其他协议在 3.1.30 起进入独立协议分类。服务层只为已知端口补充协议名，前端负责本地化显示和横向排序。
- Web 端通过 `PortDistributionChart` 展示离散分类条形图，通过 `NetworkIdentifier` 统一值格式；3.1.30 起只有 MAC 使用专用标签样式。
- 该变更只影响查询聚合与展示，不改 Syslog/MQTT 输入协议、Kafka/Flink 管道、ML 评估、告警流程、数据库写入和部署端口。

## 3.1.27 威胁评估字段贯通

- `attack_events.attack_ip` 经 Flink 聚合写入 `threat_assessments.attack_ip`。
- 威胁详情、分页列表、分组展开、租户列表和总览最近威胁共用 `ThreatAssessmentDetailResponse`，响应转换必须从持久化实体读取 `attack_ip`，禁止使用固定占位值覆盖真实数据。
- 前端只负责空值降级展示；CSV/JSON 导出复用相同 API 字段，保证页面与导出一致。

## 3.1.26 数据库迁移账本

- PostgreSQL 空卷首先执行 `00-schema-migrations.sql` 创建 `tds_schema_migrations`。
- 安装器管理的 43–45 号迁移在脚本末尾登记完成状态。
- 安装器在数据库就绪后先读取账本：已登记迁移跳过，未登记迁移按顺序执行并验证登记结果。
- 历史数据卷没有账本时由安装器自动创建，不改变业务表和数据保留边界。
- 迁移元数据位于同一 PostgreSQL 数据卷：`--uninstall` 保留，`--purge` 随卷删除。

## 3.1.25 设备绑定一致性

设备与客户的绑定采用时间区间模型：当前绑定同时满足 `is_active=true` 与 `unbind_time IS NULL`，解绑只关闭当前区间并保留历史。升级迁移先按大小写无关的设备序列号归并旧重复数据，仅保留最新一条当前绑定，再以 PostgreSQL 部分唯一索引保证一个序列号最多存在一条当前绑定。

Customer Management 和 Threat Assessment 两条绑定写路径使用相同的设备级 PostgreSQL advisory transaction lock；客户侧另加客户级锁串行化配额计算。数据库唯一索引是最终一致性边界，前端同步 ref 锁只负责阻止双击和改善交互，不能替代后端约束。

攻击日志归属回填由独立异步服务执行，使 Spring 代理真正启用 `@Async`，避免绑定事务同步扫描历史事件表。Web 对批量绑定必须读取 `succeeded`、`failed` 与 `failures`，HTTP 201 本身不代表每个设备均绑定成功。

## 3.1.24 心跳与数据活跃状态模型

客户端的 V1 Syslog 通道默认每 1800 秒发送一次心跳，因此 Customer Management 不再使用固定 5 分钟阈值。默认策略为：最后心跳不超过 2100 秒时为 `ONLINE`，2100–3900 秒为 `DELAYED`，超过 3900 秒才为 `OFFLINE`；3900 秒相当于容忍一次预期心跳丢失并增加 5 分钟宽限。阈值由 Customer Management 环境变量控制，不能与 MQTT 的 300 秒详细心跳或订阅端 60 秒保活混用。

设备状态分为四个正交维度：绑定状态表示客户关系是否启用；心跳状态只读取 `device_status_history`；数据活跃状态取最近一次已入库心跳或攻击事件；分析链路状态比较最近攻击事件与威胁评估。客户端显示的“V1 Syslog 已连接”仅表示到 `9080/tcp` 的通道可达，不作为平台心跳在线的替代证据。

API 新增 `heartbeatState`、`dataActivityState` 和三项心跳阈值字段，同时暂时保留 `connectionState` 作为 `heartbeatState` 的兼容别名。Web 分列展示上述状态，并在设备详情中说明当前阈值。

## 3.1.23 数据链路可观测性与控制面性能

设备绑定记录不再被当作“设备已联通”的证明。Customer Management 通过单次批量查询关联最近心跳、最近原始攻击事件、24 小时事件数和最近威胁评估，并将连接状态和处理状态分别返回；原始事件明细使用分页接口，禁止把设备历史一次性加载到 Web。

总览使用 Threat Assessment 的单个 `overview-snapshot` 接口获取统计、趋势、端口、攻击者和最近威胁。客户范围按客户 ID 查询；“全部客户”直接用租户 ID 在 PostgreSQL 聚合，不依赖浏览器缓存的客户列表。顶栏范围选择器只保留当前页结果，租户和客户均采用服务端搜索与分页。

Flink 攻击事件水位线采用 30 秒乱序预算，并在分区 45 秒无数据后标记 idle，避免空闲分区长期阻塞有效分区的窗口输出。Gateway 为 Customer、Threat Assessment 和 Alert Management 统一使用 5 秒有界冷启动预算；前端 GET 最多重试一次，并在短窗口内合并同一接口的重复错误提示。

## 3.1.22 多租户作用域与权限边界

前端由 `ScopeContext` 独占租户、客户、列表加载和持久化。顶栏、总览、客户设备页与 API 拦截器只消费这一个状态源；“全部客户”是纯 UI 值，不写入请求。平台/租户管理员清空客户选择后，拦截器不得回退历史 `customer_id`。租户或客户生命周期变化会刷新同一状态源，并清除已经停用的当前选择。

权限层级为：`SUPER_ADMIN -> TENANT_ADMIN -> CUSTOMER_ADMIN -> CUSTOMER_USER`。客户创建时的可选账号赋予 `CUSTOMER_ADMIN`；客户管理员令牌固定携带 tenant/customer，网关把读写请求收敛到该客户，只开放普通用户管理和客户自助配置写入。`CUSTOMER_USER` 仍是只读客户账号。

Customer Management 在客户事务开始时锁定租户行，检查租户存在且为 `ACTIVE`，并以未归档客户数校验 `max_customers`。API Gateway 的用户控制器再次校验角色、租户和客户归属。因此修改浏览器参数不能越过多租户边界。

## 3.1.21 客户创建与账号原子化

客户创建写路径由 Customer Management 统一负责：API Gateway 传递调用者角色和租户作用域，控制器先解析有效租户，再由单个 Spring 数据库事务写入 `customers`、`auth_users` 和 `auth_user_roles`。可选账号使用 BCrypt 哈希，固定绑定到新客户及其租户，并赋予客户范围管理角色 `CUSTOMER_ADMIN`。任一写入或约束校验失败都会回滚整个事务，因此不会出现“客户已创建但账号创建失败”或相反的半成功状态。

前端只负责生成并一次性展示与客户 ID 关联的初始凭据；服务端重新验证账号格式、密码前缀、长度和强度，不能依赖前端校验作为安全边界。缺少租户属于可预期请求错误，异常处理必须保留原始 400/403，只有未预期异常才返回 500。

## 主数据流

```text
JZ 客户端
  ├─ HTTP 日志/心跳 ─────────────┐
  ├─ V1 Syslog -> Logstash ─────┼-> Data Ingestion -> Kafka
  └─ V2 MQTT -> EMQX ───────────┘       ├─ attack-events
                                         └─ status-events
                                                |
                             Flink JobManager + TaskManager
                                                |
        Threat Assessment <-> Threat Intelligence <-> TIRE <-> ML Detection
                                                |
                           PostgreSQL <- Alert Management
                                                |
                                  API Gateway -> Frontend
```

## 部署组成

18 个常驻 Compose 服务：

1. postgres
2. zookeeper
3. redis
4. logstash
5. emqx
6. kafka
7. data-ingestion
8. stream-processing
9. taskmanager
10. threat-assessment
11. alert-management
12. customer-management
13. threat-intelligence
14. tire
15. api-gateway
16. config-server
17. ml-detection
18. frontend

`topic-init` 是一次性初始化容器，必须成功退出但不属于常驻服务。

## 安装架构

- 发布包携带构建源码，目标机本地执行 Docker 构建。
- `.env.example` 是字段模板，安装器生成真实 `.env`，升级时保留。
- PostgreSQL 数据、Logstash 数据和 EMQX 数据使用 Compose named volumes。
- systemd unit 调用同一份 Compose 文件，保证安装启动和开机启动一致。
- 安装前校验 `MANIFEST.sha256`；文件替换前建立回滚快照。
- 安装事务前解析最终 Compose 配置并检查宿主发布端口；Threat Assessment 使用宿主 `8083/tcp`，EMQX WebSocket 使用宿主 `18084/tcp`（容器内仍为 `8083`）。
- `tdsdiag` 只收集状态和公开健康结果，不读取秘密或业务数据。

## 3.1.16 性能与可用性基础

- Kafka 业务主题默认 12 分区；接入生产端启用批量、压缩、幂等与 `acks=all`。
- HTTP 单条接入异步等待 Kafka ACK，不占用请求线程；批量接入使用有界线程池、队列、超时和反压。
- Flink 默认并行度 2、4 个 Task Slot，JDBC 与 APT Sink 可独立调节，并将 checkpoint 写入持久卷。
- PostgreSQL 大表增加组合索引、BRIN 时间索引和 autovacuum 参数；首页统计合并为单次聚合扫描。
- Gateway 提供公开 `/api/v1/system/readiness`，同时检查关键业务服务、Kafka、PostgreSQL 与 Redis。
- 前端按路由懒加载，短暂依赖故障采用有限 GET 重试和局部降级。
- 单机 Compose 仍是单节点形态。Kubernetes production overlay 只完成无状态服务多副本、PDB/HPA 基础；端到端高可用仍要求外部/集群化 Kafka、PostgreSQL、Redis、EMQX 和共享 Flink checkpoint。

## 3.1.17 控制面契约与响应性

- 威胁列表只在客户作用域明确后查询；明细和分组视图分别按需加载，分页统一为后端的 `page`/`size` 契约。
- 默认 SMTP 配置采用单例 upsert，空库返回正常的 `configured=false`，API 只返回 `passwordConfigured`，不回传密码。
- Customer Management 的网关熔断采用 5 秒有界预算和 2 秒慢调用观测阈值；安装验收直接预热客户分页查询。
- ML 数据就绪统计通过一次聚合查询完成，在工作线程中执行并带 SQL 超时和短时缓存；ML 页面核心状态与次要指标解耦加载，轮询不重叠。

## 与控制面 Broker 的边界

本项目内置 EMQX 的 `1883` 是数据接入通道；`jz-cloud-mqtt-server` 的 `8883` 是设备/配置平台控制面 mTLS 通道。两者的 Topic、账户、证书和业务职责均不共用。

## 3.1.18 运行时真实性与定向恢复

- `threat-detection-compose.service` 负责整套 Compose 开机启动，启动后调用运行时看门狗检查真实任务。
- `threat-detection-runtime-watchdog.timer` 每分钟触发一次只读检查；发现 Docker `running` 与 PID/containerd 不一致时，仅重建对应 Compose 服务。
- 看门狗与安装器共享安装锁，升级、回滚和 purge 期间不会并发修改容器。
- `tdsdiag` 使用同一检查器，并另外探测 V1 Syslog `9080/tcp`，避免端口代理存在但后端进程已停止的假健康。
- 数据卷不属于看门狗操作范围，定向重建保留 PostgreSQL、Kafka、Logstash队列和 Flink checkpoint。

## 3.1.19 客户生命周期与租户隔离

- `INACTIVE` 表示可恢复归档，不等同于物理删除；客户唯一标识始终保留，管理列表继续显示归档记录。
- 永久删除先检查跨服务共享库中的用户、设备映射、攻击、告警、评估、配置、心跳与 ML 依赖；存在任一依赖即返回 409 及依赖计数。
- API Gateway 每次认证请求读取实时用户状态，并验证所属租户和客户均为 `ACTIVE`；JWT 中的旧状态或调用方参数不能扩大作用域。
- Tenant Admin 的客户路径必须属于其租户；Customer User 只能访问自身客户，查询参数由网关覆盖为权威作用域。
- Data Ingestion 仅缓存活动客户的设备映射，并以 5 秒生命周期状态缓存阻断停用客户的新攻击日志与心跳，避免逐事件数据库查询。

## 3.1.20 网关认证响应生命周期

- JWT 过滤器先把用户查询结果转换为显式“存在/不存在”分支，再进入返回 `Mono<Void>` 的 GatewayFilterChain；成功完成的下游请求不会再被当成空用户。
- 实时账户、租户和客户状态验证仍在转发前执行；账户不存在或生命周期失效分别返回确定的 `401`，不会进入业务服务。
- GlobalExceptionHandler 在响应已提交时传播原始异常，不再改写只读状态码、响应头或响应体，避免掩盖首个故障并再次中断连接。
- 限流、认证和授权直接响应在写入 `429/401/403` 前统一检查提交状态，作为并发和后置异常场景的防御边界。
