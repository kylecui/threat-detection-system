# 3.1.27 威胁列表攻击者 IP 修复

## 发现的问题

威胁列表详情视图和分组视图展开后的每条评估记录都显示攻击者 IP 为 `N/A`，导出与总览最近威胁也复用同一错误响应。

## 找到的原因

运行库检查确认 `attack_events.attack_ip` 与 `threat_assessments.attack_ip` 均有真实值，146 条评估记录无一缺失。问题位于 `ThreatQueryService.convertToDetailResponse()`：代码没有读取实体字段，而是无条件执行 `response.setAttackIp("N/A")`。所有复用 `ThreatAssessmentDetailResponse` 的接口因此同时丢失真实 IP。

## 修改

- 响应转换改为使用 `assessment.getAttackIp()`。
- 不在前端伪造或反查 IP，数据库为空时仍保持真实空值语义。
- 新增查询服务单元测试，验证持久化攻击者 IP 原样进入 API DTO。
- 页面列表、分组展开、租户列表、总览最近威胁和 CSV/JSON 导出通过同一字段自动恢复。
