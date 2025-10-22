# 代码审查报告 - 命名规范问题

**生成时间**: 2025-10-17  
**审查范围**: Customer-Management服务 & Alert-Management服务  
**审查重点**: JSON序列化命名、数据库映射、API路径命名

---

## 执行摘要

### 🔴 严重问题: 1个
### 🟡 警告: 3个
### ✅ 符合规范: NotificationConfig相关DTO

---

## 1. 严重问题

### 🔴 问题1: Customer相关DTO缺少@JsonProperty注解

**影响**: 高  
**优先级**: P0 - 必须立即修复

**问题描述**:
Customer相关的DTO类没有使用`@JsonProperty`注解，导致JSON字段名使用默认的camelCase。这与NotificationConfig API使用snake_case的规范不一致。

**受影响文件**:
- `CustomerResponse.java` - 响应DTO
- `CreateCustomerRequest.java` - 创建请求DTO  
- `UpdateCustomerRequest.java` - 更新请求DTO

**当前状态**:
```java
// CustomerResponse.java - 当前实现
@Data
public class CustomerResponse {
    private String customerId;  // ❌ JSON输出: "customerId" (camelCase)
    private String createdAt;   // ❌ JSON输出: "createdAt"
}
```

**期望状态**:
```java
// CustomerResponse.java - 应该的实现
@Data
public class CustomerResponse {
    @JsonProperty("customer_id")  // ✅ JSON输出: "customer_id" (snake_case)
    private String customerId;
    
    @JsonProperty("created_at")   // ✅ JSON输出: "created_at"
    private Instant createdAt;
}
```

**修复建议**:
1. 为所有Customer相关DTO添加`@JsonProperty`注解
2. 遵循snake_case命名规范
3. 更新集成测试脚本使用snake_case字段
4. 更新API文档

**影响范围**:
- [ ] `/api/v1/customers` POST请求
- [ ] `/api/v1/customers` GET响应
- [ ] `/api/v1/customers/{id}` PUT/PATCH请求
- [ ] 所有返回Customer对象的API

---

## 2. 警告问题

### 🟡 警告1: Device相关DTO命名不一致

**问题描述**:
设备相关的DTO使用了混合命名风格，部分使用camelCase，部分使用snake_case。

**受影响文件**:
- `DeviceMappingRequest.java`
- `DeviceMappingResponse.java`

**检查结果**:
```bash
# 需要审查
services/customer-management/src/main/java/com/threatdetection/customer/device/dto/DeviceMappingRequest.java
services/customer-management/src/main/java/com/threatdetection/customer/device/dto/DeviceMappingResponse.java
```

**修复建议**:
1. 统一使用snake_case + @JsonProperty
2. 特别注意`dev_serial`字段（数据库列名）

### 🟡 警告2: 数据库表名与列名大小写不一致

**问题描述**:
部分数据库操作使用了不一致的表名/列名大小写。

**示例**:
```sql
-- ✅ 正确: 全小写 + snake_case
SELECT customer_id, created_at FROM customers;

-- ❌ 错误: 可能存在混合大小写
SELECT CustomerId FROM Customers;  -- 如果存在
```

**修复建议**:
1. 统一使用小写 + snake_case
2. 审查所有SQL查询
3. 使用@Column注解明确指定列名

### 🟡 警告3: 测试脚本中的字段名不一致

**问题描述**:
在今天的调试中发现，集成测试脚本之前使用了camelCase，导致PATCH请求失败。虽然已修复，但需要审查所有测试脚本。

**已修复**:
- ✅ `integration_test_responsibility_separation.sh` - 已修改为snake_case

**需要审查**:
- `integration_test_notifications.py` - Python测试脚本
- 其他测试脚本（如果有）

---

## 3. 符合规范的代码 ✅

### NotificationConfig相关DTO

**文件**:
- `NotificationConfigRequest.java` ✅
- `NotificationConfigResponse.java` ✅

**示例** (符合规范):
```java
@Data
public class NotificationConfigRequest {
    @JsonProperty("email_enabled")
    private Boolean emailEnabled;
    
    @JsonProperty("min_severity_level")
    private String minSeverityLevel;
    
    @JsonProperty("created_at")
    private Instant createdAt;
}
```

**优点**:
- ✅ 使用`@JsonProperty`明确指定JSON字段名
- ✅ JSON使用snake_case
- ✅ Java字段使用camelCase
- ✅ 与数据库列名一致

---

## 4. 修复计划

### 阶段1: 立即修复（P0）

**任务**: 修复Customer相关DTO的命名问题

