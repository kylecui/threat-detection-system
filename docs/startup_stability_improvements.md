# 启动稳定性改进建议

## 🎯 核心问题

当前系统的启动流程存在**数据持久性风险**：所有数据库表在初始化时都使用`DROP TABLE IF EXISTS`，导致历史业务数据在某些场景下会被意外清空。

## 📊 风险评估

### 高风险场景

| 场景 | 命令 | 后果 | 概率 |
|------|------|------|------|
| 完全重建环境 | `docker-compose down -v && docker-compose up -d` | **所有历史数据丢失** | 高 |
| 测试清理 | `docker volume rm threat-detection-system_postgres_data` | **所有历史数据丢失** | 中 |
| 错误升级 | 修改init-db.sql后重建卷 | **部分数据丢失** | 低 |

## ✅ 改进方案

### 方案1: 分离持久化表和配置表（推荐）

#### 实施步骤

**步骤1**: 重组SQL文件结构

```bash
cd /home/kylecui/threat-detection-system/docker

# 备份现有文件
cp init-db.sql init-db.sql.backup
cp 02-attack-events-storage.sql 02-attack-events-storage.sql.backup

# 创建新的文件结构
# 01-schema-persistent.sql    - 持久化表 (不能DROP)
# 02-schema-config.sql        - 配置表 (可以DROP)
# 03-config-data.sql          - 配置数据
# 04-functions-triggers.sql   - 函数和触发器
# 05-views.sql                - 视图
```

**步骤2**: 创建持久化表SQL (参考01-schema-persistent.sql.example)

关键点:
```sql
-- ❌ 错误: 会删除历史数据
DROP TABLE IF EXISTS attack_events CASCADE;
CREATE TABLE attack_events (...);

-- ✅ 正确: 保留历史数据
CREATE TABLE IF NOT EXISTS attack_events (...);

-- ✅ 正确: 所有索引也使用IF NOT EXISTS
CREATE INDEX IF NOT EXISTS idx_attack_events_customer ON attack_events(customer_id);
```

**步骤3**: 创建配置表SQL

```sql
-- 配置表可以安全DROP (数据可重建)
DROP TABLE IF EXISTS device_customer_mapping CASCADE;
CREATE TABLE device_customer_mapping (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dev_serial VARCHAR(50) NOT NULL UNIQUE,
    customer_id VARCHAR(100) NOT NULL,
    -- ...
);

DROP TABLE IF EXISTS port_risk_configs CASCADE;
CREATE TABLE port_risk_configs (
    id SERIAL PRIMARY KEY,
    port_number INTEGER NOT NULL UNIQUE,
    -- ...
);
```

**步骤4**: 配置数据使用幂等性插入

```sql
-- 幂等性INSERT
INSERT INTO device_customer_mapping (dev_serial, customer_id, description) VALUES
('10221e5a3be0cf2d', 'customer_a', 'Customer A - Device 1')
ON CONFLICT (dev_serial) DO NOTHING;  -- 重复执行安全

-- 或者需要更新时
ON CONFLICT (dev_serial) DO UPDATE 
SET customer_id = EXCLUDED.customer_id;
```

**步骤5**: 函数和触发器使用CREATE OR REPLACE

```sql
-- 函数幂等性
CREATE OR REPLACE FUNCTION check_device_expiration()
RETURNS TRIGGER AS $$
-- ...
$$ LANGUAGE plpgsql;

-- 触发器幂等性
DROP TRIGGER IF EXISTS trigger_check_device_expiration ON device_status_history;
CREATE TRIGGER trigger_check_device_expiration
    BEFORE INSERT OR UPDATE ON device_status_history
    FOR EACH ROW EXECUTE FUNCTION check_device_expiration();
```

**步骤6**: 视图使用CREATE OR REPLACE

