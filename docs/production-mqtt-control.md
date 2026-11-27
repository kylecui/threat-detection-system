# 威胁检测平台生产MQTT控制接入

新增控制能力位于API Gateway的`control`包，使用独立Mosquitto连接；原`data-ingestion/MqttListenerService`、EMQX、Kafka和分析流程保持原路径。

## 启用

1. 在配置平台审批并注册 controller/platform、专用账户及动作范围。v1.1 在配置平台注册项设置 `"device_scope": "all_registered"` 可按已注册的安装序列码直寻内部设备。威胁平台仍检查本地客户当前有效绑定，按大小写不敏感匹配，并把绑定表中的原始序列码经请求信封发送。配置平台不支持该模式时，继续使用显式设备 grant。
2. 通过合并版注册脚本一次性交付MQTT密码和HMAC签名密钥，客户端证书经可信CA签发后转换成`client.p12`，服务器CA存入`trust.p12`，将目录置于源码目录之外并限制宿主权限。证书SAN必须匹配`JZ_CONTROL_BROKER_URL`主机名；不得关校验。
3. 通过私密环境文件或秘密管理服务注入 `JZ_CONTROL_BROKER_URL=ssl://<主机>:<TLS端口>`、`JZ_CONTROL_{CONTROLLER_ID,PLATFORM_ID,USERNAME,PASSWORD,SIGNING_SECRET}`、`JZ_CONTROL_{KEY_STORE,TRUST_STORE,KEY_STORE_PASSWORD,TRUST_STORE_PASSWORD,TLS_DIR}`。专用 MQTT 用户名以 `control-` 开头。两个 PKCS12 容器路径位于只读 `/run/jz-control` 挂载，默认文件名为 `client.p12` 与 `trust.p12`。配置平台注册的 `secret_env` 必须取得同一密钥。服务器证书及 SAN 校验保持启用。
4. 使用现有Compose参数并追加`-f docker/docker-compose.control.yml`；该覆盖文件不改EMQX数据端口。主服务`application.yml`默认`JZ_CONTROL_ENABLED=false`，未配置控制能力不会阻塞现有分析。
5. 默认启动时初始化控制请求日志与事件表。禁止启动 DDL 的环境由管理员先按 `DeviceControlService.initializeJournal` 校验并补齐请求表，执行下方事件 DDL，再设置 `JZ_CONTROL_INITIALIZE_SCHEMA=false`。缺表或连接未就绪时控制提交返回 503。
6. 方案 A 的 Mosquitto ACL 允许专用账户发布精确 `request`，订阅同前缀精确 `result`、`event`。Gateway 在两条订阅都获得 QoS 1 后标记 ready。检查双方时间、客户端证书有效期、Broker SAN、ACL 与 HMAC 密钥，再用隔离设备运行实际联调。

## API和Web

`/api/v1/device-control/{customerId}/{devSerial}/readiness`和`/requests`GET查看就绪和最多100条持久化请求；`/requests`POST提交`{action,idempotencyKey,params}`。Gateway本地控制器独立校验Bearer JWT、数据库当前账户/角色和客户/租户生命周期，并查询设备当前激活绑定；不信任客户端X-Auth-*、customer_id或裸角色。客户用户只读，当前客户管理员及租户/平台管理员在其设备范围内可变更。

| 当前数据库角色 | 动作权限 |
| --- | --- |
| CUSTOMER_USER | 11 项只读动作及事件接口 |
| CUSTOMER_ADMIN | 只读和常规写，`channel.repair` 返回 403 |
| TENANT_ADMIN / SUPER_ADMIN | 28 项，仍检查当前绑定、客户和租户状态，租户管理员还受其租户范围约束 |

- 原有只读动作为 `capabilities`、`device.status`、`config.read`、`config.history`、`config.validate`、`lock.read`、`commands.history`、`task.get`。原有写动作为 `config.draft`、`config.push`、`config.restore`、`lock.set`、`lock.clear`、`candidates.pull`、`candidates.approve`。
- 新增只读 `channel.diagnostics`、`policy.list`、`violation.list`。新增写 `cmd.send`、`task.retry`、`task.cancel`、`channel.reconcile.start`、`channel.reconcile.review`、`channel.repair`、`policy.create`、`policy.update`、`policy.delete`、`violation.acknowledge`。`cmd.send` 拒绝 `containment_approve` 和 `containment_candidates`，两者分别使用 `candidates.approve` 和 `candidates.pull`。人工对账复核要求 JSON 布尔值 `human_confirmed: true`。
- 请求和结果保持签名信封。已存结果只能被严格较高的 `result_version` 更新，含终态回滚。无版本的旧结果保留原阶段护栏。待设备确认继续发起只读快照补偿，需人工对账状态由操作员处理。

### 事件

