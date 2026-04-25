# TDS Frontend Restructure Plan — Comprehensive Test Cases

## 1. 项目理解摘要

- **项目类型**: Web UI（React 18 + TypeScript + Ant Design 5 + Vite）+ 微服务后端（Spring Boot API Gateway + JWT RBAC）。
- **测试目标来源**: `TDS-Frontend-Restructure-Plan.md`（4 phases）+ 现有前端页面/服务实现 + API Gateway 认证授权代码。
- **当前前端事实（基于代码）**:
  - 页面入口：`frontend/src/pages/*/index.tsx`（13页），目前路由在 `frontend/src/App.tsx`。
  - 当前角色模型（实现）: `SUPER_ADMIN`, `TENANT_ADMIN`, `CUSTOMER_USER`（见 `frontend/src/types/index.ts` 与后端 `AuthService/JwtTokenProvider`）。
  - 当前 scope 机制：`localStorage.customer_id` + `api.ts` 自动注入 `customer_id` query（非显式 UI scope）。
  - 当前 `SystemMonitor` 通过浏览器直接探测端口（`frontend/src/services/system.ts`）而不是统一后端健康端点。
  - 当前 Settings 为单页（`frontend/src/pages/Settings/index.tsx`，1981行）。
  - 当前 Dashboard/Analytics 数据重复明显，Analytics仍有客户端聚合 `page_size=200` 行为。
- **后端鉴权事实（用于测试基线）**:
  - 登录返回 user roles + tenant/customer scope（`AuthService` + `LoginResponse`）。
  - JWT claims 当前为 `roles`, `customerId`, `tenantId`（`JwtTokenProvider`）。
  - Gateway 鉴权与RBAC过滤：`JwtAuthenticationFilter` + `RbacAuthorizationFilter`。
  - 当前 CUSTOMER_USER 是读限制角色（写操作应403），TENANT_ADMIN/SUPER_ADMIN有更高权限。
- **高风险区域**:
  1. 角色模型差异（计划5角色 vs 当前后端3角色）导致前后端权限不一致。
  2. scope selector 重构替代 localStorage 注入，容易引发越权/串租户。
  3. 配置级联（继承/覆盖/push/lock）带来的状态一致性与权限边界风险。
  4. 页面合并（Dashboard+Analytics、CustomerMgmt+DeviceMgmt、Settings拆分）带来的功能回归风险。
  5. PipelineHealth、ThreatList增强、Analytics准确性修复涉及新接口契约与前端展示一致性。
- **当前测试现状**:
  - 前端无单元测试（0%）；无既有 Playwright 用例。
- **主要假设**:
  - 本文以**当前后端3角色**为阻塞验收基线；计划中的 `customer_admin/operator/viewer` 作为目标态补充映射验证。
  - 不编写实现代码，仅输出测试设计与可执行测试案例。

---

## 2. 测试策略

### 2.1 分层策略（按收益优先）

1. **E2E/UI（Playwright，P0优先）**
   - 覆盖 RBAC 菜单可见性、路由守卫、关键页面动作权限、scope selector 行为、配置级联交互、页面合并后的核心流程。
   - 原因：本次重构核心风险集中在“角色+导航+页面编排+跨页面流程”。

2. **契约/集成（API Contract + UI API Mock）**
   - 对新接口做契约校验：
     - `/api/v1/system/health`
     - `/api/v1/assessment/stats|trend|top-ports|top-attackers|export|{id}`
     - `/api/v1/config/*`（push/lock/override/overrides）
   - 原因：前端重构成功依赖后端字段/语义稳定（source/locked/scope headers）。

3. **单元测试（Vitest + React Testing Library）**
   - 覆盖 `RouteGuard`, `PermissionGate`, `usePermission`, `ScopeContext`, `ConfigField`、ThreatList 筛选参数组装。
   - 原因：新增组件/Hook 逻辑密集，易出现边界漏洞。

4. **回归冒烟（Playwright smoke）**
   - 每 phase 完成后执行最小回归路径（登录→菜单→关键读写动作）。

### 2.2 自动化优先级

- **优先自动化（P0/P1）**:
  - RBAC矩阵、scope selector、配置级联、关键页面合并回归、ThreatList关键增强、PipelineHealth自动刷新与异常提示。
- **人工探索保留（P2）**:
  - 复杂图表视觉细节、极端响应式布局、可访问性键盘体验（初期可人工，后续再自动化补齐）。

### 2.3 角色基线与目标态映射策略

- **当前后端可执行角色（必须阻塞验收）**:
  - `SUPER_ADMIN`
  - `TENANT_ADMIN`
  - `CUSTOMER_USER`
- **计划目标态5角色（非阻塞跟踪）**:
  - `customer_admin`, `operator`, `viewer` 暂由 `CUSTOMER_USER` 映射承载，必须验证“不会被错误提升权限”。

---

## 3. Traceability Matrix

