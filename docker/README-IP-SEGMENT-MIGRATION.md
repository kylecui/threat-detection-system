# IP网段权重表迁移指南

## 📋 概述

本文档说明如何安全地从旧版网段权重表（`06-ip-segment-weights.sql`）迁移到新版方向性权重表（`10-ip-segment-weights-v2.sql`）。

---

## ⚠️ 重要：数据持久化保护

### 原则
- ✅ **客户自定义配置必须持久化**
- ✅ 容器重启不会删除客户数据
- ✅ 默认配置仅在初始化时插入一次

### 脚本安全机制

#### 1. 表创建保护
```sql
CREATE TABLE IF NOT EXISTS ip_segment_weight_config (...)
```
- 如果表已存在，跳过创建
- 不会删除已有表和数据

#### 2. 数据插入保护
```sql
DO $$ 
BEGIN
    IF (SELECT COUNT(*) FROM ip_segment_weight_config) = 0 THEN
        -- 仅在表为空时插入默认配置
        INSERT INTO ...
    ELSE
        RAISE NOTICE '⚠️ Table already contains data, skipping insertion';
    END IF;
END $$;
```
- 检查表是否为空
- 已有数据则跳过插入
- 客户配置得到保护

---

## 🚀 部署场景

### 场景1: 全新部署（首次安装）

**操作步骤**:
```bash
# 1. 启动数据库容器
docker-compose up -d postgres

# 2. 初始化脚本会自动执行
# docker/01-init-db.sql (基础表结构)
# docker/10-ip-segment-weights-v2.sql (网段权重配置)

# 3. 验证表创建
docker exec -it <postgres-container> psql -U threat_user -d threat_detection \
  -c "SELECT COUNT(*) FROM ip_segment_weight_config;"

# 预期结果: 14条记录 (12个default + 2个customer-A示例)
```

**预期输出**:
```
NOTICE:  ✅ Creating new table: ip_segment_weight_config
NOTICE:  ✅ Inserted default IP segment weight configurations
```

---

### 场景2: 升级现有系统（已有旧表）

**前提**: 系统中已存在旧版 `ip_segment_weight_config` 表（19条记录）

#### 选项A: 保留旧表，手动迁移（推荐）

**步骤1**: 备份现有配置
```bash
# 导出客户自定义配置
docker exec -it <postgres-container> psql -U threat_user -d threat_detection \
  -c "COPY (SELECT * FROM ip_segment_weight_config WHERE segment_name NOT LIKE '%AWS%' AND segment_name NOT LIKE '%Cloud%') TO STDOUT WITH CSV HEADER;" > ip_segments_backup.csv
```

**步骤2**: 创建新表（使用不同名称）
```sql
-- 修改脚本，使用新表名
CREATE TABLE ip_segment_weight_config_v2 (...);
```

**步骤3**: 数据转换和迁移
```sql
-- 转换旧数据到新格式
INSERT INTO ip_segment_weight_config_v2 (
    customer_id, segment_name, ip_range_start, ip_range_end,
    zone_type, zone_level,
    weight_as_source, weight_as_target,
    weight_lateral_same_zone, weight_lateral_cross_zone,
    weight_escalation, weight_exfiltration,
    description, priority
)
SELECT 
    'default' AS customer_id,
    segment_name,
    ip_range_start,
    ip_range_end,
    
    -- 根据category映射zone_type
    CASE 
        WHEN category = 'PRIVATE' THEN 'OFFICE'
        WHEN category LIKE 'CLOUD%' THEN 'SERVER'
        WHEN category = 'HIGH_RISK_REGION' THEN 'SERVER'
        WHEN category = 'MALICIOUS' THEN 'SERVER'
        ELSE 'OFFICE'
    END AS zone_type,
    
    -- 根据weight映射zone_level
    CASE 
        WHEN weight < 1.0 THEN 'LOW'
        WHEN weight < 1.5 THEN 'MEDIUM'
        WHEN weight < 1.8 THEN 'HIGH'
        ELSE 'CRITICAL'
    END AS zone_level,
    
    -- 方向性权重（基于旧的单一权重估算）
    weight AS weight_as_source,
    weight AS weight_as_target,
    1.00 AS weight_lateral_same_zone,
    1.50 AS weight_lateral_cross_zone,
    2.00 AS weight_escalation,
    1.80 AS weight_exfiltration,
    
    description,
    priority
FROM ip_segment_weight_config;
```

**步骤4**: 切换表名
```sql
-- 重命名旧表（保留备份）
ALTER TABLE ip_segment_weight_config RENAME TO ip_segment_weight_config_old;

-- 新表使用正式名称
ALTER TABLE ip_segment_weight_config_v2 RENAME TO ip_segment_weight_config;
```

#### 选项B: 就地升级（风险较高）

