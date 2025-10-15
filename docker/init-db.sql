-- Database initialization script for threat detection system
-- This script creates the necessary tables and inserts sample data

-- Drop existing table if it exists to ensure clean recreation
DROP TABLE IF EXISTS device_customer_mapping CASCADE;

-- Create device_customer_mapping table
CREATE TABLE device_customer_mapping (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dev_serial VARCHAR(50) NOT NULL UNIQUE,
    customer_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT true,
    description TEXT
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_dev_serial ON device_customer_mapping(dev_serial);
CREATE INDEX IF NOT EXISTS idx_customer_id ON device_customer_mapping(customer_id);
CREATE INDEX IF NOT EXISTS idx_active_mapping ON device_customer_mapping(is_active) WHERE is_active = true;

-- Insert test data for E2E testing (based on real log files)
-- ============================================================================
-- 真实测试数据映射表
-- ============================================================================
INSERT INTO device_customer_mapping (dev_serial, customer_id, description) VALUES
-- Customer A
('10221e5a3be0cf2d', 'customer_a', 'Customer A - Device 1'),
('eebe4c42df504ea5', 'customer_a', 'Customer A - Device 2'),

-- Customer B
('44056bfd85030e0e', 'customer_b', 'Customer B - Device 1'),
('5355ac453fe4e74d', 'customer_b', 'Customer B - Device 2'),

-- Customer C
('578b8eed4856244d', 'customer_c', 'Customer C - Device 1'),
('6360b776893dc0cc', 'customer_c', 'Customer C - Device 2'),
('GSFB2204200410007425', 'customer_c', 'Customer C - Device 3'),

-- Customer D
('a1fce03baf456aba', 'customer_d', 'Customer D - Device 1'),
('a458749d86a13bde', 'customer_d', 'Customer D - Device 2'),
('bce9288a4caa2c61', 'customer_d', 'Customer D - Device 3'),
('c606765df087c8a6', 'customer_d', 'Customer D - Device 4'),

-- Customer E
('caa0beea29676c6d', 'customer_e', 'Customer E - Primary Device'),

-- Customer F
('df01185343413132381002b2aaf96900', 'customer_f', 'Customer F - Primary Device')
ON CONFLICT (dev_serial) DO NOTHING;

-- Create a function to update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger to automatically update updated_at
CREATE TRIGGER update_device_customer_mapping_updated_at
    BEFORE UPDATE ON device_customer_mapping
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Grant permissions (adjust as needed for your application)
GRANT SELECT, INSERT, UPDATE, DELETE ON device_customer_mapping TO threat_user;
GRANT USAGE, SELECT ON SEQUENCE device_customer_mapping_id_seq TO threat_user;

-- ============================================================================
-- Device Status History Table (log_type=2 heartbeat data)
-- ============================================================================
-- ✅ 持久化表: 使用 CREATE IF NOT EXISTS 保护历史状态数据
CREATE TABLE IF NOT EXISTS device_status_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dev_serial VARCHAR(50) NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    
    -- 心跳核心数据
    sentry_count INTEGER NOT NULL,           -- 诱饵设备数量 (虚拟哨兵IP数)
    real_host_count INTEGER NOT NULL,        -- 真实在线设备数量
    dev_start_time BIGINT NOT NULL,          -- 设备启用时间 (Unix时间戳)
    dev_end_time BIGINT NOT NULL,            -- 设备到期时间 (-1表示长期有效)
    
    -- 时间戳
    report_time TIMESTAMP NOT NULL,          -- 心跳报告时间点
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 状态分析字段
    is_healthy BOOLEAN DEFAULT true,         -- 设备健康状态
    is_expiring_soon BOOLEAN DEFAULT false,  -- 是否临近到期 (7天内)
    is_expired BOOLEAN DEFAULT false,        -- 是否已过期
    
    -- 变化检测字段
    sentry_count_changed BOOLEAN DEFAULT false,     -- 诱饵数量是否变化
    real_host_count_changed BOOLEAN DEFAULT false,  -- 在线设备数是否变化
    
    -- 原始日志
    raw_log TEXT
);

-- 创建索引以优化查询性能
CREATE INDEX IF NOT EXISTS idx_device_status_dev_serial ON device_status_history(dev_serial);
CREATE INDEX IF NOT EXISTS idx_device_status_customer_id ON device_status_history(customer_id);
CREATE INDEX IF NOT EXISTS idx_device_status_report_time ON device_status_history(report_time DESC);
CREATE INDEX IF NOT EXISTS idx_device_status_dev_serial_time ON device_status_history(dev_serial, report_time DESC);
CREATE INDEX IF NOT EXISTS idx_device_status_unhealthy ON device_status_history(is_healthy) WHERE is_healthy = false;
CREATE INDEX IF NOT EXISTS idx_device_status_expiring ON device_status_history(is_expiring_soon) WHERE is_expiring_soon = true;
CREATE INDEX IF NOT EXISTS idx_device_status_expired ON device_status_history(is_expired) WHERE is_expired = true;

-- 创建视图: 设备最新状态
CREATE OR REPLACE VIEW device_latest_status AS
SELECT DISTINCT ON (dev_serial)
    dev_serial,
    customer_id,
    sentry_count,
    real_host_count,
    dev_start_time,
    dev_end_time,
    report_time,
    is_healthy,
    is_expiring_soon,
    is_expired,
    created_at