| 设计点 / 模块 | 入口 / 接口 | 风险 | 测试层 | 应测场景 | 优先级 | 备注 |
|---|---|---|---|---|---|---|
| Phase 1.1 SystemMonitor→PipelineHealth | `pages/SystemMonitor`→`pages/PipelineHealth`; `GET /api/v1/system/health` | 浏览器探活误判、代理环境不可用 | E2E + Contract | 单端点展示服务状态/延迟/pipeline字段，失败态提示 | P0 | 设计推导 + 当前实现观察 |
| PipelineHealth 自动刷新 | `/operate/pipeline` | 时序刷新导致脏数据/闪烁/请求风暴 | E2E | 10s自动刷新、手动刷新、异常重试 | P1 | 设计推导 |
| Phase 1.2 AuthContext/ScopeContext | `AuthContext`, `ScopeContext`, `api.ts` interceptor | scope丢失、header注入错、串租户 | Unit + E2E + Contract | JWT初始化scope、header注入 `X-Tenant-Id`/`X-Customer-Id` | P0 | 设计推导 |
| RBAC路由守卫/菜单过滤 | `RouteGuard`, ProLayout route filter | 越权访问页面、隐藏菜单可直跳 | Unit + E2E | 菜单可见性+直访URL重定向/403处理 | P0 | 设计推导 |
| 当前3角色权限边界 | Gateway `JwtAuthenticationFilter` + `RbacAuthorizationFilter` | CUSTOMER_USER写操作越权 | E2E + API | CUSTOMER_USER读可见写不可用且后端403 | P0 | 实现观察 |
| 目标态5角色差异管理 | 计划3.2/3.3 | 角色语义与后端实现不一致 | Spec/Acceptance | `operator/viewer/customer_admin` 映射回归说明与风险提醒 | P1 | 设计推导 |
| Scope selector 角色行为 | Header scope selector | 选择器显示/禁用错误导致越权 | E2E | super_admin下拉、tenant_admin固定租户+客户下拉、customer_user固定 | P0 | 设计推导 |
| scope crossing & role escalation | 手工构造 header/query | 绕过前端限制读取他租户数据 | API + E2E negative | tamper `X-Tenant-Id/X-Customer-Id` 与 URL参数越权失败 | P0 | 安全边界 |
| Phase 1.3 Region removal | `services/api.ts` | 删除region逻辑导致请求错误 | Unit + E2E smoke | baseURL固定`/api`，无 region 入口，无回归 | P1 | 设计推导 + 实现观察 |
| Phase 1.4 Analytics accuracy quick fix | `pages/Analytics` | 200条样本被误当全量 | E2E | disclaimer显示/文案条件正确 | P1 | 设计推导 |
| Phase 2.1 Dashboard+Analytics→Overview | `pages/Overview` | 重复数据、统计口径不一致 | E2E + Visual sanity | 单一指标区、趋势切换、Top攻击者、recent threats | P0 | 设计推导 |
| Overview 数据一致性 | `/api/v1/assessment/*` | 同屏组件数据不一致 | E2E + Contract | 卡片总数与图表/表格口径一致 | P1 | 设计推导 |
| Phase 2.2 CustomerMgmt+DeviceMgmt→CustomersAndDevices | `/admin/customers` tabs | CRUD/绑定能力丢失 | E2E | Customers tab与Devices tab关键流程保留 | P0 | 设计推导 + 实现观察 |
| Customers&Devices 角色作用域 | customer service endpoints | tenant/customer范围泄露 | E2E + API | TENANT_ADMIN仅本租户，CUSTOMER_USER仅本客户设备 | P0 | 安全边界 |
| Phase 2.3 Settings split 5子页 | `/config/*` | 表单字段丢失、入口混乱 | E2E + Unit | 5子页路由、字段迁移、保存成功 | P0 | 设计推导 |
| Config cascading effective value | `GET /api/v1/config?...` | source/locked元数据错误 | Contract + E2E | inherited/custom/locked 标识与按钮态正确 | P0 | 设计推导 |
| Config push / lock / reset | `POST /config/push` `/lock` `DELETE /override` | 下发错误、不可回滚、权限错误 | API + E2E | push选中范围、force lock、reset恢复继承 | P0 | 设计推导 |
| Override summary | `GET /config/overrides` | 子级覆盖统计不准确 | API + E2E | 表格与实际值一致 | P1 | 设计推导 |
| Phase 3.1 ThreatList enhancement | `pages/ThreatList` + `/assessment/list|{id}|export` | 筛选错误、导出错误、详情丢字段 | E2E + Contract | 过滤、详情抽屉、导出CSV/JSON、批量动作 | P0 | 设计推导 |
| ThreatList与Alerts联动 | Create Alert from threat | 调查流断链 | E2E | threat detail → create alert 成功并可在AlertCenter看到 | P1 | 设计推导 |
| Phase 3.2 PipelineHealth capability | `/operate/pipeline` | “无事件”无告警导致盲区 | E2E + Contract | `last_event_received>5min`黄色告警 | P1 | 设计推导 |
| Phase 3.3 Server-side analytics endpoints | `/assessment/stats|trend|top-*` | 前端仍用样本聚合 | Contract + E2E | 页面调用新聚合端点，口径可信 | P0 | 设计推导 |
| Phase 4 navigation polish | `App.tsx` 新分组导航 | 路由错配、菜单回归 | E2E smoke | 5组导航、默认落地页按角色 | P1 | 设计推导 |
| Breadcrumb/page title | 全页面 | 导航语义不清 | E2E | 标题与层级一致 | P2 | 设计推导 |
| Loading & error boundaries | 全数据页 | 空白页/卡死 | E2E + Unit | skeleton/error/retry/empty state 区分 | P1 | 设计推导 |
| Responsive + a11y | 关键页面 | 小屏溢出、键盘不可达 | E2E manual-assisted | 断点布局、Tab序、快捷键、ARIA | P2 | 设计推导 |