事件主题为 `jz/control/v1/{controller_id}/{platform_id}/event`。配置平台使用原 HMAC 信封，设置 QoS 1 和 `retain=false`。网关校验签名、两端身份、schema、事件标识和字段，在持久化后发送 MQTT ACK。五类事件为 `device.presence`、`config.reported`、`lock.changed`、`task.terminal`、`violation.created`。

以下 API 均使用 `Authorization: Bearer <token>` 和现有只读授权。有多个同名有效绑定或事件早于新绑定时，事件写入拒绝关联。查询只读取当前绑定期间收到的事件。

| 方法及路径 | 参数 | 返回 |
| --- | --- | --- |
| GET `/api/v1/device-control/{customerId}/{devSerial}/events` | `limit` 1–100，默认 50。可选非负整数 `after` | `{items,nextCursor,hasMore}`。首屏最近入库倒序，带 after 按入库顺序向前读取。游标为十进制字符串 |
| GET `/api/v1/device-control/{customerId}/{devSerial}/events/stream` | 无查询参数 | `text/event-stream`，`control-event` 的 id 和 JSON `{cursor}` 通知浏览器增量查询，每 10 秒心跳，最长 5 分钟后重连。授权在建立和每次通知/心跳前复核 |

`items` 包含 `eventId,eventType,controllerId,platformId,deviceId,devSerial,occurredAt,data,cursor`。浏览器用带 Bearer 请求头的 fetch 读取 SSE，断线可短轮询补齐。配置报告触发只读刷新时保留未保存 YAML。经过反向代理时保留 `X-Accel-Buffering: no`，代理读超时须大于 10 秒。

```sql
CREATE TABLE IF NOT EXISTS mqtt_control_events (
  event_id VARCHAR(128) PRIMARY KEY,
  sequence_id BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
  event_type VARCHAR(32) NOT NULL,
  controller_id VARCHAR(64) NOT NULL,
  platform_id VARCHAR(64) NOT NULL,
  device_id VARCHAR(128) NOT NULL,
  dev_serial VARCHAR(50) NOT NULL,
  customer_id VARCHAR(100) NOT NULL,
  binding_id BIGINT NOT NULL,
  bound_at TIMESTAMPTZ NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  data JSONB NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_mqtt_control_event_device
  ON mqtt_control_events (UPPER(dev_serial), occurred_at DESC, event_id);
CREATE INDEX IF NOT EXISTS idx_mqtt_control_event_cursor
  ON mqtt_control_events (customer_id, binding_id, UPPER(dev_serial), sequence_id DESC);
```

Web入口：平台运行→设备控制（`/operate/device-control`，使用当前客户范围）；原客户与设备→设备管理也有设备控制入口。客户管理员及只读客户用户可以进入独立设备控制页，组织管理页保持原权限。先读取授权能力，再读取配置及版本。完整YAML编辑保留observed/desired/applied/draft区分，保存须带基线revision，下发冻结保存revision；历史恢复只创建新草稿。候选审批要求先成功拉取同设备批次，选定设备返回的候选并人工确认，审批人由后端身份强制写入。

任务显示“Broker已接收”“受理等待设备”“设备执行成功/拒绝/超时”等独立阶段，关闭面板停止轮询，数据异常保留最后有效内容并显示过期。控制器重连只查询任务快照，不自动重发隔离操作。请求/结果HMAC、主题和动作细节以合并版`docs/platform-control-v1.md`为准。

控制连接恢复时和每30秒进行一次有界只读任务补偿，因此结果消费者写库暂时失败后仍能恢复真实状态。当前Gateway控制连接须单实例；未来扩容需领导权或独立控制进程。设备转客户时先停旧管控、撤销合并版旧grant并核对在途任务，再审核新grant；当前不会把客户数据绑定变更自动视为合并版控制授权变更。

## 方案 A → 公网方案 C 迁移前置清单

