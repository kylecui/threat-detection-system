# API文档补充计划

**创建日期**: 2025-10-16  
**状态**: 规划中  
**参考标准**: `email_notification_configuration.md` (完整版)

---

## 📋 当前状态

### ✅ 已完成 (详细文档)

| 文档 | 行数 | 状态 | 说明 |
|------|------|------|------|
| `email_notification_configuration.md` | ~1000 | ✅ 完整 | 包含完整目录、架构、示例、故障排查 |
| `data_ingestion_api.md` | 1595 | ✅ 完整 | 包含完整目录、架构、示例、故障排查 |

### ⏳ 待补充 (精简版 → 完整版)

| 文档 | 当前行数 | 目标行数 | 优先级 | 预计时间 |
|------|---------|---------|--------|---------|
| **核心API** |
| `alert_crud_api.md` | 171 | ~800 | P0 | 2小时 |
| `alert_lifecycle_api.md` | 165 | ~800 | P0 | 2小时 |
| `threat_assessment_evaluation_api.md` | 698 | ~900 | P1 | 1.5小时 |
| `threat_assessment_query_api.md` | 348 | ~700 | P1 | 1.5小时 |
| **分析和管理** |
| `alert_analytics_api.md` | 163 | ~600 | P2 | 1.5小时 |
| `alert_escalation_api.md` | 127 | ~500 | P2 | 1小时 |
| `alert_notification_api.md` | 285 | ~600 | P2 | 1小时 |
| **维护和测试** |
| `alert_maintenance_api.md` | 232 | ~500 | P3 | 1小时 |
| `integration_test_api.md` | 403 | ~600 | P3 | 1小时 |
| **指南文档** |
| `threat_assessment_client_guide.md` | 210 | ~700 | P2 | 1.5小时 |

### ✅ 无需补充 (概述文档)

| 文档 | 行数 | 说明 |
|------|------|------|
| `alert_management_overview.md` | 91 | 概述文档,保持简洁 |
| `threat_assessment_overview.md` | 430 | 已较完整 |

---

## 📐 补充标准

### 参考模板: `email_notification_configuration.md`

#### 必需章节

1. **文档头部**
   - 服务名称、端口、版本、更新日期
   - 完整目录 (TOC)

2. **系统概述** (~200行)
   - 核心特性列表
   - 工作流程图 (ASCII或文字描述)
   - 架构组件说明

3. **API端点详细文档** (每个端点 ~150行)
   - 端点信息表格
   - 请求参数详解 (表格形式)
   - curl示例 (至少2个场景)
   - Java示例 (完整可运行代码)
   - 响应示例 (成功和失败)
   - 错误码表格

4. **数据模型** (~100行)
   - 请求/响应DTO定义
   - 字段说明表格
   - JSON Schema示例

5. **使用场景** (~300行)
   - 至少3个真实场景
   - 场景描述 + 完整代码实现
   - 预期效果说明

6. **Java客户端示例** (~200行)
   - 完整客户端类
   - 使用示例main方法
   - 最佳实践说明

7. **最佳实践** (~150行)
   - 推荐做法 (✅)
   - 避免事项 (❌)
   - 性能优化建议
   - 安全性建议

8. **故障排查** (~200行)
   - 常见问题列表
   - 排查步骤 (shell/Java示例)
   - 解决方案

9. **附录** (~100行)
   - 配置参考
   - 版本历史
   - 相关文档链接

#### 代码示例规范

**curl示例**:
```bash
# 场景描述
curl -X POST http://localhost:8082/api/endpoint \
  -H "Content-Type: application/json" \
  -d '{
    "field": "value"
  }'
```

**Java示例**:
```java
/**
 * 方法描述 (Javadoc注释)
 */
public ReturnType methodName(ParamType param) {
    // 1. 构建请求
    Request request = new Request();
    request.setField(value);
    
    // 2. 发送请求
    ResponseEntity<Response> response = restTemplate.postForEntity(
        url,
        new HttpEntity<>(request, headers),
        Response.class
    );
    
    // 3. 处理响应
    return response.getBody();
}
```

#### 表格规范

**统一表格样式**:
```markdown
| 字段 | 类型 | 必需 | 说明 |
|-----|------|------|------|
| `fieldName` | String | ✅ | 字段说明 |
```

#### 响应示例规范

**成功响应**:
```json
{
  "field": "value",
  "nestedObject": {
    "field": "value"
  },
  "array": ["item1", "item2"]
}
```

**失败响应**:
```json
{
  "timestamp": "2025-01-15T02:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "详细错误信息",
  "errors": [
    {
      "field": "fieldName",
      "message": "验证错误信息"
    }
  ]
}
```

