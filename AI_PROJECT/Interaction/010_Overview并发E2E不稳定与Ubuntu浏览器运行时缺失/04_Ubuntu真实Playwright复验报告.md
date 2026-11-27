# INTERACTION-010 Ubuntu 真实 Playwright 复验报告

日期：2026-10-28（报告时间戳为 UTC 2026-10-29）  
执行主机：`jzzn@192.168.100.9`（Ubuntu 24.04 x86_64）  
源码目录：`/home/jzzn/tds-3.1.38-final-nLocg6/source`  
最终结论：**不通过**

## 1. 运行环境确认

- 固定镜像：`mcr.microsoft.com/playwright:v1.59.1-noble`。
- 镜像 ID：`sha256:b0ab6f3cb99aa7803adbc14d9027ec1785fc6e433b97e134e0f8fe61683b6b53`。
- Playwright：`1.59.1`。
- Chromium revision：`1217`（`147.0.7727.15`）。
- 每次运行均执行干净的 `npm ci`，安装 667 个依赖。
- 测试容器内 `/dev/shm` 为 1 GiB；执行前宿主机约有 4 GiB 可用磁盘、3 GiB 可用内存。
- 三次 `run-info.txt` 均记录了相同的固定镜像 ID 和 `exit_status=1`。这说明此前的“浏览器运行时缺失”已经解决，本次失败来自实际测试结果，而不是镜像下载或浏览器启动失败。

## 2. 三组测试结果

| 组别 | 实际命令参数 | 结果 | 报告目录 |
| --- | --- | --- | --- |
| Overview 定向并发重复 | `e2e/overview-trend.spec.ts --workers=4 --repeat-each=3` | 5 通过，4 失败，共 9 项，56.8 秒 | `outputs/e2e-reports/20261029T020727Z/` |
| 四个关键 spec 组合 | `e2e/responsive-layout.spec.ts e2e/overview-trend.spec.ts e2e/table-interactions.spec.ts e2e/api-mocks.spec.ts --workers=4` | 24 通过，6 失败，共 30 项，120.1 秒 | `outputs/e2e-reports/20261029T020944Z/` |
| 完整 4-worker 回归 | `--workers=4` | 119 通过，44 失败，共 163 项，578.5 秒 | `outputs/e2e-reports/20261029T021345Z/` |

三个 `results.json` 已从 Ubuntu 回传本机，传输前后 SHA-256 一致：

- `20261029T020727Z/results.json`：`318841875e9b64887a7fd361bfac267348dcef417bff0d3ac208ca4e182ef54e`。
- `20261029T020944Z/results.json`：`5e4a42ee2702f4f115706dc64c7188bbc653b5eb1dce541023734fccdcba845f`。
- `20261029T021345Z/results.json`：`fc9c20018dfeff209915d3f01cf7131d59913c97a47e6d2ec2da0be84fe42558`。

每个本地报告目录均包含 `run-info.txt`、`console.log`、`results.json`、失败截图/错误上下文和 `html/index.html`。

## 3. 报告检查结果

### 3.1 Overview 定向回归仍然不稳定

4 个失败全部是等待初始 `hours=24` 的 overview snapshot 响应超过 15 秒。失败截图和无障碍快照显示页面仍停留在全局安全盾牌 Loading 页面。

同一个 spec 的 3 个用例各重复 3 次，最终出现 5 次成功、4 次失败，证明它不是稳定的接口契约错误，而是冷启动/并发时序问题。测试代码只在 `page.goto('/overview')` 前注册响应监听，但 Vite 首次编译路由模块、Scope 初始化和页面首次请求均计入固定 15 秒窗口；4 worker 同时冷启动时可能在请求真正发出前就耗尽等待时间。

### 3.2 组合回归暴露了共享的页面就绪问题

6 个失败分别是：

- 3 个 Overview 用例全部在初始 snapshot 响应处超时，并停在全局 Loading。
- 设备数据流窄屏用例在断言时未找到表格行；失败截图稍后已经出现行和加载指示，属于断言与异步数据加载竞争。
- Top 攻击者排序用例断言时得到 0 行，截图仍处于 Overview 骨架屏。
- 设备抽屉操作用例断言时未找到首行，截图显示页面仍在加载数据。

这说明问题不只存在于 `overview-trend.spec.ts`，多个用例都把“导航完成”当成“认证、数据范围、接口数据和页面渲染全部完成”。

### 3.3 完整回归的 44 个失败

失败可分为以下几类：

1. **28 个控制台告警门禁失败**  
   `table-layout.spec.ts` 的表格、行数、布局检查已经执行，页面截图也正常渲染，但 React StrictMode 输出 Ant Design/ProTable/Tooltip/ResizeObserver 的 `findDOMNode is deprecated`，被 `collectErrors()` 作为未允许的 `console.error` 收集，最终使测试失败。当前 allowlist 只允许 `[antd: ...]` 格式的弃用提示，不包含该 React StrictMode 警告。