FROM device_status_history
ORDER BY dev_serial, report_time DESC;

-- 创建函数: 检测设备到期状态
CREATE OR REPLACE FUNCTION check_device_expiration()
RETURNS TRIGGER AS $$
DECLARE
    current_epoch BIGINT;
    days_until_expiry NUMERIC;
BEGIN
    -- 获取当前Unix时间戳
    current_epoch := EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::BIGINT;
    
    -- 如果 dev_end_time = -1,表示长期有效
    IF NEW.dev_end_time = -1 THEN
        NEW.is_expired := false;
        NEW.is_expiring_soon := false;
    ELSE
        -- 检查是否已过期
        IF NEW.dev_end_time < current_epoch THEN
            NEW.is_expired := true;
            NEW.is_expiring_soon := false;
        ELSE
            -- 计算距离到期还有多少天
            days_until_expiry := (NEW.dev_end_time - current_epoch) / 86400.0;
            
            -- 如果7天内到期,标记为临期
            IF days_until_expiry <= 7 THEN
                NEW.is_expiring_soon := true;
            ELSE
                NEW.is_expiring_soon := false;
            END IF;
            
            NEW.is_expired := false;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建触发器: 自动检测到期状态
DROP TRIGGER IF EXISTS trigger_check_device_expiration ON device_status_history;
CREATE TRIGGER trigger_check_device_expiration
    BEFORE INSERT OR UPDATE ON device_status_history
    FOR EACH ROW EXECUTE FUNCTION check_device_expiration();

-- 创建函数: 检测状态变化
CREATE OR REPLACE FUNCTION detect_status_changes()
RETURNS TRIGGER AS $$
DECLARE
    last_record RECORD;
BEGIN
    -- 查找该设备的上一条记录
    SELECT sentry_count, real_host_count
    INTO last_record
    FROM device_status_history
    WHERE dev_serial = NEW.dev_serial
    ORDER BY report_time DESC
    LIMIT 1;
    
    -- 如果找到上一条记录,比较变化
    IF FOUND THEN
        IF last_record.sentry_count != NEW.sentry_count THEN
            NEW.sentry_count_changed := true;
        END IF;
        
        IF last_record.real_host_count != NEW.real_host_count THEN
            NEW.real_host_count_changed := true;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建触发器: 自动检测状态变化
DROP TRIGGER IF EXISTS trigger_detect_status_changes ON device_status_history;
CREATE TRIGGER trigger_detect_status_changes
    BEFORE INSERT ON device_status_history
    FOR EACH ROW EXECUTE FUNCTION detect_status_changes();

-- 授予权限
GRANT SELECT, INSERT, UPDATE ON device_status_history TO threat_user;
GRANT USAGE, SELECT ON SEQUENCE device_status_history_id_seq TO threat_user;
GRANT SELECT ON device_latest_status TO threat_user;

-- ============================================================================
-- 威胁评估表 (Threat Assessments)
-- ============================================================================
-- 存储所有威胁评分和评估结果
-- ✅ 持久化表: 使用 CREATE IF NOT EXISTS 保护历史评估数据
CREATE TABLE IF NOT EXISTS threat_assessments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    
    -- 客户和攻击者信息
    customer_id VARCHAR(100) NOT NULL,
    attack_mac VARCHAR(17) NOT NULL,
    
    -- 威胁评分
    threat_score DECIMAL(12,2) NOT NULL,
    threat_level VARCHAR(20) NOT NULL CHECK (threat_level IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')),
    
    -- 统计维度
    attack_count INTEGER NOT NULL DEFAULT 0,
    unique_ips INTEGER NOT NULL DEFAULT 0,
    unique_ports INTEGER NOT NULL DEFAULT 0,
    unique_devices INTEGER NOT NULL DEFAULT 0,
    
    -- 时间信息
    assessment_time TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 附加字段 (由 port_weights_migration.sql 添加)
    port_list TEXT,
    port_risk_score DECIMAL(10,2) DEFAULT 0.0,
    detection_tier INTEGER DEFAULT 2
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_threat_assessments_customer ON threat_assessments(customer_id);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_attack_mac ON threat_assessments(attack_mac);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_threat_level ON threat_assessments(threat_level);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_assessment_time ON threat_assessments(assessment_time DESC);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_customer_mac ON threat_assessments(customer_id, attack_mac);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_customer_time ON threat_assessments(customer_id, assessment_time DESC);

-- 授予权限
GRANT SELECT, INSERT, UPDATE ON threat_assessments TO threat_user;
GRANT USAGE, SELECT ON SEQUENCE threat_assessments_id_seq TO threat_user;

COMMENT ON TABLE threat_assessments IS '威胁评估记录 - 存储所有威胁评分和告警';
COMMENT ON COLUMN threat_assessments.attack_mac IS '被诱捕者MAC地址 (内网失陷主机)';
COMMENT ON COLUMN threat_assessments.threat_score IS '威胁评分 (基于攻击次数、IP数、端口数等维度)';
COMMENT ON COLUMN threat_assessments.threat_level IS '威胁等级: CRITICAL/HIGH/MEDIUM/LOW/INFO';