---

## 4. 测试用例列表（按 Phase 组织）

> 说明：
> - **Priority**: P0=blocking, P1=high, P2=medium
> - **来源标记**: `[Design]` 由重构方案推导，`[Impl]` 由当前代码观察得出

### Phase 1 — Critical Fixes + RBAC Foundation

#### TC-P1-001 PipelineHealth 单端点替换成功 [Design][Impl]
- **Objective**: 验证系统监控由浏览器端口探测迁移为网关健康聚合。
- **Target**: `/operate/pipeline`, `frontend/src/services/system.ts`, `GET /api/v1/system/health`
- **Preconditions**: 已登录 `SUPER_ADMIN`；后端返回健康聚合JSON。
- **Inputs / Test Data**: 健康返回：`status=healthy`，`services`和`pipeline`完整字段。
- **Steps**:
  1. 打开 Pipeline Health 页面。
  2. 观察服务卡片与总状态。
  3. 打开网络面板确认仅调用 `/api/v1/system/health`。
- **Expected Results**:
  1. 页面展示 overall + services + pipeline 字段。
  2. 不再出现对 `localhost:808x` 的浏览器直接请求。
- **Priority**: P0
- **Risk Covered**: 反向代理场景失效、误报“服务全挂”。
- **Automation Hint**: Playwright + route assertion。

#### TC-P1-002 PipelineHealth 异常与退化状态呈现 [Design]
- **Objective**: 验证 `degraded/unhealthy` 状态下 UI 告警正确。
- **Target**: `/operate/pipeline`
- **Preconditions**: 可mock健康接口返回异常状态。
- **Inputs / Test Data**: `status=degraded`; 任一 service `DOWN`; `kafka_lag>0`。
- **Steps**:
  1. 注入降级响应。
  2. 刷新页面。
  3. 查看状态色、警示文案、受影响组件。
- **Expected Results**:
  1. 页面显示黄色/红色告警，不显示“Healthy”。
  2. 受影响服务与延迟/lag信息可见。
- **Priority**: P1
- **Risk Covered**: 健康状态误导。
- **Automation Hint**: Playwright API mock。

#### TC-P1-003 PipelineHealth 自动刷新时序 [Design]
- **Objective**: 验证每10秒自动刷新和手动刷新并存正确。
- **Target**: `/operate/pipeline`
- **Preconditions**: 页面可稳定加载。
- **Inputs / Test Data**: 两组连续健康数据（计数递增）。
- **Steps**:
  1. 进入页面记录首屏值。
  2. 等待>10秒并检查值变化。
  3. 点击手动刷新按钮。
- **Expected Results**:
  1. 自动刷新触发且数据更新。
  2. 手动刷新不会重复叠加定时器。
- **Priority**: P1
- **Risk Covered**: 请求风暴/陈旧数据。
- **Automation Hint**: Playwright + fake timers。

#### TC-P1-004 RBAC 菜单可见性（当前3角色）[Design][Impl]
- **Objective**: 验证 `SUPER_ADMIN/TENANT_ADMIN/CUSTOMER_USER` 菜单过滤符合当前后端角色能力与重构路由。
- **Target**: ProLayout菜单 + 路由。
- **Preconditions**: 3类测试账号可登录。
- **Inputs / Test Data**:
  - A: SUPER_ADMIN
  - B: TENANT_ADMIN (tenantId=T1)
  - C: CUSTOMER_USER (tenantId=T1, customerId=C1)
- **Steps**:
  1. 分别登录A/B/C。
  2. 记录可见菜单组与子项。
  3. 比较预期权限矩阵。
- **Expected Results**:
  1. SUPER_ADMIN见全部菜单。
  2. TENANT_ADMIN不见 Tenants 之外的超管专属。
  3. CUSTOMER_USER仅见读相关页，不见管理与高权限配置页。
- **Priority**: P0
- **Risk Covered**: 菜单泄露与误导操作。
- **Automation Hint**: Playwright parameterized by role。

#### TC-P1-005 路由守卫防直链越权 [Design]
- **Objective**: 验证隐藏菜单不能通过 URL 直接访问。
- **Target**: `RouteGuard` + `/admin/tenants`, `/config/integrations`, `/operate/ml` 等。
- **Preconditions**: CUSTOMER_USER登录。
- **Inputs / Test Data**: 直接访问受限URL。
- **Steps**:
  1. 在地址栏输入受限路由。
  2. 观察跳转和提示。
  3. 检查实际数据请求是否发出。
- **Expected Results**:
  1. 重定向到允许页面（如 `/overview`）。
  2. 出现无权限toast。
  3. 不发起受限页面敏感请求。
- **Priority**: P0
- **Risk Covered**: 前端越权入口。
- **Automation Hint**: Playwright。