1. **CustomerResponse.java**
   ```java
   // 添加@JsonProperty注解到所有字段
   @JsonProperty("customer_id")
   private String customerId;
   
   @JsonProperty("created_at")
   private Instant createdAt;
   
   @JsonProperty("updated_at")
   private Instant updatedAt;
   
   @JsonProperty("subscription_tier")
   private String subscriptionTier;
   
   @JsonProperty("max_devices")
   private Integer maxDevices;
   
   @JsonProperty("current_devices")
   private Integer currentDevices;
   
   @JsonProperty("subscription_start_date")
   private Instant subscriptionStartDate;
   
   @JsonProperty("subscription_end_date")
   private Instant subscriptionEndDate;
   
   @JsonProperty("alert_enabled")
   private Boolean alertEnabled;
   
   @JsonProperty("created_by")
   private String createdBy;
   
   @JsonProperty("updated_by")
   private String updatedBy;
   ```

2. **CreateCustomerRequest.java**
   ```java
   @JsonProperty("customer_id")
   private String customerId;
   
   @JsonProperty("subscription_tier")
   private String subscriptionTier;
   
   @JsonProperty("max_devices")
   private Integer maxDevices;
   
   @JsonProperty("company_size")
   private String companySize;
   ```

3. **UpdateCustomerRequest.java**
   ```java
   // 同上，为所有字段添加@JsonProperty
   ```

### 阶段2: 设备管理DTO修复

**任务**: 审查并修复Device相关DTO

文件清单:
- [ ] `DeviceMappingRequest.java`
- [ ] `DeviceMappingResponse.java`
- [ ] `DeviceQuotaResponse.java`
- [ ] `BatchDeviceMappingRequest.java`
- [ ] `BatchOperationResponse.java`

### 阶段3: 测试脚本更新

**任务**: 更新所有测试脚本使用snake_case

文件清单:
- [x] `integration_test_responsibility_separation.sh` - 已完成
- [ ] 其他bash测试脚本
- [ ] Python测试脚本

### 阶段4: 文档更新

**任务**: 更新API文档

文件清单:
- [x] `DEVELOPMENT_CHEATSHEET.md` - 已创建
- [ ] 更新Swagger/OpenAPI文档（如果有）
- [ ] 更新README.md中的API示例

---

## 5. 验证清单

修复完成后需要验证:

### 编译验证
```bash
cd services/customer-management
mvn clean compile
```

### 单元测试
```bash
mvn test
```

### 集成测试
```bash
# 1. 重新构建容器
cd /home/kylecui/threat-detection-system/docker
docker-compose up -d --build customer-management

# 2. 运行集成测试
cd /home/kylecui/threat-detection-system/scripts
bash integration_test_responsibility_separation.sh
```

### API响应验证
```bash
# 测试Customer API返回snake_case字段
curl -X POST "http://localhost:8084/api/v1/customers" \
  -H "Content-Type: application/json" \
  -d '{
    "customer_id": "verify-test-001",
    "name": "验证测试",
    "email": "verify@test.com"
  }' | jq '.'

# 期望输出应包含:
# {
#   "customer_id": "verify-test-001",  ✅ snake_case
#   "created_at": "2025-10-17T...",    ✅ snake_case
#   "max_devices": 10,                 ✅ snake_case
#   ...
# }
```

---

## 6. 预防措施

为了避免将来再次出现命名不一致问题:

### 6.1 代码Review清单

每次PR必须检查:
- [ ] 所有DTO字段都有`@JsonProperty`注解
- [ ] JSON字段名使用snake_case
- [ ] Java字段名使用camelCase
- [ ] 数据库列名使用snake_case
- [ ] @Column注解明确指定列名

### 6.2 自动化检查

建议添加:
1. **Maven插件**: 检查@JsonProperty注解的存在
2. **Checkstyle规则**: 强制DTO类使用@JsonProperty
3. **集成测试**: 验证JSON响应字段名格式

### 6.3 开发规范

**强制要求**:
- 每次开发前查阅`DEVELOPMENT_CHEATSHEET.md`
- 新增DTO必须参考`NotificationConfigRequest.java`示例
- 提交前运行完整的集成测试

---

## 7. 总结

### 当前状态
- ✅ NotificationConfig API: 完全符合规范
- 🔴 Customer API: 需要立即修复
- 🟡 Device API: 需要审查
- 🟡 测试脚本: 部分已修复，需要全面审查

### 优先级
1. **P0 (立即)**: 修复Customer DTO的@JsonProperty问题
2. **P1 (本周)**: 审查并修复Device DTO
3. **P2 (下周)**: 完善测试脚本和文档

### 预估工作量
- Customer DTO修复: 2小时
- Device DTO审查修复: 3小时
- 测试脚本更新: 2小时
- 文档更新: 1小时
- **总计**: 约8小时

---

**报告生成者**: AI Assistant  
**审查范围**: Customer-Management & Alert-Management  
**下次审查**: 修复完成后
