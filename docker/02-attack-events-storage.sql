-- ============================================================================
-- 原始攻击事件存储表 (用于追溯和审计)
-- ============================================================================
DROP TABLE IF EXISTS attack_events CASCADE;

CREATE TABLE attack_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    
    -- 客户和设备信息
    customer_id VARCHAR(100) NOT NULL,
    dev_serial VARCHAR(50) NOT NULL,
    
    -- 攻击者信息 (被诱捕的内网失陷主机)
    attack_mac VARCHAR(17) NOT NULL,
    attack_ip VARCHAR(45),  -- 支持IPv6
    
    -- 诱饵信息 (虚拟哨兵)
    response_ip VARCHAR(45) NOT NULL,
    response_port INTEGER NOT NULL,
    
    -- 时间戳
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    log_time BIGINT,
    received_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 元数据
    raw_log_data JSONB,  -- 完整的原始日志JSON
    
    -- 索引字段
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 性能优化索引
CREATE INDEX idx_attack_events_customer ON attack_events(customer_id);
CREATE INDEX idx_attack_events_attack_mac ON attack_events(attack_mac);
CREATE INDEX idx_attack_events_timestamp ON attack_events(event_timestamp DESC);
CREATE INDEX idx_attack_events_customer_mac ON attack_events(customer_id, attack_mac);
CREATE INDEX idx_attack_events_customer_time ON attack_events(customer_id, event_timestamp DESC);
CREATE INDEX idx_attack_events_response_port ON attack_events(response_port);

-- JSONB字段GIN索引(用于快速查询原始日志)
CREATE INDEX idx_attack_events_raw_log ON attack_events USING GIN (raw_log_data);

COMMENT ON TABLE attack_events IS '原始攻击事件存储 - 所有蜜罐检测到的探测行为';
COMMENT ON COLUMN attack_events.attack_mac IS '被诱捕者MAC地址 (内网失陷主机)';
COMMENT ON COLUMN attack_events.response_ip IS '诱饵IP (不存在的虚拟哨兵)';
COMMENT ON COLUMN attack_events.response_port IS '攻击者尝试的端口 (暴露攻击意图)';


-- ============================================================================
-- 威胁告警历史表 (保存所有评分和告警)
-- ============================================================================
DROP TABLE IF EXISTS threat_alerts CASCADE;

CREATE TABLE threat_alerts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    
    -- 客户信息
    customer_id VARCHAR(100) NOT NULL,
    
    -- 攻击者信息
    attack_mac VARCHAR(17) NOT NULL,
    
    -- 威胁评分
    threat_score DECIMAL(12,2) NOT NULL,
    threat_level VARCHAR(20) NOT NULL CHECK (threat_level IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')),
    
    -- 统计维度
    attack_count INTEGER NOT NULL,
    unique_ips INTEGER NOT NULL,
    unique_ports INTEGER NOT NULL,
    unique_devices INTEGER NOT NULL,
    mixed_port_weight DECIMAL(10,2),
    
    -- 时间窗口信息
    tier INTEGER NOT NULL CHECK (tier IN (1, 2, 3)),
    window_type VARCHAR(50),
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    window_end TIMESTAMP WITH TIME ZONE NOT NULL,
    
    -- 告警时间
    alert_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- 处理状态
    status VARCHAR(20) DEFAULT 'NEW' CHECK (status IN ('NEW', 'REVIEWED', 'RESOLVED', 'FALSE_POSITIVE')),
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    
    -- 完整JSON数据(保留所有字段)
    raw_alert_data JSONB
);

-- 性能优化索引
CREATE INDEX idx_threat_alerts_customer ON threat_alerts(customer_id);
CREATE INDEX idx_threat_alerts_mac ON threat_alerts(attack_mac);
CREATE INDEX idx_threat_alerts_level ON threat_alerts(threat_level);
CREATE INDEX idx_threat_alerts_tier ON threat_alerts(tier);
CREATE INDEX idx_threat_alerts_timestamp ON threat_alerts(alert_timestamp DESC);
CREATE INDEX idx_threat_alerts_status ON threat_alerts(status);
CREATE INDEX idx_threat_alerts_customer_time ON threat_alerts(customer_id, alert_timestamp DESC);
CREATE INDEX idx_threat_alerts_customer_mac_time ON threat_alerts(customer_id, attack_mac, alert_timestamp DESC);