#### TC-P1-006 Scope selector：SUPER_ADMIN 行为 [Design]
- **Objective**: 验证 super admin 可切 tenant/customer 下拉并支持 All。
- **Target**: Header `ScopeSelector` + API headers。
- **Preconditions**: SUPER_ADMIN，存在 T1/T2, C1/C2。
- **Inputs / Test Data**: 切换 `All Tenants -> T1 -> C1`。
- **Steps**:
  1. 切换scope组合。
  2. 触发Threat/Overview请求。
  3. 检查请求头与页面数据范围。
- **Expected Results**:
  1. `X-Tenant-Id/X-Customer-Id` 与选中scope一致。
  2. All模式展示聚合，不固定单客户。
- **Priority**: P0
- **Risk Covered**: 作用域错绑。
- **Automation Hint**: Playwright network assertions。

#### TC-P1-007 Scope selector：TENANT_ADMIN 行为 [Design]
- **Objective**: 验证 tenant_admin 固定 tenant，仅可切本租户客户。
- **Target**: Header `ScopeSelector`。
- **Preconditions**: TENANT_ADMIN(T1)；T1下C1/C2，T2下C3。
- **Inputs / Test Data**: 尝试选择T2或C3。
- **Steps**:
  1. 登录TENANT_ADMIN。
  2. 检查tenant UI是否固定。
  3. 检查客户下拉只含C1/C2。
- **Expected Results**:
  1. tenant不可切换。
  2. 无法看到/选择T2、C3。
- **Priority**: P0
- **Risk Covered**: 跨租户数据泄露。
- **Automation Hint**: Playwright。

#### TC-P1-008 Scope selector：CUSTOMER_USER 固定scope [Design][Impl]
- **Objective**: 验证 customer_user 无下拉，仅显示固定 scope badge。
- **Target**: Header scope区域。
- **Preconditions**: CUSTOMER_USER(C1)。
- **Inputs / Test Data**: 页面切换至Threats/Alerts/Overview。
- **Steps**:
  1. 登录并巡检各数据页。
  2. 验证scope badge一致。
  3. 验证请求始终落在C1。
- **Expected Results**:
  1. 无tenant/customer可交互下拉。
  2. 请求范围固定C1。
- **Priority**: P0
- **Risk Covered**: 客户用户越权切scope。
- **Automation Hint**: Playwright。

#### TC-P1-009 负面：role escalation 尝试 [Design]
- **Objective**: 验证通过前端存储篡改角色/菜单不能提升后端权限。
- **Target**: 网关 RBAC + 前端动作按钮。
- **Preconditions**: CUSTOMER_USER 登录后可篡改 localStorage。
- **Inputs / Test Data**: 将 `user.roles` 改为 `SUPER_ADMIN`，尝试删除威胁/创建租户。
- **Steps**:
  1. 手动改 localStorage user。
  2. 刷新并执行高危操作。
  3. 观察HTTP状态。
- **Expected Results**:
  1. 后端返回403/拒绝。
  2. 数据不变。
- **Priority**: P0
- **Risk Covered**: 客户端伪造角色。
- **Automation Hint**: Playwright + evaluate localStorage。

#### TC-P1-010 负面：scope crossing 尝试 [Design]
- **Objective**: 验证篡改 `X-Tenant-Id/X-Customer-Id` 或 query `customer_id` 无法越权。
- **Target**: `api.ts` + Gateway scope校验。
- **Preconditions**: TENANT_ADMIN(T1)。
- **Inputs / Test Data**: 构造请求查询T2/C3。
- **Steps**:
  1. 拦截请求并改header/query为他租户。
  2. 发送请求。
  3. 验证响应及数据范围。
- **Expected Results**:
  1. 请求被拒绝或被服务器重写回授权范围。
  2. 不返回跨租户数据。
- **Priority**: P0
- **Risk Covered**: 横向越权。
- **Automation Hint**: API integration test + Playwright route modify。

#### TC-P1-011 Region removal 无回归 [Design][Impl]
- **Objective**: 验证删除 region 切换后 API baseURL 固定 `/api`，功能正常。
- **Target**: `services/api.ts`。
- **Preconditions**: 有历史 `localStorage.region` 值。
- **Inputs / Test Data**: `region=cn/west/auto`。
- **Steps**:
  1. 预置不同region值。
  2. 登录访问核心页面。
  3. 检查请求URL。
- **Expected Results**:
  1. 请求仍正确到 `/api` 代理。
  2. 无 region 相关错误。
- **Priority**: P1
- **Risk Covered**: 历史配置污染。
- **Automation Hint**: Vitest for api client init + Playwright smoke。

#### TC-P1-012 Analytics 准确性 quick-fix 提示 [Design][Impl]
- **Objective**: 验证当仍使用200样本时，页面展示免责声明。
- **Target**: Analytics/Overview（Phase1临时态）。
- **Preconditions**: 仍调用 list?size=200 聚合。
- **Inputs / Test Data**: 页面加载统计视图。
- **Steps**:
  1. 打开分析页。
  2. 检查提示banner文案。
  3. 切换period仍保留提示。
- **Expected Results**:
  1. 明确“最近200条样本”提示可见。
  2. 不以“全量权威统计”暗示展示。
- **Priority**: P1
- **Risk Covered**: 误导性分析结论。
- **Automation Hint**: Playwright text assert。

---

### Phase 2 — Merge & Restructure