```sql
-- 视图幂等性
CREATE OR REPLACE VIEW v_recent_alerts AS
SELECT 
    ta.id,
    ta.customer_id,
    ta.attack_mac,
    ta.threat_score,
    -- ...
FROM threat_alerts ta
WHERE ta.alert_timestamp >= NOW() - INTERVAL '7 days'
ORDER BY ta.alert_timestamp DESC;
```

#### 文件执行顺序

Docker Compose会按字母顺序执行`/docker-entrypoint-initdb.d/`下的文件：

```
01-schema-persistent.sql     ← 持久化表 (CREATE IF NOT EXISTS)
02-schema-config.sql         ← 配置表 (DROP + CREATE)
03-config-data.sql           ← 配置数据 (INSERT ... ON CONFLICT)
04-functions-triggers.sql    ← 函数触发器 (CREATE OR REPLACE)
05-views.sql                 ← 视图 (CREATE OR REPLACE)
```

### 方案2: 添加智能迁移脚本

创建 `docker/00-check-and-migrate.sql`:

```sql
-- ============================================
-- 智能数据库迁移脚本
-- 检查表是否存在,只在首次创建时执行完整建表
-- 已有数据时只做增量更新
-- ============================================

DO $$
DECLARE
    attack_events_exists BOOLEAN;
    threat_alerts_exists BOOLEAN;
BEGIN
    -- 检查attack_events表
    SELECT EXISTS (
        SELECT FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'attack_events'
    ) INTO attack_events_exists;
    
    IF attack_events_exists THEN
        RAISE NOTICE '✅ attack_events表已存在,执行增量更新';
        
        -- 增量添加列 (向后兼容)
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'attack_events' AND column_name = 'raw_log_data'
        ) THEN
            RAISE NOTICE '➕ 添加raw_log_data列';
            ALTER TABLE attack_events ADD COLUMN raw_log_data JSONB;
        END IF;
        
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'attack_events' AND column_name = 'received_at'
        ) THEN
            RAISE NOTICE '➕ 添加received_at列';
            ALTER TABLE attack_events ADD COLUMN received_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
        END IF;
        
    ELSE
        RAISE NOTICE '🆕 创建attack_events表';
        -- 完整的CREATE TABLE语句
        CREATE TABLE attack_events (
            id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            customer_id VARCHAR(100) NOT NULL,
            -- ... 完整字段
        );
    END IF;
    
    -- 检查threat_alerts表
    SELECT EXISTS (
        SELECT FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'threat_alerts'
    ) INTO threat_alerts_exists;
    
    IF threat_alerts_exists THEN
        RAISE NOTICE '✅ threat_alerts表已存在,执行增量更新';
        
        -- 增量添加列
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'threat_alerts' AND column_name = 'unique_devices'
        ) THEN
            RAISE NOTICE '➕ 添加unique_devices列';
            ALTER TABLE threat_alerts ADD COLUMN unique_devices INTEGER NOT NULL DEFAULT 0;
        END IF;
        
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = 'threat_alerts' AND column_name = 'mixed_port_weight'
        ) THEN
            RAISE NOTICE '➕ 添加mixed_port_weight列';
            ALTER TABLE threat_alerts ADD COLUMN mixed_port_weight DECIMAL(10,2);
        END IF;
        
    ELSE
        RAISE NOTICE '🆕 创建threat_alerts表';
        -- 完整的CREATE TABLE语句
        CREATE TABLE threat_alerts (
            id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            customer_id VARCHAR(100) NOT NULL,
            -- ... 完整字段
        );
    END IF;
    
END $$;
```

### 方案3: 数据备份和恢复机制

#### 自动备份脚本

创建 `scripts/tools/backup_database.sh`:

