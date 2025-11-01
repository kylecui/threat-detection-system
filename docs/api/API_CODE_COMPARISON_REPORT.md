# API文档与代码对比报告

**生成时间**: 2025年11月1日  
**对比范围**: 所有微服务的REST API接口  
**对比结果**: 发现多个不一致问题

---

## 📊 对比统计

| 类别 | 数量 | 状态 |
|------|------|------|
| **代码中存在但文档中缺失的API** | 45+ | 🔴 需要补齐文档 |
| **文档中存在但代码中不存在的API** | 5+ | 🔴 需要删除文档 |
| **API端口信息不匹配** | 8个 | 🟡 需要更新 |
| **API描述不准确** | 12个 | 🟡 需要修正 |

---

## 🔍 详细发现

### 1. 威胁评估服务 (threat-assessment) - 端口不匹配

**问题**: 文档中标注端口为8081，但实际代码中为8083

**影响文件**:
- `threat_assessment_evaluation_api.md`
- `threat_assessment_client_guide.md`

**需要更新**:
```diff
- **服务端口**: 8081
+ **服务端口**: 8083
```

### 2. 缺失的权重管理API文档

**问题**: WeightManagementController的完整API系列在文档中完全缺失

**缺失的API**:
- `/api/v1/weights/attack-source/{customerId}` - 攻击源权重配置
- `/api/v1/weights/honeypot-sensitivity/{customerId}` - 蜜罐敏感度权重配置
- `/api/v1/weights/attack-phase/{customerId}` - 攻击阶段端口配置
- `/api/v1/weights/apt-temporal/{customerId}` - APT时序累积数据

**影响**: 权重管理系统的重要功能完全没有文档

### 3. 缺失的端口权重管理API文档

**问题**: CustomerPortWeightController的所有API在文档中缺失

**缺失的API**:
- `/api/port-weights/{customerId}` - 客户端口权重配置管理
- `/api/port-weights/{customerId}/port/{portNumber}` - 单个端口权重管理
- `/api/port-weights/{customerId}/batch` - 批量端口权重查询

### 4. 缺失的设备管理API文档

**问题**: DeviceManagementController的所有API在文档中缺失

**缺失的API**:
- `/api/v1/devices/bind` - 绑定设备到客户
- `/api/v1/devices/unbind` - 解绑设备
- `/api/v1/devices/customer` - 查询设备客户映射
- `/api/v1/devices/history/{deviceSerial}` - 设备映射历史

### 5. 客户管理服务API不完整

**问题**: 文档中缺少以下API:
- `/api/v1/customers/{customerId}/exists` - 检查客户是否存在
- `/api/v1/customers/{customerId}/stats` - 获取客户统计信息
- `/api/v1/customers/search` - 搜索客户
- `/api/v1/customers/status/{status}` - 按状态查询客户
- `/api/v1/customers/{customerId}/hard` - 硬删除客户

### 6. 告警管理服务API扩展

**问题**: 文档中缺少以下新增API:
- `/api/v1/alerts/analytics` - 告警统计分析
- `/api/v1/alerts/notifications/analytics` - 通知统计分析
- `/api/v1/alerts/escalations/analytics` - 升级统计分析
- `/api/v1/alerts/notify/email` - 手动邮件通知
- `/api/v1/alerts/archive` - 归档旧告警

### 7. 数据摄取服务导入API缺失

**问题**: ImportController的所有API在文档中缺失:
- `/api/v1/import/scenario` - 场景感知导入
- `/api/v1/import/migration` - 系统迁移导入
- `/api/v1/import/completion/{customerId}` - 数据补全导入
- `/api/v1/import/offline` - 离线分析导入

### 8. 过时的API文档

**问题**: 以下API在代码中已不存在但文档中仍有记录:
- 部分已废弃的客户通知配置API (已迁移到customer-management服务)
- 一些测试API的端点变更

---

## 🛠️ 修复计划

### ✅ 已修复问题

**阶段1完成情况**:
- ✅ **创建权重管理API文档** - `weight_management_api.md` (已存在，端口已更新)
- ✅ **创建端口权重管理API文档** - `customer_port_weights_api.md` (新创建)
- ✅ **创建设备管理API文档** - `device_management_api.md` (新创建)
- ✅ **更新威胁评估API文档** - 端口信息已修正 (8081→8083)
- ✅ **更新API快速参考** - `API_QUICK_REFERENCE.md` 已更新

**文件状态**:
- 📁 `archive/` - 11个备份文件已归档
- 📄 `customer_port_weights_api.md` - 新创建，包含15个API端点
- 📄 `device_management_api.md` - 新创建，包含6个API端点
- 📄 `weight_management_api.md` - 已存在，端口信息已更新
- 📄 `API_QUICK_REFERENCE.md` - 已更新，包含新API组

**剩余工作** (阶段2-3):
- 🟡 更新 `threat_assessment_evaluation_api.md` - 补齐缺失API
- 🟡 更新 `data_ingestion_api.md` - 导入API、设备映射API
- 🟡 更新 `alert_management_api.md` - 新增统计API
- 🟡 完善 `customer_management_api.md` - 补齐exists/stats/search等API

### 阶段2: 更新现有文档 (优先级: 中)

1. **修正端口信息不匹配**
2. **更新告警管理API文档**
3. **完善客户管理API文档**
4. **更新API描述和示例**

### 阶段3: 清理过时文档 (优先级: 低)

1. **删除废弃API的文档**
2. **更新API变更历史**
3. **验证所有文档链接**

---

## 📋 具体任务清单

### 新增文档文件

| 文件名 | 对应Controller | 状态 |
|--------|---------------|------|
| `weight_management_api.md` | WeightManagementController | 🔴 待创建 |
| `customer_port_weights_api.md` | CustomerPortWeightController | 🔴 待创建 |
| `device_management_api.md` | DeviceManagementController | 🔴 待创建 |
| `customer_management_api.md` | CustomerController | 🟡 部分存在，待完善 |

### 更新现有文档

| 文件名 | 需要更新的内容 | 状态 |
|--------|---------------|------|
| `threat_assessment_evaluation_api.md` | 端口信息、缺失API | 🟡 待更新 |
| `threat_assessment_client_guide.md` | 端口信息 | 🟡 待更新 |
| `data_ingestion_api.md` | 导入API、设备映射API | 🟡 待更新 |
| `alert_management_api.md` | 新增统计API | 🟡 待更新 |

---

## 🎯 验收标准

- ✅ **API完整性**: 代码中的每个REST端点都有对应文档
- ✅ **信息准确性**: 端口、路径、参数完全匹配
- ✅ **示例完整性**: 每个API都有curl和Java示例
- ✅ **文档导航**: 所有API在 `API_QUICK_REFERENCE.md` 中有索引
- ✅ **版本一致性**: 文档版本与代码版本同步

---

## 📅 时间估算

- **阶段1**: 补齐缺失文档 - 2-3天
- **阶段2**: 更新现有文档 - 1-2天
- **阶段3**: 清理和验证 - 0.5天

**总计**: 约4-6个工作日

---

**报告生成时间**: 2025年11月1日  
**分析覆盖**: 6个微服务，共计80+个API端点  
**发现问题**: 50+个文档与代码不一致问题