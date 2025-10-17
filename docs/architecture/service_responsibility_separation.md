# Customer Management 与 Alert Management 服务职责分离方案

**版本**: 1.0  
**日期**: 2025-10-16  
**状态**: APPROVED ✅

---

## 📋 架构原则

### 单一职责原则 (SRP)

| 服务 | 职责 | 权限 |
|------|------|------|
| **Customer-Management** | 客户配置管理 | **读写** (CRUD) |
| **Alert-Management** | 告警通知发送 | **只读** (Read-Only) |

---

## 🎯 职责划分

### Customer-Management Service (端口: 8084)

**核心职责**: 客户信息和配置的**管理中心**

#### 管理范围
1. ✅ 客户基础信息 (customers表)
2. ✅ 设备绑定关系 (device_customer_mapping表)
3. ✅ 通知配置 (customer_notification_configs表)

#### API权限
- **完整CRUD权限**:
  - ✅ CREATE: 创建新配置
  - ✅ READ: 查询配置
  - ✅ UPDATE: 更新配置
  - ✅ DELETE: 删除配置

#### 使用场景
- 管理员配置客户通知规则
- 客户自助修改通知设置
- 系统集成配置管理
- 批量配置导入/导出

#### API端点示例
```
POST   /api/v1/customers                           # 创建客户
GET    /api/v1/customers/{id}                      # 查询客户
PUT    /api/v1/customers/{id}                      # 更新客户
DELETE /api/v1/customers/{id}                      # 删除客户

POST   /api/v1/customers/{id}/devices              # 绑定设备
GET    /api/v1/customers/{id}/devices              # 查询设备
DELETE /api/v1/customers/{id}/devices/{devSerial}  # 解绑设备

GET    /api/v1/customers/{id}/notification-config  # 获取通知配置
PUT    /api/v1/customers/{id}/notification-config  # 更新通知配置
PATCH  /api/v1/customers/{id}/notification-config/email/toggle  # 切换邮件通知
```

---

### Alert-Management Service (端口: 8082)

**核心职责**: 告警处理和通知**执行引擎**

#### 执行范围
1. ✅ 消费Kafka威胁告警事件
2. ✅ 读取客户通知配置
3. ✅ 执行通知发送逻辑
4. ✅ 记录通知历史

#### API权限
- **只读权限** (Read-Only):
  - ✅ READ: 读取配置用于通知发送
  - ❌ CREATE: 不应创建配置
  - ❌ UPDATE: 不应修改配置
  - ❌ DELETE: 不应删除配置

#### 使用场景
- Kafka消费者读取配置发送通知
- 系统内部服务读取配置
- 监控服务查询配置状态

#### 保留的只读API（用于内部查询）
```
GET /api/notification-config/customer/{customerId}  # 内部使用：读取配置
```

#### 应移除的写权限API（不应暴露）
```
❌ POST   /api/notification-config/customer         # 应移除
❌ PUT    /api/notification-config/customer/{id}    # 应移除
❌ DELETE /api/notification-config/customer/{id}    # 应移除
```

---

## 🔒 安全性考虑

### 最小权限原则

**问题**: 如果Alert-Management拥有写权限：
- ❌ 可能被误用修改配置
- ❌ 增加安全风险面
- ❌ 职责不清晰
- ❌ 难以审计配置变更来源

**解决方案**: Alert-Management只保留只读权限：
- ✅ 只能读取配置
- ✅ 不能修改配置
- ✅ 职责清晰
- ✅ 配置变更来源明确（只能通过Customer-Management）

### 数据库访问权限

建议在数据库层面也实施权限隔离：

```sql
-- Customer-Management数据库用户: 完整权限
CREATE USER customer_mgmt_user WITH PASSWORD 'secure_password';
GRANT SELECT, INSERT, UPDATE, DELETE ON customers TO customer_mgmt_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON device_customer_mapping TO customer_mgmt_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON customer_notification_configs TO customer_mgmt_user;

-- Alert-Management数据库用户: 只读权限
CREATE USER alert_mgmt_user WITH PASSWORD 'secure_password';
GRANT SELECT ON customer_notification_configs TO alert_mgmt_user;  -- 只读
GRANT SELECT ON device_customer_mapping TO alert_mgmt_user;        -- 只读
GRANT SELECT, INSERT, UPDATE ON notifications TO alert_mgmt_user;  -- 通知历史表有写权限
GRANT SELECT, INSERT, UPDATE ON notification_rate_limits TO alert_mgmt_user;  -- 频率限制表有写权限
```