#### TC-P2-001 Dashboard+Analytics 合并后无重复卡片 [Design][Impl]
- **Objective**: 验证 Overview 取代两页后信息无重复且结构完整。
- **Target**: `/overview`。
- **Preconditions**: Dashboard/Analytics已下线并重定向。
- **Inputs / Test Data**: 正常统计数据。
- **Steps**:
  1. 访问 `/dashboard`, `/analytics`。
  2. 打开 `/overview` 检查组件布局。
- **Expected Results**:
  1. 旧路由不可用/重定向。
  2. Overview 含：stat cards、trend、level dist、top ports/attackers、recent threats。
  3. 无重复同义组件。
- **Priority**: P0
- **Risk Covered**: 合并不彻底、信息冗余。
- **Automation Hint**: Playwright route + component count。

#### TC-P2-002 Overview 图表切换与数据联动 [Design]
- **Objective**: 验证 24h/7d/30d 切换请求参数与图表更新正确。
- **Target**: `/overview` + trend/top endpoints。
- **Preconditions**: 后端提供各period数据。
- **Inputs / Test Data**: 3个period不同数据集。
- **Steps**:
  1. 切换period。
  2. 记录请求query。
  3. 对比图表渲染值。
- **Expected Results**:
  1. 请求参数正确。
  2. 图表与卡片无明显口径冲突。
- **Priority**: P1
- **Risk Covered**: 时间窗口错绑。
- **Automation Hint**: Playwright + snapshot lite。

#### TC-P2-003 CustomersAndDevices tabs 保留原CRUD [Design][Impl]
- **Objective**: 验证 CustomerMgmt + DeviceMgmt 合并后关键能力不丢。
- **Target**: `/admin/customers`（Customers/Devices tabs）。
- **Preconditions**: SUPER_ADMIN或TENANT_ADMIN。
- **Inputs / Test Data**: 新建客户、编辑、删除；绑定/解绑/同步设备。
- **Steps**:
  1. Customers tab 执行客户CRUD。
  2. Devices tab 执行设备绑定/编辑/同步/解绑。
  3. 跨tab校验数据一致。
- **Expected Results**:
  1. 两类流程均可达且成功。
  2. 设备归属客户关系正确。
- **Priority**: P0
- **Risk Covered**: 功能回归与状态不同步。
- **Automation Hint**: Playwright + API seed。

#### TC-P2-004 CustomersAndDevices 角色作用域 [Design]
- **Objective**: 验证角色作用域内 CRUD 限制正确。
- **Target**: `/admin/customers` + customer endpoints。
- **Preconditions**: 3角色账号 + 多租户数据。
- **Inputs / Test Data**: SUPER_ADMIN, TENANT_ADMIN(T1), CUSTOMER_USER(C1)。
- **Steps**:
  1. 分角色访问并尝试超范围操作。
  2. CUSTOMER_USER尝试设备写操作。
  3. TENANT_ADMIN尝试访问T2客户。
- **Expected Results**:
  1. CUSTOMER_USER无写权限（UI禁用+后端403）。
  2. TENANT_ADMIN仅能操作T1客户。
- **Priority**: P0
- **Risk Covered**: 纵向/横向越权。
- **Automation Hint**: Playwright + API asserts。

#### TC-P2-005 Settings 拆分5子页路由完整性 [Design][Impl]
- **Objective**: 验证 Settings 拆分后路由和菜单完整，旧入口清理。
- **Target**: `/config/general|notifications|integrations|ai|plugins`。
- **Preconditions**: 新路由上线。
- **Inputs / Test Data**: 访问旧 `/settings` 与新子页。
- **Steps**:
  1. 访问旧路由。
  2. 逐个访问5子页。
  3. 检查页面标题与表单模块。
- **Expected Results**:
  1. 旧 `/settings` 重定向/下线。
  2. 5子页均可访问（按权限）。
  3. 表单字段未丢失。
- **Priority**: P0
- **Risk Covered**: 大规模拆页回归。
- **Automation Hint**: Playwright smoke matrix。

#### TC-P2-006 配置继承显示（inherited/custom）[Design]
- **Objective**: 验证每个字段显示有效值与来源标签。
- **Target**: `ConfigField` + config APIs。
- **Preconditions**: platform/tenant/customer存在不同覆盖值。
- **Inputs / Test Data**: 字段A继承、字段B覆盖。
- **Steps**:
  1. 在tenant/customer视角打开配置页。
  2. 观察来源tag与按钮。
- **Expected Results**:
  1. inherited字段显示灰态+Override。
  2. custom字段显示Custom+Edit/Reset。
- **Priority**: P0
- **Risk Covered**: 配置来源误判。
- **Automation Hint**: Playwright + mocked metadata。

#### TC-P2-007 Push 到子级（非锁定）[Design]
- **Objective**: 验证 push 将父值同步到子级且子级仍可覆盖。
- **Target**: `POST /api/v1/config/push` + UI push dialog。
- **Preconditions**: SUPER_ADMIN 或 TENANT_ADMIN。
- **Inputs / Test Data**: 字段 `smtp_host` from parent。
- **Steps**:
  1. 执行 push to selected children。
  2. 切换到子级查看字段。
  3. 在子级编辑同字段。
- **Expected Results**:
  1. 子级值更新为父值。
  2. 子级可再次 override（未锁定）。
- **Priority**: P0
- **Risk Covered**: 下发语义错误。
- **Automation Hint**: API contract + Playwright。