2. **7 个全局 Loading 超时**  
   包含登出、空数据、HTTP 错误、网络超时、分组排序、主路由访问和侧边栏固定位置等用例。错误上下文均显示全局 Loading 页面，属于应用启动/Scope 初始化尚未完成时测试已经进入断言。
3. **2 个 Overview 移动端结构失败**  
   `overview.spec.ts` 和 `table-layout.spec.ts` 都确认在 390px 下找不到预期的 `.tds-overview__mobile-list`。截图显示移动端 Overview 主体已正常渲染，因此这不是全局启动超时，而是当前 DOM 与测试声明的“桌面表格替换为移动卡片”契约不一致。
4. **2 个表格数据就绪竞争**  
   Top 攻击者排序得到 0 行，设备抽屉操作找不到首行；截图分别显示骨架屏或仍在加载中的列表。
5. **5 个已渲染页面上的断言/交互不匹配**  
   涉及 CUSTOMER_USER Overview 标题、全部工作区子菜单动画、横向查询标签、菜单高亮指示器和语言切换。部分截图已经出现完整页面，不能统一归因于浏览器运行时或全局 Loading，需逐项复现并校准页面就绪条件、可访问名称或组件结构。

完整回归中 `overview-trend.spec.ts` 的 3 项反而全部通过，而同一组用例在前两轮分别失败 4/9 和 3/3。这进一步证明现有 15 秒固定窗口受 Vite 冷编译及并发启动影响：完整套件运行到 Overview 时相关模块已经预热，但测试本身仍不具备独立、稳定执行的条件。

## 4. 建议整改顺序

1. 建立公共 `waitForAppReady()`：明确等待全局 Loading 消失、Scope 初始化结束，并允许页面声明其关键接口/关键容器的就绪条件；不要只依赖 `page.goto()` 或固定延时。
2. Overview 定向测试应将“开发服务器冷模块编译”和“业务 snapshot 响应”拆开同步。可以在正式计时前预热 `/overview` 路由，或让 runner 在测试开始前完成受控的路由编译；业务断言仍需等待精确响应，不能用扩大超时掩盖问题。
3. 表格交互用例统一复用“无 spinning、至少一行并稳定一段时间”的等待方法，避免各 spec 自行立即查找首行。
4. 对 `findDOMNode` 警告优先升级或替换触发警告的 Ant Design/Pro Components 依赖；若确认只能在开发 StrictMode 中由第三方依赖产生，可增加精确到消息和调用栈的临时 allowlist，并建立后续移除任务，不能放宽为忽略全部 console error。
5. 独立修复或更新 Overview 390px 的移动卡片契约；`overview.spec.ts` 和 `table-layout.spec.ts` 应使用同一公共 surface 定义，避免重复但不一致的选择器。
6. 对其余 5 个已渲染页面失败逐项确认产品 DOM 与测试契约：优先使用稳定的 `data-testid`/可访问名称，并在动作前等待具体组件就绪。

### 后续代码级排查修正（2026-10-28）

进一步对照 `frontend/src/pages/Overview/index.tsx` 与失败时序后，需修正第 3.3 节第 3 类的初步判断：两组 `.tds-overview__mobile-list` 实际位于 Overview 已加载分支（约第 592、710 行），390px CSS 也会将其显示；`loading` 骨架分支（约第 400–425 行）不包含该节点。相关测试在 `reload()`/导航后没有等待骨架退出，失败截图又是在断言超时后采集，因此截图中稍后出现完整页面不能证明断言时移动列表已经挂载。

故这 2 个失败应归入“页面就绪同步缺失”，不应按“移动端结构缺失”修改产品 DOM。完整 44 个失败经代码级复核后的归类为：14 个应用/页面就绪竞争、28 个 StrictMode 第三方弃用告警、1 个 ProTable 查询表单旧选择器、1 个四工作区动效用例总超时。对应后续分析记录为 `INTERACTION-011` 至 `INTERACTION-014`。

## 5. 验收结论

- Playwright 1.59.1 Ubuntu 浏览器运行时：**通过**。
- 三组命令是否真实执行并生成完整报告：**通过**。
- Overview 4-worker 并发稳定性：**不通过**。
- 完整 4-worker 前端 E2E：**不通过**。
- `INTERACTION-010`：**不能关闭**。

本轮只执行测试、检查报告和补充记录，没有修改前端源码，也没有重新打包或部署。既有 3.1.38 安装归档不包含这些开发报告，其哈希不受影响。
