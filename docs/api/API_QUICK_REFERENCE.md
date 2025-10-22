````markdown
# 📌 后端API快速参考卡片

**更新**: 2025-10-21  
**用途**: Day 2-5 快速查询

---

## 🎯 5个关键API组 (快速导航)

### Group 1: 客户管理 (8084)
```
POST   /api/v1/customers                          创建客户
GET    /api/v1/customers/{customerId}              获取客户
GET    /api/v1/customers                           列表
PUT    /api/v1/customers/{customerId}              更新
DELETE /api/v1/customers/{customerId}              删除
```

### Group 2: 设备绑定 (8084)
```
POST   /api/v1/customers/{customerId}/devices     绑定设备
GET    /api/v1/customers/{customerId}/devices     列表设备
PUT    /api/v1/customers/{customerId}/devices/{devSerial}  更新
DELETE /api/v1/customers/{customerId}/devices/{devSerial}  解绑
```

### Group 3: 通知配置 (8084)
```
GET    /api/v1/customers/{customerId}/notification-config         查询
PUT    /api/v1/customers/{customerId}/notification-config         设置
PATCH  /api/v1/customers/{customerId}/notification-config         部分更新
POST   /api/v1/customers/{customerId}/notification-config/test    测试
```

### Group 4: 告警管理 (8082)
```
POST   /api/v1/alerts                             创建
GET    /api/v1/alerts                             列表
PUT    /api/v1/alerts/{id}/status                 更新状态
POST   /api/v1/alerts/{id}/resolve                解决
POST   /api/v1/alerts/{id}/escalate               升级
```

### Group 5: 威胁评估 (8083)
```
GET    /api/v1/assessment/assessments?customer_id=xxx   列表
GET    /api/v1/assessment/statistics?customer_id=xxx    统计
GET    /api/v1/assessment/trend?customer_id=xxx         趋势
GET    /api/v1/assessment/port-distribution?customer_id=xxx  端口分布
```

---

## 🔄 3个关键业务流程 (速查表)

### 流程1: 初始化 (5分钟)
```
1️⃣  POST /api/v1/customers
    Response: {customerId: "cust-001"}

2️⃣  POST /api/v1/customers/cust-001/devices
    Body: {devSerial: "DEV-001"}
    Response: {bound: true}

3️⃣  PUT /api/v1/customers/cust-001/notification-config
    Body: {emailEnabled: true, emailList: ["admin@test.com"]}
    Response: {configured: true}
```

### 流程2: 日志处理 (实时)
```
1️⃣  日志从设备→syslog→Data-Ingestion (8080)

2️⃣  POST /api/v1/logs/ingest
    Body: {raw syslog message}
    Response: {processed: true}

3️⃣  [自动] Flink处理 + 威胁评分

4️⃣  GET /api/v1/assessment/assessments?customer_id=cust-001
    Response: [{threatScore: 125.5, level: "HIGH"}]

5️⃣  [自动] 告警创建 + 邮件发送
```

### 流程3: 告警管理
```
状态转换: OPEN → ACKNOWLEDGED → RESOLVED → ARCHIVED

POST /api/v1/alerts/{id}/acknowledge        确认
POST /api/v1/alerts/{id}/resolve            解决
GET  /api/v1/alerts/analytics               统计
```

---

## 🔑 关键DTO字段 (JSON格式)

### 请求体: snake_case

```json
{
  "custom_id": "customer-001",
  "dev_serial": "DEV-001",
  "email_enabled": true,
  "min_severity_level": "HIGH",
  "email_list": ["admin@test.com"]
}
```

### 响应体: snake_case

```json
{
  "custom_id": "customer-001",
  "dev_serial": "DEV-001",
  "email_enabled": true,
  "created_at": "2025-10-21T10:00:00Z"
}
```

---

