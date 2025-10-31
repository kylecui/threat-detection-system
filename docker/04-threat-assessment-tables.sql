-- =====================================================
-- Threat Assessment Tables
-- 用于威胁评估服务的数据持久化
-- 创建时间: 2025-11-01
-- =====================================================

-- 威胁评估表 (Threat Assessment)
CREATE TABLE IF NOT EXISTS threat_assessments (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    attack_mac VARCHAR(17) NOT NULL,
    threat_score DECIMAL(10,2) NOT NULL,
    threat_level VARCHAR(20) NOT NULL,
    attack_count INTEGER NOT NULL,
    unique_ips INTEGER NOT NULL,
    unique_ports INTEGER NOT NULL,
    unique_devices INTEGER NOT NULL,
    assessment_time TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 扩展字段
    port_list TEXT,
    port_risk_score DECIMAL(5,2) DEFAULT 0.0,
    detection_tier INTEGER DEFAULT 2,

    -- 权重因子 (用于审计和调试)
    time_weight DECIMAL(5,2),
    ip_weight DECIMAL(5,2),
    port_weight DECIMAL(5,2),
    device_weight DECIMAL(5,2),

    -- 攻击源IP
    attack_ip VARCHAR(45),

    -- 缓解相关字段
    mitigation_recommendations TEXT,
    mitigation_status VARCHAR(50),
    updated_at TIMESTAMP WITH TIME ZONE,

    -- 约束
    CONSTRAINT chk_threat_level CHECK (threat_level IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO')),
    CONSTRAINT chk_detection_tier CHECK (detection_tier IN (1, 2, 3)),
    CONSTRAINT chk_mitigation_status CHECK (mitigation_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_threat_assessments_customer ON threat_assessments(customer_id);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_attack_mac ON threat_assessments(attack_mac);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_customer_mac ON threat_assessments(customer_id, attack_mac);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_assessment_time ON threat_assessments(assessment_time);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_threat_level ON threat_assessments(threat_level);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_attack_ip ON threat_assessments(attack_ip);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_mitigation_status ON threat_assessments(mitigation_status);

-- 添加注释
COMMENT ON TABLE threat_assessments IS '威胁评估表 - 存储威胁评分和评估结果';
COMMENT ON COLUMN threat_assessments.customer_id IS '客户ID';
COMMENT ON COLUMN threat_assessments.attack_mac IS '被诱捕者MAC地址 (内网失陷主机)';
COMMENT ON COLUMN threat_assessments.threat_score IS '威胁评分 (基于蜜罐机制计算)';
COMMENT ON COLUMN threat_assessments.threat_level IS '威胁等级: CRITICAL/HIGH/MEDIUM/LOW/INFO';
COMMENT ON COLUMN threat_assessments.attack_count IS '攻击尝试次数';
COMMENT ON COLUMN threat_assessments.unique_ips IS '访问的诱饵IP数量 (横向移动范围)';
COMMENT ON COLUMN threat_assessments.unique_ports IS '尝试的端口种类 (攻击意图多样性)';
COMMENT ON COLUMN threat_assessments.unique_devices IS '检测到该攻击者的设备数';
COMMENT ON COLUMN threat_assessments.assessment_time IS '评估时间';
COMMENT ON COLUMN threat_assessments.port_list IS '涉及的端口列表 (JSON格式)';
COMMENT ON COLUMN threat_assessments.port_risk_score IS '端口风险评分';
COMMENT ON COLUMN threat_assessments.detection_tier IS '检测层级: 1=30秒, 2=5分钟, 3=15分钟';
COMMENT ON COLUMN threat_assessments.time_weight IS '时间权重因子 (基于攻击发生时间段)';
COMMENT ON COLUMN threat_assessments.ip_weight IS 'IP权重因子 (基于唯一IP数量)';
COMMENT ON COLUMN threat_assessments.port_weight IS '端口权重因子 (基于唯一端口数量)';
COMMENT ON COLUMN threat_assessments.device_weight IS '设备权重因子 (基于唯一设备数量)';
COMMENT ON COLUMN threat_assessments.attack_ip IS '被诱捕者IP地址 (内网地址)';
COMMENT ON COLUMN threat_assessments.mitigation_recommendations IS '缓解建议 (JSON数组格式)';
COMMENT ON COLUMN threat_assessments.mitigation_status IS '缓解状态: PENDING/IN_PROGRESS/COMPLETED/FAILED';
COMMENT ON COLUMN threat_assessments.updated_at IS '记录最后更新时间';

-- 权限授予
GRANT SELECT, INSERT, UPDATE ON threat_assessments TO threat_user;
GRANT USAGE, SELECT ON SEQUENCE threat_assessments_id_seq TO threat_user;

-- 数据完整性检查
DO $$
BEGIN
    RAISE NOTICE 'Threat Assessment Tables Created Successfully';
    RAISE NOTICE 'Table: threat_assessments';
    RAISE NOTICE 'Indexes: 7 indexes total';
END $$;