#### TC-P2-008 Force lock（super_admin only）[Design]
- **Objective**: 验证 lock 后子级字段不可编辑。
- **Target**: `POST /api/v1/config/lock`。
- **Preconditions**: SUPER_ADMIN。
- **Inputs / Test Data**: 字段 `notification_email_enabled`。
- **Steps**:
  1. 对字段执行 lock。
  2. 切到tenant/customer视角。
  3. 尝试编辑。
- **Expected Results**:
  1. UI显示🔒并禁用编辑。
  2. 后端拒绝更新请求（403/409）。
- **Priority**: P0
- **Risk Covered**: 锁定失效。
- **Automation Hint**: Playwright + API negative。

#### TC-P2-009 Reset to inherited [Design]
- **Objective**: 验证子级覆盖可回退为继承值。
- **Target**: `DELETE /api/v1/config/{key}/override`。
- **Preconditions**: 子级字段已custom。
- **Inputs / Test Data**: 目标key有父值。
- **Steps**:
  1. 点击 Reset to inherited。
  2. 刷新页面。
- **Expected Results**:
  1. 值恢复父级。
  2. 来源标签变为 inherited。
- **Priority**: P1
- **Risk Covered**: 配置无法回滚。
- **Automation Hint**: API + UI assert。

#### TC-P2-010 配置页角色访问矩阵（3角色基线）[Design]
- **Objective**: 验证配置子页按角色可见且动作权限正确。
- **Target**: `/config/*`
- **Preconditions**: 3角色账号。
- **Inputs / Test Data**: 访问5子页 + 保存动作。
- **Steps**:
  1. SUPER_ADMIN访问全部并可写。
  2. TENANT_ADMIN访问 General/Notifications；不可进 Integrations/AI/Plugins。
  3. CUSTOMER_USER仅可见允许范围（按最终实现），禁止平台级配置写。
- **Expected Results**:
  1. 页面可见性与权限矩阵一致。
  2. 不允许动作后端兜底拒绝。
- **Priority**: P0
- **Risk Covered**: 配置越权。
- **Automation Hint**: Playwright role matrix。

---

### Phase 3 — New Capabilities

#### TC-P3-001 ThreatList 多条件筛选 [Design][Impl]
- **Objective**: 验证新筛选条件正确传参并返回匹配结果。
- **Target**: `/investigate/threats`, `GET /api/v1/assessment/list`（增强参数）。
- **Preconditions**: 有多级别/多设备/多日期样本。
- **Inputs / Test Data**: level/type/source_ip/device/date range/customer/search。
- **Steps**:
  1. 组合筛选条件。
  2. 检查请求query。
  3. 核对列表结果与计数。
- **Expected Results**:
  1. 查询参数完整且正确。
  2. 返回列表符合过滤逻辑。
- **Priority**: P0
- **Risk Covered**: 调查页不可用或误过滤。
- **Automation Hint**: Playwright + fixture dataset。

#### TC-P3-002 ThreatDetail 抽屉完整性 [Design]
- **Objective**: 验证点击行可打开详情抽屉并显示 raw event 等关键信息。
- **Target**: `GET /api/v1/assessment/{id}` + Threat detail drawer。
- **Preconditions**: 存在可查看 threat id。
- **Inputs / Test Data**: 含原始事件JSON、关联alerts、device信息。
- **Steps**:
  1. 点击列表行打开抽屉。
  2. 校验模块字段。
  3. 关闭再开不同记录。
- **Expected Results**:
  1. 抽屉数据与所选行一致。
  2. 无跨记录残留数据。
- **Priority**: P0
- **Risk Covered**: 调查证据缺失/错位。
- **Automation Hint**: Playwright。

#### TC-P3-003 Threat 导出 CSV/JSON [Design]
- **Objective**: 验证导出遵循当前筛选条件且格式正确。
- **Target**: `GET /api/v1/assessment/export`。
- **Preconditions**: 已设置过滤条件。
- **Inputs / Test Data**: format=csv/json。
- **Steps**:
  1. 点击 Export CSV。
  2. 点击 Export JSON。
  3. 校验下载文件内容行数与字段。
- **Expected Results**:
  1. 导出数据与筛选结果一致。
  2. 文件结构合法（CSV列头/JSON数组对象）。
- **Priority**: P0
- **Risk Covered**: 审计导出失真。
- **Automation Hint**: Playwright download assertions。

#### TC-P3-004 Threat bulk actions 权限控制 [Design]
- **Objective**: 验证批量 dismiss/escalate/delete 按角色受控。
- **Target**: ThreatList actions + backend permission。
- **Preconditions**: 多条可操作记录。
- **Inputs / Test Data**: SUPER_ADMIN/TENANT_ADMIN/CUSTOMER_USER。
- **Steps**:
  1. 分角色尝试批量动作。
  2. 检查按钮状态和返回码。
- **Expected Results**:
  1. CUSTOMER_USER仅可读，不可执行破坏性动作。
  2. 管理角色可执行允许动作。
- **Priority**: P0
- **Risk Covered**: 写权限泄露。
- **Automation Hint**: Playwright。

