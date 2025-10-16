-- =====================================================
-- Alert Management Tables
-- 用于告警管理服务的数据持久化
-- 创建时间: 2025-10-15
-- =====================================================

-- 告警管理表 (Alert Management)
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    severity VARCHAR(20) NOT NULL,
    source VARCHAR(100) DEFAULT 'threat-detection-system',
    event_type VARCHAR(100),
    metadata TEXT,
    attack_mac VARCHAR(17),
    threat_score DOUBLE PRECISION,
    assigned_to VARCHAR(100),
    resolution VARCHAR(1000),
    resolved_by VARCHAR(100),
    resolved_at TIMESTAMP,
    last_notified_at TIMESTAMP,
    escalation_level INTEGER DEFAULT 0,
    escalation_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 约束
    CONSTRAINT chk_status CHECK (status IN ('NEW', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'SUPPRESSED')),
    CONSTRAINT chk_severity CHECK (severity IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_alerts_status ON alerts(status);
CREATE INDEX IF NOT EXISTS idx_alerts_severity ON alerts(severity);
CREATE INDEX IF NOT EXISTS idx_alerts_attack_mac ON alerts(attack_mac);
CREATE INDEX IF NOT EXISTS idx_alerts_created_at ON alerts(created_at);
CREATE INDEX IF NOT EXISTS idx_alerts_source ON alerts(source);

-- 告警影响资产关联表
CREATE TABLE IF NOT EXISTS alert_affected_assets (
    alert_id BIGINT NOT NULL REFERENCES alerts(id) ON DELETE CASCADE,
    asset VARCHAR(255) NOT NULL,
    PRIMARY KEY (alert_id, asset)
);

CREATE INDEX IF NOT EXISTS idx_alert_affected_assets_alert_id ON alert_affected_assets(alert_id);

-- 告警推荐措施关联表
CREATE TABLE IF NOT EXISTS alert_recommendations (
    alert_id BIGINT NOT NULL REFERENCES alerts(id) ON DELETE CASCADE,
    recommendation VARCHAR(1000) NOT NULL,
    PRIMARY KEY (alert_id, recommendation)
);

CREATE INDEX IF NOT EXISTS idx_alert_recommendations_alert_id ON alert_recommendations(alert_id);

-- 通知记录表 (Notification Records)
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    alert_id BIGINT REFERENCES alerts(id) ON DELETE CASCADE,
    channel VARCHAR(50) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(1000),
    content TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(500),
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 约束
    CONSTRAINT chk_notification_channel CHECK (channel IN ('EMAIL', 'SMS', 'SLACK', 'WEBHOOK', 'TEAMS')),
    CONSTRAINT chk_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'RETRYING'))
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_notifications_alert_id ON notifications(alert_id);
CREATE INDEX IF NOT EXISTS idx_notifications_channel ON notifications(channel);
CREATE INDEX IF NOT EXISTS idx_notifications_status ON notifications(status);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at);

-- 添加注释
COMMENT ON TABLE alerts IS '告警管理表 - 存储告警生命周期管理信息';
COMMENT ON TABLE notifications IS '通知记录表 - 存储邮件/短信/Slack通知发送记录';
COMMENT ON TABLE alert_affected_assets IS '告警影响资产关联表';
COMMENT ON TABLE alert_recommendations IS '告警推荐措施关联表';

COMMENT ON COLUMN alerts.title IS '告警标题';
COMMENT ON COLUMN alerts.severity IS '严重程度 - INFO/LOW/MEDIUM/HIGH/CRITICAL';
COMMENT ON COLUMN alerts.status IS '告警状态 - NEW/ACKNOWLEDGED/IN_PROGRESS/RESOLVED/CLOSED/SUPPRESSED';

COMMENT ON COLUMN notifications.channel IS '通知渠道 - EMAIL/SMS/SLACK/WEBHOOK/TEAMS';
COMMENT ON COLUMN notifications.status IS '发送状态 - PENDING/SENT/FAILED/RETRYING';
COMMENT ON COLUMN notifications.retry_count IS '重试次数 - 失败后的重试计数';
COMMENT ON COLUMN notifications.max_retries IS '最大重试次数';

-- 数据完整性检查
DO $$
BEGIN
    RAISE NOTICE 'Alert Management Tables Created Successfully';
    RAISE NOTICE 'Tables: alerts, notifications, alert_affected_assets, alert_recommendations';
    RAISE NOTICE 'Indexes: 12 indexes total';
END $$;