---

## 🔄 数据流设计

### 配置管理流程

```
管理员/客户
    ↓
Customer-Management API (8084)
    ↓
PostgreSQL: customer_notification_configs (写入)
```

### 通知发送流程

```
威胁检测 → Kafka (threat-alerts)
    ↓
Alert-Management (消费者)
    ↓
PostgreSQL: customer_notification_configs (只读)
    ↓
应用通知规则 + 频率限制 + 静默时段
    ↓
发送通知 (Email/SMS/Slack/Webhook)
    ↓
PostgreSQL: notifications (写入历史记录)
```

---

## 📡 API Gateway 路由配置

### 推荐配置

```yaml
# API Gateway (如Kong/Nginx)配置示例

# 客户管理相关 → Customer-Management
location /api/v1/customers {
    proxy_pass http://customer-management:8084;
}

# 通知发送和SMTP配置 → Alert-Management
location /api/notification-config/smtp {
    proxy_pass http://alert-management:8082;
}

# 内部只读API → Alert-Management (可选限制内网访问)
location /api/notification-config/customer {
    # 只允许内网访问
    allow 10.0.0.0/8;
    allow 172.16.0.0/12;
    deny all;
    
    # 只允许GET方法
    limit_except GET {
        deny all;
    }
    
    proxy_pass http://alert-management:8082;
}
```

---

## 🎯 迁移计划

### 阶段1: 双写阶段 (过渡期)
**目标**: 平滑迁移，不中断现有功能

1. **保留Alert-Management的写API** (标记为deprecated)
2. **启用Customer-Management的完整API**
3. **新客户使用Customer-Management**
4. **现有客户继续使用Alert-Management**

### 阶段2: 迁移阶段
**目标**: 逐步迁移现有客户

1. **通知现有API用户**
2. **提供迁移指南**
3. **监控API使用情况**
4. **逐步迁移客户到新API**

### 阶段3: 清理阶段
**目标**: 移除Alert-Management的写权限

1. **确认所有客户已迁移**
2. **移除Alert-Management的POST/PUT/DELETE端点**
3. **保留只读GET端点（内部使用）**
4. **更新文档**

---

## 📝 代码实施建议

### Alert-Management Service 修改

#### 1. Repository层限制写操作

```java
@Repository
public interface CustomerNotificationConfigRepository extends JpaRepository<CustomerNotificationConfig, Long> {
    
    // ✅ 保留: 只读查询方法
    Optional<CustomerNotificationConfig> findByCustomerId(String customerId);
    List<CustomerNotificationConfig> findByIsActiveTrue();
    
    // ❌ 移除或标记为@Deprecated: 写操作方法
    // 如果JPA自带的save/delete方法，可以在Service层限制调用
}
```

#### 2. Service层添加只读限制

```java
@Service
@Slf4j
public class CustomerNotificationConfigService {
    
    private final CustomerNotificationConfigRepository repository;
    
    /**
     * ✅ 只读: 获取客户通知配置
     */
    @Transactional(readOnly = true)
    public CustomerNotificationConfig getConfig(String customerId) {
        return repository.findByCustomerId(customerId)
                .orElseThrow(() -> new ConfigNotFoundException(customerId));
    }
    
    /**
     * ❌ 移除: 不再提供创建配置的功能
     * 配置管理应通过Customer-Management服务
     */
    // @Deprecated
    // public CustomerNotificationConfig createConfig(CustomerNotificationConfig config) {
    //     throw new UnsupportedOperationException(
    //         "Config creation should be done via Customer-Management service");
    // }
    
    /**
     * ❌ 移除: 不再提供更新配置的功能
     */
    // @Deprecated
    // public CustomerNotificationConfig updateConfig(String customerId, CustomerNotificationConfig config) {
    //     throw new UnsupportedOperationException(
    //         "Config updates should be done via Customer-Management service");
    // }
}
```

#### 3. Controller层限制HTTP方法

