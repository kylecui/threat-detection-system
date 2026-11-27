# 3.1.31 租户总览原生 SQL 兼容性修复

## 现象

威胁总览的 24 小时、7 天和 30 天请求在租户范围下均返回 HTTP 500，前端显示“加载总览数据失败”。

## 根因

`ThreatAssessmentRepository` 的非端口协议原生查询使用 PostgreSQL `::int`。Hibernate 将双冒号中的 `:int` 当作命名参数处理，数据库实际收到非法 SQL 并返回 `SQLState 42601`。旧筛选条件还只覆盖小于 1 或大于 65535 的端口，没有覆盖 `NULL` 端口。

## 修改

- 客户和租户查询统一使用 `CAST(... AS integer)`。
- 非端口事件使用 `COALESCE(response_port, 0) NOT BETWEEN 1 AND 65535` 识别。
- 增加回归契约，禁止再次使用 `::int` 并固定空端口分类行为。

## 边界

本次不修改总览 API、前端调用参数、聚合窗口、租户权限或历史业务数据，只修复原生 SQL 的兼容性与分类完整性。