⚠️ **警告**: 此方法会修改表结构，建议先在测试环境验证

**步骤1**: 备份数据库
```bash
docker exec -it <postgres-container> pg_dump -U threat_user threat_detection > backup_$(date +%Y%m%d_%H%M%S).sql
```

**步骤2**: 添加新字段
```sql
-- 添加多租户字段
ALTER TABLE ip_segment_weight_config 
ADD COLUMN IF NOT EXISTS customer_id VARCHAR(50) DEFAULT 'default' NOT NULL;

-- 添加网络分区字段
ALTER TABLE ip_segment_weight_config 
ADD COLUMN IF NOT EXISTS zone_type VARCHAR(50) DEFAULT 'OFFICE' NOT NULL,
ADD COLUMN IF NOT EXISTS zone_level VARCHAR(20) DEFAULT 'MEDIUM' NOT NULL;

-- 添加方向性权重字段
ALTER TABLE ip_segment_weight_config 
ADD COLUMN IF NOT EXISTS weight_as_source DECIMAL(3,2) DEFAULT 1.00,
ADD COLUMN IF NOT EXISTS weight_as_target DECIMAL(3,2) DEFAULT 1.00,
ADD COLUMN IF NOT EXISTS weight_lateral_same_zone DECIMAL(3,2) DEFAULT 1.00,
ADD COLUMN IF NOT EXISTS weight_lateral_cross_zone DECIMAL(3,2) DEFAULT 1.50,
ADD COLUMN IF NOT EXISTS weight_escalation DECIMAL(3,2) DEFAULT 2.00,
ADD COLUMN IF NOT EXISTS weight_exfiltration DECIMAL(3,2) DEFAULT 1.80,
ADD COLUMN IF NOT EXISTS is_honeypot BOOLEAN DEFAULT FALSE;

-- 更新现有数据
UPDATE ip_segment_weight_config 
SET weight_as_source = weight,
    weight_as_target = weight
WHERE weight_as_source IS NULL;
```

**步骤3**: 删除旧约束，添加新约束
```sql
-- 删除旧的唯一约束
ALTER TABLE ip_segment_weight_config DROP CONSTRAINT IF EXISTS ip_segment_weight_config_segment_name_key;

-- 添加新的多租户唯一约束
ALTER TABLE ip_segment_weight_config 
ADD CONSTRAINT uk_customer_segment UNIQUE (customer_id, segment_name);
```

---

### 场景3: 容器重启（生产环境）

**操作**: 直接重启容器
```bash
docker-compose restart postgres
docker-compose restart threat-assessment
```

**预期行为**:
```
NOTICE:  ⚠️ Table ip_segment_weight_config already exists, skipping creation
NOTICE:  ⚠️ Table already contains data, skipping insertion
NOTICE:  💡 Customer configurations are preserved
```

**结果**: ✅ 客户自定义配置完全保留

---

## 🔍 验证和测试

### 1. 检查表结构
```sql
-- 查看表结构
\d+ ip_segment_weight_config

-- 预期字段:
-- customer_id, zone_type, zone_level,
-- weight_as_source, weight_as_target,
-- weight_lateral_same_zone, weight_lateral_cross_zone,
-- weight_escalation, weight_exfiltration,
-- is_honeypot
```

### 2. 检查数据完整性
```sql
-- 统计记录数
SELECT 
    customer_id,
    COUNT(*) as segment_count,
    COUNT(DISTINCT zone_type) as zone_types
FROM ip_segment_weight_config
GROUP BY customer_id;

-- 预期结果:
-- default    | 12 | 7  (OFFICE, SERVER, DMZ, MANAGEMENT, PRODUCTION, IOT, GUEST)
-- customer-A | 2  | 2  (OFFICE, SERVER)
```

### 3. 测试方向性权重函数
```sql
-- 测试场景1: 办公区横向到数据库
SELECT calculate_directional_weight('default', '192.168.10.50', '10.0.2.100');
-- 预期: 2.50 (权限提升)

-- 测试场景2: 同办公区横向
SELECT calculate_directional_weight('default', '192.168.10.50', '192.168.10.100');
-- 预期: 0.90 (同区域)

-- 测试场景3: 访问蜜罐
SELECT calculate_directional_weight('default', '192.168.10.50', '10.0.99.50');
-- 预期: 3.00 (蜜罐警报)
```

---

## 📝 客户自定义配置示例