```java
@RestController
@RequestMapping("/api/notification-config/customer")
@Slf4j
public class CustomerNotificationConfigController {
    
    private final CustomerNotificationConfigService service;
    
    /**
     * ✅ 保留: 只读API (内部使用)
     */
    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerNotificationConfig> getConfig(@PathVariable String customerId) {
        log.info("Internal read: Getting notification config for customer: {}", customerId);
        return ResponseEntity.ok(service.getConfig(customerId));
    }
    
    /**
     * ❌ 移除或返回403: POST方法
     */
    // @PostMapping
    // public ResponseEntity<?> createConfig(@RequestBody CustomerNotificationConfig config) {
    //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
    //             .body(Map.of(
    //                 "error", "Forbidden",
    //                 "message", "Config creation should be done via Customer-Management service (port 8084)",
    //                 "endpoint", "POST /api/v1/customers/{id}/notification-config"
    //             ));
    // }
    
    /**
     * ❌ 移除或返回403: PUT方法
     */
    // @PutMapping("/{customerId}")
    // public ResponseEntity<?> updateConfig(...) {
    //     return ResponseEntity.status(HttpStatus.FORBIDDEN)
    //             .body(Map.of(
    //                 "error", "Forbidden",
    //                 "message", "Config updates should be done via Customer-Management service (port 8084)",
    //                 "endpoint", "PUT /api/v1/customers/{id}/notification-config"
    //             ));
    // }
}
```

---

## 📚 文档更新

### Alert-Management文档更新

在 `docs/api/email_notification_configuration.md` 中添加说明：

```markdown
## ⚠️ API变更通知

### 配置管理API迁移

**生效日期**: 2025-10-16

客户通知配置的**创建/更新/删除**功能已迁移至 **Customer-Management Service**。

#### 新API端点 (推荐使用)

**服务地址**: `http://localhost:8084`

```bash
# 获取配置
GET /api/v1/customers/{customerId}/notification-config

# 更新配置
PUT /api/v1/customers/{customerId}/notification-config

# 快速切换
PATCH /api/v1/customers/{customerId}/notification-config/email/toggle
PATCH /api/v1/customers/{customerId}/notification-config/slack/toggle
```

#### 旧API端点 (已废弃)

**服务地址**: `http://localhost:8082`

```bash
❌ POST   /api/notification-config/customer         # 已废弃，请使用Customer-Management
❌ PUT    /api/notification-config/customer/{id}    # 已废弃，请使用Customer-Management
❌ DELETE /api/notification-config/customer/{id}    # 已废弃，请使用Customer-Management

✅ GET    /api/notification-config/customer/{id}    # 保留用于内部查询
```

#### 迁移指南

详见: `docs/migration/alert_to_customer_management.md`
```

---

## ✅ 实施检查清单

### Customer-Management Service
- [x] ✅ 实现完整的NotificationConfig CRUD API
- [x] ✅ 实现快速开关toggle API
- [x] ✅ 实现配置测试API
- [x] ✅ 编写完整的API测试
- [ ] 📝 编写API文档
- [ ] 🔒 添加访问权限控制（可选）

### Alert-Management Service
- [ ] 🔧 移除或限制POST/PUT/DELETE端点
- [ ] 🔧 保留GET端点用于内部查询
- [ ] 🔧 添加403响应并引导用户使用新API
- [ ] 📝 更新文档说明API变更
- [ ] 🧪 确认只读访问不影响通知发送功能

### API Gateway
- [ ] 🌐 配置路由规则
- [ ] 🌐 限制Alert-Management的写操作
- [ ] 🌐 允许内网访问只读端点

### 文档
- [ ] 📝 更新Alert-Management API文档（标记废弃）
- [ ] 📝 创建Customer-Management API文档
- [ ] 📝 编写迁移指南
- [ ] 📝 更新架构图

---

## 🎯 预期效果

### 安全性提升
✅ Alert-Management无法误修改配置  
✅ 配置变更来源清晰可追溯  
✅ 符合最小权限原则  

### 职责清晰
✅ Customer-Management: 配置管理中心  
✅ Alert-Management: 通知执行引擎  
✅ 各司其职，易于维护  

### 可扩展性
✅ 未来可添加配置审计功能  
✅ 未来可添加配置版本控制  
✅ 未来可添加配置审批流程  

---

**文档结束**

*本方案已获批准，建议按照实施检查清单逐步执行*
