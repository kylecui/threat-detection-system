# Database Initialization Scripts - Production Safety Guide

## 🚨 重要安全更新

### 问题识别
1. **数据丢失风险**: 原 `01-init-db.sql` 会删除 `device_customer_mapping` 表，导致生产环境数据丢失
2. **初始化不一致**: `customers` 表初始化分散在不同位置
3. **状态管理错误**: 设备解绑时 `is_active` 字段未正确设置为 `false`

### 解决方案

#### 1. 生产安全初始化脚本
- **新文件**: `01-init-db-production-safe.sql`
- **特性**:
  - 使用 `CREATE TABLE IF NOT EXISTS` 保护现有表结构
  - 使用 `ON CONFLICT DO NOTHING` 避免覆盖现有数据
  - 添加时效性字段 (`bind_time`, `unbind_time`, `bind_reason`)
  - 保留所有历史映射关系

#### 2. 统一客户表初始化
- **新文件**: `17-customers-init.sql`
- **特性**:
  - 将 `customers` 表初始化从服务层移至 Docker 层
  - 确保所有部署环境的一致性
  - 使用 `ON CONFLICT DO NOTHING` 保护现有客户数据

#### 3. 修复设备解绑逻辑
- **修复文件**: `DeviceSerialToCustomerMappingService.java`
- **修复内容**: `unbindDevice()` 方法现在正确设置 `is_active = false`

## 📋 部署指南

### 生产环境部署
```bash
# 使用生产安全脚本
cp docker/01-init-db-production-safe.sql docker/01-init-db.sql
```

### 开发/测试环境部署
```bash
# 使用完整重建脚本（会删除所有数据）
cp docker/01-init-db.sql docker/01-init-db.sql.backup
# 然后正常部署
```

### Docker Compose 配置
已更新 `docker-compose.yml` 使用新的初始化脚本顺序：
1. `01-init-db-production-safe.sql` - 设备映射表（安全）
2. `17-customers-init.sql` - 客户表
3. `02-attack-events-storage.sql` - 攻击事件存储

## 🔍 验证检查

### 检查设备映射状态
```sql
SELECT dev_serial, customer_id, is_active, bind_time, unbind_time
FROM device_customer_mapping
ORDER BY dev_serial, bind_time DESC;
```

### 检查客户数据
```sql
SELECT customer_id, name, status, subscription_tier
FROM customers
ORDER BY customer_id;
```

### 验证解绑功能
```sql
-- 解绑设备后检查状态
SELECT * FROM device_customer_mapping
WHERE dev_serial = 'TEST_DEVICE'
ORDER BY bind_time DESC;
```

## ⚠️ 迁移注意事项

### 从旧版本升级
1. **备份数据**: 在部署前备份 `device_customer_mapping` 表
2. **逐步迁移**: 先部署新脚本验证，再切换到生产模式
3. **数据验证**: 确认所有设备映射关系正确保留

### 向后兼容性
- 新脚本完全兼容现有数据结构
- 旧的 `01-init-db.sql` 保留作为破坏性重建选项
- 所有新字段都有默认值，确保兼容性

## 📊 数据持久性保证

| 表名 | 部署策略 | 数据保护 |
|------|----------|----------|
| `device_customer_mapping` | `CREATE IF NOT EXISTS` + `ON CONFLICT` | ✅ 完全保护 |
| `customers` | `CREATE IF NOT EXISTS` + `ON CONFLICT` | ✅ 完全保护 |
| `threat_assessments` | `CREATE IF NOT EXISTS` | ✅ 完全保护 |
| `device_status_history` | `CREATE IF NOT EXISTS` | ✅ 完全保护 |

---

**最后更新**: 2025-10-31
**状态**: ✅ 生产就绪