```bash
#!/bin/bash
# 数据库自动备份脚本

set -e

BACKUP_DIR="/home/kylecui/threat-detection-system/backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/threat_detection_${TIMESTAMP}.sql"

# 创建备份目录
mkdir -p "${BACKUP_DIR}"

echo "🔄 开始备份数据库..."

# 备份整个数据库
docker exec postgres pg_dump -U threat_user threat_detection > "${BACKUP_FILE}"

# 压缩备份文件
gzip "${BACKUP_FILE}"

echo "✅ 备份完成: ${BACKUP_FILE}.gz"

# 只保留最近7天的备份
find "${BACKUP_DIR}" -name "*.sql.gz" -mtime +7 -delete

echo "📊 当前备份列表:"
ls -lh "${BACKUP_DIR}"
```

#### 恢复脚本

创建 `scripts/tools/restore_database.sh`:

```bash
#!/bin/bash
# 数据库恢复脚本

set -e

if [ -z "$1" ]; then
    echo "用法: $0 <backup_file.sql.gz>"
    echo ""
    echo "可用备份:"
    ls -lh /home/kylecui/threat-detection-system/backups/
    exit 1
fi

BACKUP_FILE="$1"

echo "⚠️  警告: 此操作将恢复数据库到备份时的状态"
read -p "确认继续? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "已取消"
    exit 0
fi

echo "🔄 开始恢复数据库..."

# 解压备份文件
gunzip -c "${BACKUP_FILE}" | docker exec -i postgres psql -U threat_user threat_detection

echo "✅ 恢复完成"
```

## 🛡️ 安全操作指南

### 安全的重启流程

```bash
# ✅ 安全: 只重启服务,不删除数据
docker-compose restart

# ✅ 安全: 停止服务但保留数据卷
docker-compose down
docker-compose up -d

# ⚠️  警告: 会触发重新初始化,但现有数据会保留(使用CREATE IF NOT EXISTS)
docker-compose down
docker volume rm threat-detection-system_postgres_data
docker-compose up -d

# ❌ 危险: 会删除所有数据 (仅在明确需要时使用)
docker-compose down -v
```

### 安全的测试流程

```bash
# 1. 备份当前数据
./scripts/tools/backup_database.sh

# 2. 运行测试
python3 scripts/test/full_integration_test.py

# 3. 如需清理测试数据,只删除最近的记录
docker exec -it postgres psql -U threat_user -d threat_detection -c "
DELETE FROM attack_events WHERE created_at > NOW() - INTERVAL '1 hour';
DELETE FROM threat_alerts WHERE created_at > NOW() - INTERVAL '1 hour';
"

# 4. 或者恢复备份
./scripts/tools/restore_database.sh backups/threat_detection_20250115_103000.sql.gz
```

## 📋 实施检查清单

### 立即执行 (P0 - 高优先级)

- [ ] **重构init-db.sql**: 将持久化表改为`CREATE TABLE IF NOT EXISTS`
- [ ] **重构02-attack-events-storage.sql**: 同上
- [ ] **测试幂等性**: 重复执行初始化脚本验证无副作用
- [ ] **创建备份脚本**: `backup_database.sh`
- [ ] **更新文档**: 将改进方案写入README

### 本周完成 (P1 - 中优先级)

- [ ] **分离SQL文件**: 按照方案1重组文件结构
- [ ] **添加迁移脚本**: `00-check-and-migrate.sql`
- [ ] **创建恢复脚本**: `restore_database.sh`
- [ ] **添加健康检查**: 验证数据完整性
- [ ] **CI/CD集成**: 自动备份策略

### 长期优化 (P2 - 低优先级)

- [ ] **使用Flyway/Liquibase**: 专业数据库版本管理
- [ ] **数据分区**: 历史数据按月分区
- [ ] **冷热分离**: 归档90天以上数据
- [ ] **监控告警**: 数据丢失自动告警

## 🔗 相关资源

- [详细文档](./database_initialization_guide.md)
- [Docker Compose配置](../docker/docker-compose.yml)
- [持久化表示例](../docker/01-schema-persistent.sql.example)

---

**最后更新**: 2025-01-15  
**优先级**: P0 - 高优先级  
**负责人**: DevOps Team