### 添加客户专属网段
```sql
-- 客户B的配置
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    zone_type, zone_level,
    weight_as_source, weight_as_target,
    weight_lateral_same_zone, weight_lateral_cross_zone,
    weight_escalation, weight_exfiltration,
    description, priority
) VALUES
    ('customer-B', 'Office-Shanghai', '172.20.0.0', '172.20.0.255',
     'OFFICE', 'LOW',
     0.80, 1.00, 0.90, 1.30, 1.50, 1.20,
     '上海办公室', 60),
    
    ('customer-B', 'Server-Database', '10.200.1.0', '10.200.1.255',
     'SERVER', 'CRITICAL',
     2.00, 2.00, 1.80, 2.50, 3.00, 2.80,
     '核心数据库', 100),
    
    ('customer-B', 'Honeypot-Fake-HR', '10.200.99.0', '10.200.99.255',
     'SERVER', 'CRITICAL',
     0.00, 3.00, 0.00, 3.00, 3.00, 3.00,
     '蜜罐-虚假人事系统', TRUE, 100);
```

### 批量导入（CSV）
```bash
# 1. 准备CSV文件 (customer_segments.csv)
# customer_id,segment_name,ip_range_start,ip_range_end,zone_type,zone_level,...

# 2. 导入数据库
docker exec -i <postgres-container> psql -U threat_user -d threat_detection \
  -c "\COPY ip_segment_weight_config FROM STDIN WITH CSV HEADER" < customer_segments.csv
```

---

## 🛠️ 故障排查

### 问题1: 容器重启后配置丢失

**症状**: 客户自定义的网段配置在重启后消失

**原因**: 
- 数据库卷未正确挂载
- 使用了 `DROP TABLE` 的旧脚本

**解决方案**:
```yaml
# docker-compose.yml 确保卷挂载
services:
  postgres:
    volumes:
      - postgres-data:/var/lib/postgresql/data  # 持久化数据
      - ./docker:/docker-entrypoint-initdb.d:ro # 初始化脚本（只读）

volumes:
  postgres-data:  # 命名卷
```

### 问题2: 默认配置重复插入

**症状**: 每次重启后 `default` 配置数量增加

**原因**: 脚本缺少 `IF NOT EXISTS` 检查

**解决方案**: 
- 使用 `10-ip-segment-weights-v2.sql`（已包含检查）
- 或添加 `ON CONFLICT DO NOTHING`

### 问题3: 迁移后权重计算错误

**症状**: 新系统的威胁评分与旧系统差异很大

**原因**: 
- 方向性权重默认值不合理
- 网段分类映射错误

**解决方案**:
```sql
-- 调整特定客户的权重
UPDATE ip_segment_weight_config
SET weight_escalation = 2.50,
    weight_exfiltration = 2.30
WHERE customer_id = 'customer-A'
  AND zone_type = 'SERVER';
```

---

## 📊 监控和审计

### 配置变更审计
```sql
-- 创建审计表
CREATE TABLE ip_segment_config_audit (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50),
    segment_name VARCHAR(255),
    action VARCHAR(20),  -- INSERT, UPDATE, DELETE
    changed_by VARCHAR(100),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    old_values JSONB,
    new_values JSONB
);

-- 创建触发器
CREATE OR REPLACE FUNCTION audit_ip_segment_changes()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        INSERT INTO ip_segment_config_audit (customer_id, segment_name, action, old_values)
        VALUES (OLD.customer_id, OLD.segment_name, 'DELETE', row_to_json(OLD));
        RETURN OLD;
    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO ip_segment_config_audit (customer_id, segment_name, action, old_values, new_values)
        VALUES (NEW.customer_id, NEW.segment_name, 'UPDATE', row_to_json(OLD), row_to_json(NEW));
        RETURN NEW;
    ELSIF TG_OP = 'INSERT' THEN
        INSERT INTO ip_segment_config_audit (customer_id, segment_name, action, new_values)
        VALUES (NEW.customer_id, NEW.segment_name, 'INSERT', row_to_json(NEW));
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_ip_segment
AFTER INSERT OR UPDATE OR DELETE ON ip_segment_weight_config
FOR EACH ROW EXECUTE FUNCTION audit_ip_segment_changes();
```

---

## ✅ 迁移检查清单

### 迁移前
- [ ] 备份生产数据库
- [ ] 在测试环境验证迁移脚本
- [ ] 记录当前表结构和记录数
- [ ] 导出客户自定义配置

### 迁移中
- [ ] 停止应用服务（可选，取决于迁移策略）
- [ ] 执行表结构升级
- [ ] 数据转换和迁移
- [ ] 创建新索引和约束

### 迁移后
- [ ] 验证表结构正确
- [ ] 验证数据完整性（记录数、唯一性）
- [ ] 测试方向性权重函数
- [ ] 测试威胁评分计算
- [ ] 重启应用服务
- [ ] 监控系统日志
- [ ] 对比迁移前后评分差异

---

## 📞 支持

如有问题，请联系开发团队或参考文档：
- 设计文档: `docs/design/ip_segment_weight_system_v2.md`
- API文档: `docs/api/ip_segment_weight_api.md`（待创建）
- 配置指南: `docs/guides/ip_segment_configuration_guide.md`（待创建）
