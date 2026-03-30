# 领域规则参考文档 (Domain Rules Reference)

## 概述 (Overview)
This document serves as the authoritative reference for the 6 core business domain rules governing V1 log processing, device management, and tenant hierarchy within the Threat Detection System.

## 规则摘要 (Rules Summary)

| Rule ID | Description | Status |
|---------|-------------|--------|
| Rule 1  | Device Serial (`dev_serial`) belongs to at least one end user | ✅ Implemented |
| Rule 2  | Each end user may have one or more `dev_serial` | ✅ Implemented |
| Rule 3  | `log_type=1` = attack_log, `log_type=2` = heartbeat | ✅ Code Complete |
| Rule 4  | Device transfer between end users | ✅ Implemented |
| Rule 5  | TIRE and LLM configuration must follow TIRE project design | ✅ Implemented |
| Rule 6  | Tenant Admin as Distributor Model | ✅ Implemented |

---

## 规则 1: 设备序列号归属 (Device Serial Ownership)
**Rule: Device Serial (`dev_serial`) belongs to at least one end user**

- A `dev_serial` identifies one site of monitor stations (honeypot devices).
- Each device must belong to at least one customer (end user).
- **Implementation**: `device_customer_mapping` table with `dev_serial` → `customer_id` mapping.
- **Service**: `DevSerialToCustomerMappingService.resolveCustomerId()` resolves device to customer.
- **Fallback**: Unknown devices get `customer_id = "unknown"`.
- **Status**: ✅ Implemented

### 数据库结构 (Database Schema)
```sql
CREATE TABLE device_customer_mapping (
    id SERIAL PRIMARY KEY,
    dev_serial VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    bind_time TIMESTAMP NOT NULL,
    unbind_time TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE
);
```

---

## 规则 2: 用户多设备支持 (Multi-Device Support)
**Rule: Each end user may have one or more `dev_serial`**

- Customers can have multiple devices.
- If no devices, customer was just created or near end of lifecycle.
- **Implementation**: `device_customer_mapping` table, one-to-many relationship.
- **API**: `GET /api/v1/customers/{id}/devices` lists all devices for a customer.
- **Controller**: `DeviceManagementController` manages device CRUD.
- **Status**: ✅ Implemented

---

## 规则 3: 日志类型定义 (Log Type Definitions)
**Rule: `log_type=1` = attack_log, `log_type=2` = heartbeat**

- **log_type=1**: Attack events with `attack_mac`, `attack_ip`, `response_ip`, `response_port`.
- **log_type=2**: Heartbeat/status with `sentry_count` (virtual guard count), `real_host_count` (online device count), `dev_start_time`, `dev_end_time`.
- **Implementation**:
  - `LogParserService` handles both types.
  - Attack logs → `attack-events` Kafka topic → Flink → threat scoring.
  - Status logs → `status-events` Kafka topic → `HeartbeatPersistenceService` → `device_inventory` + `topology_snapshots` + `discovered_hosts` tables.
  - V2 MQTT heartbeat also supported via `V2EventParserService`.
- **Note**: Historical V1 bulk import only imported log_type=1 (attack logs). Heartbeat pipeline is code-complete but historical heartbeat data not imported (source files on Windows mount, not accessible from server).
- **Status**: ✅ Code complete (heartbeat data import pending data source availability)

---

## 规则 4: 设备转移 (Device Transfer)
**Rule: Device transfer between end users**

- A device can be transferred between customers.
- Device activated on ONE customer only at a time.
- `dev_end_time > now` or `dev_end_time == -1` means currently active.
- **Implementation**:
  - `device_customer_mapping` table has `bind_time`, `unbind_time`, `is_active` columns.
  - `unbindTime IS NULL` ≡ `dev_end_time == -1` (permanently active).
  - `isActiveAt(timestamp)`: checks `bindTime <= timestamp AND (unbindTime IS NULL OR unbindTime > timestamp)`.
  - `findCustomerIdByDevSerial()`: only returns devices where `unbindTime IS NULL`.
  - Unbinding sets `is_active = false` — device can then be re-bound to new customer.
  - Full history preserved in `findByDevSerialOrderByBindTimeDesc()`.
- **Status**: ✅ Implemented

---

## 规则 5: TIRE 与 LLM 配置 (TIRE and LLM Configuration)
**Rule: TIRE and LLM configuration must follow TIRE project design**

- **11 Threat Intelligence Plugins**: abuseipdb, virustotal, otx, greynoise, shodan, rdap, reverse_dns, honeynet, internal_flow, threatbook, tianjiyoumeng.
- **Plugin Settings**: Each plugin has: `enabled` flag, `priority` (1-100), `timeout` (seconds).
- **LLM Integration**: API key, base URL, model selection.
- **LLM Validation**: `POST /api/v1/system-config/llm/validate` calls provider's `/models` endpoint.
- **Implementation**:
  - `system_config` table with category `tire_plugins` (33 rows for 11 plugins × 3 settings).
  - Additional `llm_api_key` and `llm_base_url` in category `llm`.
  - `SystemConfigController` with full CRUD + LLM validate endpoint.
  - Frontend Settings page with "插件管理" tab (grid layout) + enhanced "LLM配置" tab with "测试连接" button.
- **Status**: ✅ Implemented and deployed

### 数据库结构 (Database Schema)
```sql
CREATE TABLE system_config (
    id SERIAL PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT,
    description TEXT
);
```

---

## 规则 6: 租户管理员分销模式 (Tenant Admin Distributor Model)
**Rule: Tenant Admin as Distributor Model**

- **TenantAdmin (租户管理员)** acts as a distributor/reseller.
- Can manage all their customers and see aggregated data.
- **Hierarchy**: SuperAdmin → TenantAdmin (distributor) → CustomerUser.
- **Implementation**:
  - `tenants` table with `id`, `name`, `description`.
  - `customers` table has `tenant_id` foreign key.
  - `auth_users` table has `role` (SUPER_ADMIN / TENANT_ADMIN / CUSTOMER_USER).
  - `RbacAuthorizationFilter`: TENANT_ADMIN sees all customers under their tenant, CUSTOMER_USER sees only their own.
  - Frontend Dashboard/Analytics: TENANT_ADMIN gets multi-customer aggregation view.
  - `TenantController`: CRUD for tenant management.
  - `CustomerController.getCustomersByTenantId()`: scoped customer listing.
- **Status**: ✅ Implemented and deployed

### 数据库结构 (Database Schema)
```sql
CREATE TABLE tenants (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT
);

CREATE TABLE customers (
    id SERIAL PRIMARY KEY,
    tenant_id INTEGER REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL
);
```

---

## 关键 API 端点 (Key API Endpoints)

| Endpoint | Description | Rule |
|----------|-------------|------|
| `GET /api/v1/customers/{id}/devices` | List devices for a customer | Rule 2 |
| `POST /api/v1/system-config/llm/validate` | Validate LLM connection | Rule 5 |
| `GET /api/v1/tenants/{id}/customers` | List customers for a tenant | Rule 6 |

## 代码交叉引用 (Code Cross-References)

- **Log Parsing**: `LogParserService.java`, `V2EventParserService.java`
- **Device Mapping**: `DevSerialToCustomerMappingService.java`, `DeviceManagementController.java`
- **System Config**: `SystemConfigController.java`, `HeartbeatPersistenceService.java`
- **Auth/RBAC**: `RbacAuthorizationFilter.java`, `TenantController.java`