---

## 🎯 补充优先级

### P0 - 立即补充 (核心CRUD操作)

**目标**: 保证核心API文档完整,支持开发者快速上手

1. **alert_crud_api.md**
   - 补充内容: 完整的创建/查询/更新/删除示例
   - 新增: 批量操作、过滤器详解、分页最佳实践
   - 场景: 告警创建工作流、批量处理、高级查询

2. **alert_lifecycle_api.md**
   - 补充内容: 状态转换详解、工作流图
   - 新增: 状态机说明、并发控制、审计日志
   - 场景: 完整生命周期管理、自动化处理

### P1 - 尽快补充 (核心业务逻辑)

**目标**: 完善核心业务逻辑文档

3. **threat_assessment_evaluation_api.md**
   - 补充内容: 评分算法详解、权重计算示例
   - 新增: 性能优化、批量评估、缓存策略
   - 场景: 实时评估、批量回溯分析、性能调优

4. **threat_assessment_query_api.md**
   - 补充内容: 趋势分析算法、聚合统计
   - 新增: 时间序列查询、多维度分析
   - 场景: 威胁趋势图表、历史对比、报表生成

### P2 - 逐步补充 (增强功能)

**目标**: 提供完整的功能覆盖

5. **alert_analytics_api.md**
   - 补充: 统计算法、聚合查询、可视化建议
   - 场景: Dashboard数据源、报表生成、趋势预测

6. **alert_escalation_api.md**
   - 补充: 升级规则配置、通知流程、SLA管理
   - 场景: 自动升级、值班轮换、紧急响应

7. **alert_notification_api.md**
   - 补充: 多渠道通知、模板配置、失败重试
   - 场景: 邮件/SMS/Webhook、模板定制、可靠性保证

8. **threat_assessment_client_guide.md**
   - 补充: 完整客户端实现、连接池配置、重试策略
   - 场景: 生产级客户端、高可用配置、监控集成

### P3 - 选择性补充 (维护和测试)

**目标**: 提供运维和测试支持

9. **alert_maintenance_api.md**
   - 补充: 归档策略、清理规则、性能优化
   - 场景: 定期维护、存储优化、历史数据管理

10. **integration_test_api.md**
    - 补充: 端到端测试流程、Mock数据生成
    - 场景: 集成测试、性能测试、压力测试

---

## 📝 补充执行计划

### Phase 1: 核心API (Week 1)

**目标**: 完成P0优先级文档

| 任务 | 文档 | 计划时间 | 责任人 |
|------|------|---------|--------|
| Day 1-2 | `alert_crud_api.md` | 4小时 | TBD |
| Day 3-4 | `alert_lifecycle_api.md` | 4小时 | TBD |
| Day 5 | 审查和测试 | 2小时 | TBD |

**交付物**:
- ✅ 2个完整API文档
- ✅ 可运行的Java示例代码
- ✅ 测试验证通过

---

### Phase 2: 业务逻辑 (Week 2)

**目标**: 完成P1优先级文档

| 任务 | 文档 | 计划时间 |
|------|------|---------|
| Day 1-2 | `threat_assessment_evaluation_api.md` | 3小时 |
| Day 3-4 | `threat_assessment_query_api.md` | 3小时 |
| Day 5 | 审查和测试 | 2小时 |

**交付物**:
- ✅ 2个完整API文档
- ✅ 评分算法详解
- ✅ 性能优化建议

---

### Phase 3: 增强功能 (Week 3-4)

**目标**: 完成P2优先级文档

| 任务 | 文档 | 计划时间 |
|------|------|---------|
| Week 3 | `alert_analytics_api.md`, `alert_escalation_api.md`, `alert_notification_api.md` | 8小时 |
| Week 4 | `threat_assessment_client_guide.md` | 3小时 |
| Week 4 | 审查和完善 | 3小时 |

**交付物**:
- ✅ 4个完整文档
- ✅ 完整客户端实现
- ✅ 最佳实践指南

---

### Phase 4: 维护和测试 (Week 5, 可选)

**目标**: 完成P3优先级文档

| 任务 | 文档 | 计划时间 |
|------|------|---------|
| Week 5 | `alert_maintenance_api.md`, `integration_test_api.md` | 4小时 |

---

## 🔧 补充工具和模板

### 文档生成器脚本 (Python)