## ⚠️ 常见问题排查

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 绑定设备失败 | 配额满 | 检查设备数 ≤ 100 |
| 没收到邮件 | 配置错 | 检查emailEnabled=true |
| 日志无customerId | devSerial无映射 | 先POST设备再发日志 |
| 告警不创建 | threatScore太低 | 增加日志或检查权重 |
| API返回403 | 多租户隔离 | 确保customerId一致 |

---

## 📊 测试用例模板

### Happy Path (正常流程)

```bash
# 1. 创建客户
curl -X POST http://localhost:8888/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "custom_id": "test-customer",
    "company_name": "Test Corp",
    "contact_email": "admin@test.com"
  }'

# 2. 绑定设备
curl -X POST http://localhost:8888/api/v1/customers/test-customer/devices \
  -H "Content-Type: application/json" \
  -d '{
    "dev_serial": "DEV-001",
    "device_type": "honeypot",
    "location": "lab"
  }'

# 3. 配置通知
curl -X PUT http://localhost:8888/api/v1/customers/test-customer/notification-config \
  -H "Content-Type: application/json" \
  -d '{
    "email_enabled": true,
    "email_list": ["admin@test.com"],
    "min_severity_level": "MEDIUM"
  }'

# 4. 查询设备
curl http://localhost:8888/api/v1/customers/test-customer/devices

# 5. 发送测试日志
curl -X POST http://localhost:8888/api/v1/logs/ingest \
  -H "Content-Type: application/json" \
  -d '{"dev_serial": "DEV-001", "attack_mac": "00:11:22:33:44:55", ...}'
```

### 检查点验证

```bash
# 检查数据库一致性
docker exec threat-db psql -U postgres -d threat_detection -c \
  "SELECT * FROM device_customer_mapping WHERE customer_id = 'test-customer';"

# 检查Kafka消息
docker exec kafka kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic attack-events --max-messages 5

# 检查告警创建
curl http://localhost:8888/api/v1/alerts?page=0&size=10
```

---

## 🛠️ 快速命令

```bash
# 查看所有服务状态
docker-compose ps

# 查看某服务日志
docker-compose logs -f threat-assessment

# 重启某服务
docker-compose restart customer-management

# 进入数据库
docker exec -it threat-db psql -U postgres threat_detection

# 查看表结构
docker exec -it threat-db psql -U postgres threat_detection \
  -c "\d+ customer_notification_configs"

# 运行所有测试
bash scripts/test_backend_api_happy_path.sh

# 检查API健康
for port in 8080 8082 8083 8084 8888; do
  echo "Port $port:"
  curl -s http://localhost:$port/health | head -20
done
```

---

## 📍 文件导航

| 需要什么 | 查看哪个文件 |
|---------|------------|
| 快速参考 | **本文件** (API_QUICK_REFERENCE.md) |
| 完整API列表 | BACKEND_API_INVENTORY.md |
| 系统流程 | API_DEPENDENCY_GRAPH.txt |
| 验证清单 | API_COMPLETE_VERIFICATION_CHECKLIST.md |
| 测试脚本 | scripts/test_backend_api_happy_path.sh |
| 执行计划 | BACKEND_TEST_ACTION_PLAN_2025-10-21.md |
| 开发手册 | DEVELOPMENT_CHEATSHEET.md |

---

## ✅ 每日检查清单

### 早晨 (启动前)
- [ ] Docker服务全部启动
- [ ] 所有5个服务健康检查 PASS
- [ ] Kafka Topics存在
- [ ] 数据库连接正常

### 中午 (测试中)
- [ ] 运行test_backend_api_happy_path.sh
- [ ] 检查数据库一致性
- [ ] 查看Kafka消息流
- [ ] 验证告警创建

### 下午 (汇总)
- [ ] 记录所有失败API
- [ ] 分类问题 (P0/P1/P2)
- [ ] 编写修复计划
- [ ] 更新进度表

---

**Last Updated**: 2025-10-21  
**Next Update**: 测试完成后 (2025-10-23)

````