-- JSONB字段GIN索引
CREATE INDEX idx_threat_alerts_raw_data ON threat_alerts USING GIN (raw_alert_data);

COMMENT ON TABLE threat_alerts IS '威胁告警历史 - 所有3层窗口产生的威胁评分';
COMMENT ON COLUMN threat_alerts.tier IS '检测层级: 1=30秒勒索软件, 2=5分钟主要威胁, 3=15分钟APT';
COMMENT ON COLUMN threat_alerts.status IS '处理状态: NEW=新告警, REVIEWED=已审核, RESOLVED=已解决, FALSE_POSITIVE=误报';


-- ============================================================================
-- 权限授予
-- ============================================================================
GRANT SELECT, INSERT, UPDATE ON attack_events TO threat_user;
GRANT USAGE, SELECT ON SEQUENCE attack_events_id_seq TO threat_user;

GRANT SELECT, INSERT, UPDATE ON threat_alerts TO threat_user;
GRANT USAGE, SELECT ON SEQUENCE threat_alerts_id_seq TO threat_user;


-- ============================================================================
-- 查询视图 (便于客户快速查阅)
-- ============================================================================

-- 最新告警视图
CREATE OR REPLACE VIEW v_recent_alerts AS
SELECT 
    ta.id,
    ta.customer_id,
    ta.attack_mac,
    ta.threat_score,
    ta.threat_level,
    ta.attack_count,
    ta.unique_ips,
    ta.unique_ports,
    ta.tier,
    ta.window_type,
    ta.alert_timestamp,
    ta.status,
    -- 关联最早的攻击事件时间
    (SELECT MIN(event_timestamp) 
     FROM attack_events ae 
     WHERE ae.customer_id = ta.customer_id 
       AND ae.attack_mac = ta.attack_mac 
       AND ae.event_timestamp >= ta.window_start 
       AND ae.event_timestamp < ta.window_end
    ) as first_attack_time,
    -- 统计该告警期间的攻击事件数
    (SELECT COUNT(*) 
     FROM attack_events ae 
     WHERE ae.customer_id = ta.customer_id 
       AND ae.attack_mac = ta.attack_mac 
       AND ae.event_timestamp >= ta.window_start 
       AND ae.event_timestamp < ta.window_end
    ) as total_events
FROM threat_alerts ta
WHERE ta.alert_timestamp >= NOW() - INTERVAL '7 days'
ORDER BY ta.alert_timestamp DESC;

COMMENT ON VIEW v_recent_alerts IS '最近7天的告警摘要 - 包含关联的原始事件统计';


-- 客户告警汇总视图
CREATE OR REPLACE VIEW v_customer_alert_summary AS
SELECT 
    customer_id,
    COUNT(*) as total_alerts,
    COUNT(DISTINCT attack_mac) as unique_attackers,
    SUM(CASE WHEN threat_level = 'CRITICAL' THEN 1 ELSE 0 END) as critical_alerts,
    SUM(CASE WHEN threat_level = 'HIGH' THEN 1 ELSE 0 END) as high_alerts,
    SUM(CASE WHEN threat_level = 'MEDIUM' THEN 1 ELSE 0 END) as medium_alerts,
    SUM(CASE WHEN threat_level = 'LOW' THEN 1 ELSE 0 END) as low_alerts,
    MAX(alert_timestamp) as last_alert_time,
    COUNT(CASE WHEN status = 'NEW' THEN 1 END) as pending_review
FROM threat_alerts
WHERE alert_timestamp >= NOW() - INTERVAL '30 days'
GROUP BY customer_id;

COMMENT ON VIEW v_customer_alert_summary IS '客户告警汇总 - 最近30天统计';


-- 权限授予视图
GRANT SELECT ON v_recent_alerts TO threat_user;
GRANT SELECT ON v_customer_alert_summary TO threat_user;