#### TC-P3-005 PipelineHealth “5分钟无事件”告警 [Design]
- **Objective**: 验证 pipeline 停滞检测。
- **Target**: `pipeline.last_event_received` 呈现逻辑。
- **Preconditions**: 可控制返回时间戳。
- **Inputs / Test Data**: `last_event_received = now-6min`。
- **Steps**:
  1. 打开页面并注入数据。
  2. 观察告警区域。
- **Expected Results**:
  1. 显示 “No events received in 5 minutes” 警告。
- **Priority**: P1
- **Risk Covered**: 系统盲区不可见。
- **Automation Hint**: Playwright mocked time。

#### TC-P3-006 Overview 使用后端聚合端点 [Design]
- **Objective**: 验证分析页从客户端样本聚合切换为服务端聚合。
- **Target**: `/api/v1/assessment/stats|trend|top-ports|top-attackers`
- **Preconditions**: 聚合端点可用。
- **Inputs / Test Data**: 返回值与list样本明显不同（全量更大）。
- **Steps**:
  1. 打开Overview。
  2. 监控网络调用。
  3. 比对卡片数值与聚合响应。
- **Expected Results**:
  1. 调用聚合端点而非仅list?size=200。
  2. 指标口径与聚合结果一致。
- **Priority**: P0
- **Risk Covered**: 统计不可信。
- **Automation Hint**: Playwright network contract。

#### TC-P3-007 Analytics 准确性回归（disclaimer收敛）[Design]
- **Objective**: 验证接入后端聚合后可移除或弱化“200样本”免责声明。
- **Target**: Overview/Analytics UI。
- **Preconditions**: Option B已上线。
- **Inputs / Test Data**: 聚合端点响应。
- **Steps**:
  1. 访问页面。
  2. 检查提示文案与数据来源说明。
- **Expected Results**:
  1. 不再误导为样本统计。
  2. 数据来源描述与真实实现一致。
- **Priority**: P1
- **Risk Covered**: 旧文案与新实现冲突。
- **Automation Hint**: Playwright text assert。

---

### Phase 4 — Polish & UX

#### TC-P4-001 新导航分组与默认落地页 [Design]
- **Objective**: 验证5组导航结构与角色默认落地页。
- **Target**: `App.tsx` route config + ProLayout。
- **Preconditions**: 新路由树启用。
- **Inputs / Test Data**: 3角色登录。
- **Steps**:
  1. 分角色登录。
  2. 检查默认跳转页。
  3. 检查导航分组层级。
- **Expected Results**:
  1. 默认落地符合策略（super/tenant/customer_user映射）。
  2. 导航按 Overview/Investigate/Operate/Admin/Config 分组。
- **Priority**: P1
- **Risk Covered**: 导航混乱与角色体验退化。
- **Automation Hint**: Playwright smoke。

#### TC-P4-002 Breadcrumb 与页面标题一致性 [Design]
- **Objective**: 验证页面层级语义可追踪。
- **Target**: 全部新路由页面。
- **Preconditions**: breadcrumb启用。
- **Inputs / Test Data**: 采样路径 `/config/notifications`, `/investigate/threats`。
- **Steps**:
  1. 访问路径并读取breadcrumb。
  2. 切换子页重复检查。
- **Expected Results**:
  1. breadcrumb文本与页面标题匹配。
  2. 无遗留“Settings”笼统标题。
- **Priority**: P2
- **Risk Covered**: 信息架构不清。
- **Automation Hint**: Playwright。

#### TC-P4-003 响应式断点回归 [Design]
- **Objective**: 验证关键页在 tablet/laptop 断点不溢出。
- **Target**: Overview, Threats, CustomersAndDevices, Config pages。
- **Preconditions**: 支持 viewport 切换。
- **Inputs / Test Data**: 1366x768, 1024x768, 768x1024。
- **Steps**:
  1. 切换viewport。
  2. 检查表格/图表/表单可操作。
- **Expected Results**:
  1. 无水平遮挡关键操作按钮。
  2. 主要交互可达。
- **Priority**: P2
- **Risk Covered**: 小屏不可用。
- **Automation Hint**: Playwright screenshot diff（可选）。

#### TC-P4-004 Loading/Error/Empty state 区分 [Design]
- **Objective**: 验证数据页在三种状态下反馈准确。
- **Target**: Overview, Threats, PipelineHealth, Config pages。
- **Preconditions**: 可mock正常/空/失败。
- **Inputs / Test Data**: success-empty-error 三组响应。
- **Steps**:
  1. 注入各响应类型。
  2. 观察 skeleton/error/retry/empty 文案。
- **Expected Results**:
  1. 不出现“空数据当加载中”。
  2. 错误页可重试并恢复。
- **Priority**: P1
- **Risk Covered**: 操作员误判系统状态。
- **Automation Hint**: Playwright + mocks。

#### TC-P4-005 可访问性与键盘导航 [Design]
- **Objective**: 验证关键表单/过滤器可键盘操作，图表具备ARIA基础信息。
- **Target**: Config forms, Threat filters, Overview charts。
- **Preconditions**: A11y增强上线。
- **Inputs / Test Data**: Tab/Enter/ESC，快捷键 `R` `/`。
- **Steps**:
  1. 键盘遍历交互控件。
  2. 测试快捷键触发。
  3. 检查图表ARIA属性。
- **Expected Results**:
  1. Tab顺序合理，无焦点陷阱。
  2. 快捷键仅在允许上下文生效。
  3. 关键图表有可读标签。