当前主 Compose 使用 `emqx/emqx:5.5.1`，带 `EMQX_ALLOW_ANONYMOUS: "true"`。这标示现有内网采集的信任边界。EMQX 5.x 的实际行为取决于各监听器和启用的认证链，不能通过把旧变量改成 false 来证明已拒绝匿名。公网侧仅开放专用 TLS 监听，采集端口与管理端口限制在现有内网。本期暂不改动现有日志采集与 Kafka 路径。 [EMQX 认证兼容说明](https://docs.emqx.com/en/emqx/latest/release-notes/auth-4.4-to-5.1-compatibility.html)

1. **基线和证书**。保存现有配置、ACL、账户和持久会话备份，盘点采集发布者、消费者、监听器和精确主题。预留自研证书系统签发、续期、双 CA 轮换和吊销演练。专用控制 TLS 监听启用双向校验（`verify_peer` 和 `fail_if_no_peer_cert=true`），配置受信 CA、服务证书和私钥。独立账户及认证链显式启用，并测试空用户名、错误密码、错误 SAN、过期证书和吊销证书。Mosquitto 控制监听要求证书，按部署版本配置 `allow_anonymous false` 与专用 ACL。 [EMQX TLS 配置](https://docs.emqx.com/en/emqx/latest/guides/network/overview.html) [Mosquitto 配置手册](https://www.mosquitto.org/man/mosquitto-conf-5.html)
2. **访问控制**。对控制和桥接身份配置默认拒绝。Gateway 控制账户只发布本地精确 `request`，只订阅精确 `result`、`event`。桥接账号的远端权限相同，本地桥接方向相反。配置平台执行者在远端仅订阅 request、发布 result/event。日志身份仅能访问原采集主题。测试对方 controller/platform、设备原生命令主题、`#`、`+`、采集主题、管理主题与 retained 发布是否被拒。避免遗留的全允许 ACL 排在拒绝规则之前。
3. **单向规则**。对固定前缀 `jz/control/v1/{controller_id}/{platform_id}/` 创建下列三个映射，保留主题及签名字节，不做双向通配转发。桥接客户端使用独立固定 client ID、只读证书目录、受信 CA、`verify_peer`，同时校验远端主机名。以 5.5.1 实际镜像的 MQTT Bridge schema 和配置校验为准。 [EMQX 5.5.1 Bridge schema](https://github.com/emqx/emqx/blob/v5.5.1/apps/emqx_bridge_mqtt/src/emqx_bridge_mqtt_schema.erl)

   | 后缀 | 方向 | QoS / 保留 |
   | --- | --- | --- |
   | request | EMQX 本地 → 公网 Mosquitto | 1 / false |
   | result | 公网 Mosquitto → EMQX 本地 | 1 / false |
   | event | 公网 Mosquitto → EMQX 本地 | 1 / false |

   检查桥接对源 retained 的处理。禁止在桥接入口将原 retained 消息洗成普通消息再交付 Gateway。切换前清查这三个精确主题是否存在 retained 记录，在隔离环境验证拒绝行为，再按实际 Broker 能力配置拦截。验证断连/恢复、QoS 1 重发、积压边界、重复递送和日志主题隔离。
4. **切换与回退**。先以测试身份和单设备验证方案 C，同时记录原方案 A 的 URL、专用账号、PKCS12 和 ACL 配置引用。切换时暂停新写、记录未完成请求，停止 A 控制连接，开启 C 并只变更 Gateway 的连接环境与证书挂载。通过 TLS、订阅、只读查询和事件验收后恢复写入。异常时再次停止写入并停用 C 桥接方向，恢复 A 的环境和证书、重连，用 `task.get` 核对未完成请求，再恢复新操作。使用同一请求标识对账，避免自动重放写操作。

## 验证和限制

工作台已与配置平台正式 v1.1 契约 `docs/platform-control-v1.md` 对齐。通用命令使用 `{cmd, params}`，在线事件读取布尔值 `data.online`，配置任务复核重推要求当前 `expected_base_token` 和人工确认。界面保留顶层历史阶段，并展示回滚、待设备确认与需人工对账等加性状态。

发起或复核对账返回的配置状态会刷新当前基准，未保存的 YAML 和其编辑基准保持配对。对账及通道修复的操作标识、配置基准和可用的诊断摘要按动作预填。操作员仍须根据正式契约确认必填的候选、证据及修复配置字段。

方案 A 的静态前置配置已列在启用清单。真实凭据、证书、Mosquitto ACL、QoS SUBACK 和设备回执待联调。用独立测试设备验证 28 个动作、配置上报到 YAML 刷新、跨租户和角色限制、撤销、断连重启、超时及迟到回执。证书与账户由配置平台管理员管理。

2026-11-27 正式契约对齐复验通过。`mvn -pl services/api-gateway test` 通过 72 项，`npm test -- --maxWorkers=2 --testTimeout=20000 --reporter=dot` 通过 86 项，`npx tsc --noEmit` 和 `npm run build` 均退出 0。

2026-11-26 执行 `mvn -pl services/api-gateway test`，71 项通过。前端 `npx tsc --noEmit` 和 `npm run build` 通过，`npm test -- --maxWorkers=2 --testTimeout=20000 --reporter=dot` 78 项通过，其中工作台 12 项。首轮前端测试遇到 Ant Design 按钮文字间距和渲染超时，最终修正选择器并以 20 秒上限整套重跑。

在临时 PGlite PostgreSQL 实例执行实际事件 DDL、插入与查询及结果更新 SQL，核验重复事件、绑定转移、序列码冲突、游标、较新版本回滚和补偿时间。静态检查确认控制覆盖配置中的凭据来自环境变量，证书目录以只读方式挂载。
