-- =====================================================
-- Notification Configuration Tables
-- 通知配置表 - 支持动态配置SMTP和客户邮箱
-- 创建时间: 2025-10-15
-- =====================================================

-- SMTP服务器配置表
CREATE TABLE IF NOT EXISTS smtp_configs (
    id BIGSERIAL PRIMARY KEY,
    config_name VARCHAR(100) NOT NULL UNIQUE,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(500) NOT NULL,
    from_address VARCHAR(255) NOT NULL,
    from_name VARCHAR(255) DEFAULT 'Threat Detection System',
    
    -- 安全配置
    enable_tls BOOLEAN DEFAULT false,
    enable_ssl BOOLEAN DEFAULT false,
    enable_starttls BOOLEAN DEFAULT false,
    
    -- 连接配置
    connection_timeout INTEGER DEFAULT 5000,
    timeout INTEGER DEFAULT 5000,
    write_timeout INTEGER DEFAULT 5000,
    
    -- 状态
    is_active BOOLEAN DEFAULT true,
    is_default BOOLEAN DEFAULT false,
    
    -- 元数据
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100) DEFAULT 'system',
    
    -- 约束
    CONSTRAINT chk_smtp_port CHECK (port > 0 AND port <= 65535)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_smtp_configs_active ON smtp_configs(is_active);
CREATE INDEX IF NOT EXISTS idx_smtp_configs_default ON smtp_configs(is_default);

-- 确保只有一个默认配置
CREATE UNIQUE INDEX IF NOT EXISTS idx_smtp_configs_unique_default 
ON smtp_configs(is_default) WHERE is_default = true;

-- 客户通知配置表
CREATE TABLE IF NOT EXISTS customer_notification_configs (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL UNIQUE,
    
    -- 邮件配置
    email_enabled BOOLEAN DEFAULT true,
    email_recipients TEXT NOT NULL, -- JSON数组格式: ["email1@example.com", "email2@example.com"]
    
    -- 短信配置
    sms_enabled BOOLEAN DEFAULT false,
    sms_recipients TEXT, -- JSON数组格式
    
    -- Slack配置
    slack_enabled BOOLEAN DEFAULT false,
    slack_webhook_url VARCHAR(500),
    slack_channel VARCHAR(100),
    
    -- Webhook配置
    webhook_enabled BOOLEAN DEFAULT false,
    webhook_url VARCHAR(500),
    webhook_headers TEXT, -- JSON对象格式
    
    -- 告警级别过滤
    min_severity_level VARCHAR(20) DEFAULT 'MEDIUM',
    notify_on_severities TEXT DEFAULT '["MEDIUM","HIGH","CRITICAL"]', -- JSON数组
    
    -- 通知频率控制
    max_notifications_per_hour INTEGER DEFAULT 100,
    enable_rate_limiting BOOLEAN DEFAULT true,
    
    -- 静默时段配置
    quiet_hours_enabled BOOLEAN DEFAULT false,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    quiet_hours_timezone VARCHAR(50) DEFAULT 'Asia/Shanghai',
    
    -- 状态
    is_active BOOLEAN DEFAULT true,
    
    -- 元数据
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100) DEFAULT 'system',
    
    -- 约束
    CONSTRAINT chk_min_severity CHECK (min_severity_level IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_customer_notification_configs_customer ON customer_notification_configs(customer_id);
CREATE INDEX IF NOT EXISTS idx_customer_notification_configs_active ON customer_notification_configs(is_active);

-- 通知发送历史统计表 (用于频率控制)
CREATE TABLE IF NOT EXISTS notification_rate_limits (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    notification_hour TIMESTAMP NOT NULL, -- 精确到小时
    notification_count INTEGER DEFAULT 0,
    
    -- 复合唯一约束
    CONSTRAINT unique_customer_hour UNIQUE (customer_id, notification_hour)
);

CREATE INDEX IF NOT EXISTS idx_notification_rate_limits_customer ON notification_rate_limits(customer_id);
CREATE INDEX IF NOT EXISTS idx_notification_rate_limits_hour ON notification_rate_limits(notification_hour);

-- =====================================================
-- 初始化默认配置数据
-- =====================================================

-- 插入默认SMTP配置
INSERT INTO smtp_configs (
    config_name,
    host,
    port,
    username,
    password,
    from_address,
    from_name,
    enable_tls,
    enable_ssl,
    enable_starttls,
    is_active,
    is_default,
    description
) VALUES (
    'default-smtp-163',
    'smtp.163.com',
    25,
    'threat_detection@163.com',
    'CHANGEME_SET_VIA_ENV',
    'threat_detection@163.com',
    '威胁检测系统',
    false,
    false,
    false,
    true,
    true,
    '默认SMTP配置 - 网易163邮箱'
) ON CONFLICT (config_name) DO NOTHING;

-- 插入测试客户通知配置
INSERT INTO customer_notification_configs (
    customer_id,
    email_enabled,
    email_recipients,
    sms_enabled,
    slack_enabled,
    webhook_enabled,
    min_severity_level,
    notify_on_severities,
    max_notifications_per_hour,
    enable_rate_limiting,
    is_active,
    description
) VALUES 
(
    'customer_a',
    true,
    '["kylecui@outlook.com"]',
    false,
    false,
    false,
    'CRITICAL',
    '["CRITICAL"]',
    50,
    true,
    true,
    '客户A - 仅CRITICAL级别告警'
),
(
    'customer_b',
    true,
    '["kylecui@outlook.com"]',
    false,
    false,
    false,
    'HIGH',
    '["HIGH","CRITICAL"]',
    50,
    true,
    true,
    '客户B - HIGH及以上级别告警'
),
(
    'customer_c',
    true,
    '["kylecui@outlook.com"]',
    false,
    false,
    false,
    'CRITICAL',
    '["CRITICAL"]',
    50,
    true,
    true,
    '客户C - 仅CRITICAL级别告警'
)
ON CONFLICT (customer_id) DO NOTHING;

-- 添加注释
COMMENT ON TABLE smtp_configs IS 'SMTP服务器配置表 - 支持多个SMTP服务器配置';
COMMENT ON TABLE customer_notification_configs IS '客户通知配置表 - 为每个客户配置独立的通知渠道和规则';
COMMENT ON TABLE notification_rate_limits IS '通知频率限制表 - 记录每小时的通知发送次数';

COMMENT ON COLUMN smtp_configs.is_default IS '是否为默认配置 - 系统级唯一';
COMMENT ON COLUMN customer_notification_configs.email_recipients IS '邮件接收人列表 - JSON数组格式';
COMMENT ON COLUMN customer_notification_configs.notify_on_severities IS '触发通知的告警级别 - JSON数组格式';
COMMENT ON COLUMN customer_notification_configs.max_notifications_per_hour IS '每小时最大通知数量 - 防止告警风暴';

-- 数据完整性检查
DO $$
DECLARE
    smtp_count INTEGER;
    customer_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO smtp_count FROM smtp_configs;
    SELECT COUNT(*) INTO customer_count FROM customer_notification_configs;
    
    RAISE NOTICE 'Notification Configuration Tables Created Successfully';
    RAISE NOTICE 'Tables: smtp_configs, customer_notification_configs, notification_rate_limits';
    RAISE NOTICE 'Default SMTP configs: %', smtp_count;
    RAISE NOTICE 'Customer notification configs: %', customer_count;
END $$;