- **Priority**: P2
- **Risk Covered**: 无障碍合规风险。
- **Automation Hint**: Playwright + axe-core（后续）。

---

### 附录：RBAC 3角色 × 页面/动作覆盖矩阵（当前阻塞基线）

| 页面/动作 | SUPER_ADMIN | TENANT_ADMIN | CUSTOMER_USER | 关键测试ID |
|---|---:|---:|---:|---|
| Overview 查看 | ✅ | ✅(本租户聚合) | ✅(本客户) | TC-P1-004, TC-P2-001 |
| Alerts 查看 | ✅ | ✅ | ✅(读) | TC-P1-004 |
| Alerts resolve/assign/escalate | ✅ | ✅ | ❌（当前后端读限制） | TC-P1-009 |
| Threats 查看/筛选/详情 | ✅ | ✅ | ✅(本客户) | TC-P3-001, TC-P3-002 |
| Threat delete/bulk destructive | ✅ | ✅(按策略) | ❌ | TC-P3-004 |
| Threat export | ✅ | ✅ | 按策略（建议✅读导出） | TC-P3-003 |
| Threat Intel 查看 | ✅ | ✅ | ✅/❌（按最终策略，需确认） | TC-P1-004 |
| PipelineHealth | ✅ | ✅ | ❌ | TC-P1-001, TC-P1-004 |
| ML Detection 查看 | ✅ | ✅ | ❌ | TC-P1-004 |
| ML 训练 | ✅ | ❌ | ❌ | TC-P1-004 |
| Customers&Devices CRUD | ✅ | ✅(本租户) | ❌写 | TC-P2-003, TC-P2-004 |
| Users 管理 | ✅ | ✅(本租户) | ❌ | TC-P1-004, TC-P1-005 |
| Tenants 管理 | ✅ | ❌ | ❌ | TC-P1-004, TC-P1-005 |
| Config General | ✅ | ✅ | ❌写 | TC-P2-010 |
| Config Notifications | ✅ | ✅ | 受限（目标态应细分） | TC-P2-010 |
| Config Integrations/AI/Plugins | ✅ | ❌ | ❌ | TC-P2-010 |
| Scope selector tenant/customer切换 | ✅/✅ | 固定tenant/✅customer | 固定 | TC-P1-006~008 |
| role escalation/scope crossing 防护 | 必须阻断 | 必须阻断 | 必须阻断 | TC-P1-009, TC-P1-010 |

> 注：计划中的 `customer_admin/operator/viewer` 为目标态。当前后端仅3角色时，需通过权限映射与负面用例保障“不放大权限”。

---

## 5. 建议目录布局

```text
frontend/
  tests/
    e2e/                           # Playwright
      phase1-rbac-scope.spec.ts
      phase1-pipeline-health.spec.ts
      phase2-overview-merge.spec.ts
      phase2-customers-devices.spec.ts
      phase2-config-cascade.spec.ts
      phase3-threat-investigation.spec.ts
      phase4-navigation-polish.spec.ts
      smoke-role-matrix.spec.ts
    unit/                          # Vitest + RTL
      contexts/
        auth-context.test.tsx
        scope-context.test.tsx
      guards/
        route-guard.test.tsx
        permission-gate.test.tsx
      hooks/
        use-permission.test.ts
      components/
        config-field.test.tsx
        scope-selector.test.tsx
    contract/
      system-health.contract.test.ts
      assessment-aggregation.contract.test.ts
      config-cascade.contract.test.ts
    fixtures/
      roles/
        super-admin.json
        tenant-admin.json
        customer-user.json
      data/
        assessment-sample.json
        config-cascade-sample.json
```

---

## 6. 自动化优先级

### P0（Blocking）
- TC-P1-001, 004, 005, 006, 007, 008, 009, 010
- TC-P2-001, 003, 004, 005, 006, 007, 008, 010
- TC-P3-001, 002, 003, 004, 006

### P1（High）
- TC-P1-002, 003, 011, 012
- TC-P2-002, 009
- TC-P3-005, 007
- TC-P4-001, 004

### P2（Medium）
- TC-P4-002, 003, 005

---

## 7. 不确定项 / 待确认项

1. **角色模型差异**: 计划文档为5角色，当前后端实现为3角色。需确认过渡期映射（尤其 `operator/viewer/customer_admin` 到 `CUSTOMER_USER` 的细分策略）。
2. **Threat Intel 对 CUSTOMER_USER 的最终可见性**: 计划目标对 viewer/operator有差异，但当前实现无细分。
3. **Alerts 动作权限**: 当前网关对 `CUSTOMER_USER` 统一读限制；若目标态需要 operator 可处理告警，需后端新增角色/permission claim 后再补用例。
4. **Config API 字段契约**: `{ value, source, locked }` 与 push/lock/override 端点目前为计划项，需确认最终响应结构与错误码规范。
5. **Overview 最终数据来源切换时点**: Phase1 临时 disclaimer 与 Phase3 服务端聚合并存阶段，需要明确切换判定条件（避免测试期望冲突）。
6. **默认落地页策略在3角色下映射**: 计划含5角色默认页，当前3角色需定义 CUSTOMER_USER 默认页（Overview/Alerts）。