```python
#!/usr/bin/env python3
"""
API文档补充助手
基于模板和参考文档生成完整API文档
"""

import sys
from pathlib import Path

TEMPLATE = """# {title}

**服务名称**: {service_name}  
**服务端口**: {port}  
**版本**: 1.0  
**更新日期**: 2025-10-16

---

## 目录

1. [系统概述](#系统概述)
2. [核心功能](#核心功能)
3. [API端点列表](#api端点列表)
4. [API详细文档](#api详细文档)
5. [请求/响应模型](#请求响应模型)
6. [使用场景](#使用场景)
7. [Java客户端示例](#java客户端示例)
8. [最佳实践](#最佳实践)
9. [故障排查](#故障排查)
10. [相关文档](#相关文档)

---

## 系统概述

### 核心职责

[待补充: 服务职责描述]

### 工作流程

```
[待补充: 工作流程图]
```

[... 其余章节待补充 ...]
"""

def generate_doc_template(doc_name: str, service_name: str, port: int):
    """生成文档模板"""
    title = doc_name.replace('_', ' ').title()
    
    content = TEMPLATE.format(
        title=title,
        service_name=service_name,
        port=port
    )
    
    output_path = Path(f"{doc_name}_TEMPLATE.md")
    output_path.write_text(content)
    print(f"✅ Template generated: {output_path}")

if __name__ == "__main__":
    # 使用示例:
    # python generate_template.py alert_crud_api "Alert Management Service" 8082
    if len(sys.argv) != 4:
        print("Usage: python generate_template.py <doc_name> <service_name> <port>")
        sys.exit(1)
    
    generate_doc_template(sys.argv[1], sys.argv[2], int(sys.argv[3]))
```

### 代码示例验证脚本 (Shell)

```bash
#!/bin/bash
# 验证文档中的代码示例是否可编译

DOC_FILE="$1"

echo "Validating code examples in $DOC_FILE..."

# 提取Java代码块
sed -n '/```java/,/```/p' "$DOC_FILE" > /tmp/code_examples.java

# 简单语法检查 (需要安装javac)
if command -v javac &> /dev/null; then
    javac -Xlint:unchecked /tmp/code_examples.java 2>&1 | grep -i error
    if [ $? -eq 0 ]; then
        echo "❌ Java code has compilation errors"
        exit 1
    else
        echo "✅ Java code syntax OK"
    fi
else
    echo "⚠️ javac not found, skipping compilation check"
fi

# 提取curl示例并检查格式
grep -E "^curl " "$DOC_FILE" > /tmp/curl_examples.txt
if [ -s /tmp/curl_examples.txt ]; then
    echo "✅ Found $(wc -l < /tmp/curl_examples.txt) curl examples"
else
    echo "⚠️ No curl examples found"
fi

echo "Validation complete!"
```

---

## 📊 进度跟踪

### 当前进度

| Phase | 状态 | 完成度 | 预计完成日期 |
|-------|------|--------|-------------|
| Phase 1 | ⏳ 待开始 | 0% | 2025-10-23 |
| Phase 2 | ⏳ 待开始 | 0% | 2025-10-30 |
| Phase 3 | ⏳ 待开始 | 0% | 2025-11-13 |
| Phase 4 | ⏳ 待开始 | 0% | 2025-11-20 |

### 检查清单

#### 文档质量检查

- [ ] 目录完整且链接有效
- [ ] 所有API端点都有完整文档
- [ ] 每个端点至少有2个curl示例
- [ ] 每个端点至少有1个完整Java示例
- [ ] 所有Java代码可编译通过
- [ ] 包含至少3个使用场景
- [ ] 故障排查章节完整
- [ ] 所有表格格式统一
- [ ] 响应示例包含成功和失败情况
- [ ] 相关文档链接有效

#### 代码质量检查

- [ ] Java代码符合命名规范
- [ ] 包含完整的Javadoc注释
- [ ] 异常处理完善
- [ ] 使用Spring Boot最佳实践
- [ ] curl示例格式正确
- [ ] 所有JSON格式正确

---

## 🔗 参考资源

### 内部文档

- **标准参考**: [email_notification_configuration.md](./email_notification_configuration.md)
- **数据摄取**: [data_ingestion_api.md](./data_ingestion_api.md)
- **项目标准**: [PROJECT_STANDARDS.md](../design/PROJECT_STANDARDS.md)

### 外部资源

- [Spring Boot REST API最佳实践](https://spring.io/guides/tutorials/rest/)
- [OpenAPI 3.0规范](https://swagger.io/specification/)
- [Markdown语法指南](https://www.markdownguide.org/)

---

## 📞 联系方式

**文档维护**: GitHub Copilot  
**审核负责人**: TBD  
**更新频率**: 每周审查,按需更新

---

**文档创建**: 2025-10-16  
**最后更新**: 2025-10-16  
**状态**: 🔄 规划中
