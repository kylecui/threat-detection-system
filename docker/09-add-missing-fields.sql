-- ============================================================================
-- 添加缺失的字段到 threat_assessments 表
-- 用于修复 POST /assessment/evaluate API 的数据库错误
-- ============================================================================

-- 添加 attack_ip 字段 (攻击源IP地址)
ALTER TABLE threat_assessments 
ADD COLUMN IF NOT EXISTS attack_ip VARCHAR(45);

-- 添加权重字段 (用于审计和调试)
ALTER TABLE threat_assessments 
ADD COLUMN IF NOT EXISTS time_weight DECIMAL(5,2);

ALTER TABLE threat_assessments 
ADD COLUMN IF NOT EXISTS ip_weight DECIMAL(5,2);

ALTER TABLE threat_assessments 
ADD COLUMN IF NOT EXISTS port_weight DECIMAL(5,2);

ALTER TABLE threat_assessments 
ADD COLUMN IF NOT EXISTS device_weight DECIMAL(5,2);

-- 添加缓解建议字段
ALTER TABLE threat_assessments 
ADD COLUMN IF NOT EXISTS mitigation_recommendations TEXT;

-- 添加缓解状态字段
ALTER TABLE threat_assessments 
ADD COLUMN IF NOT EXISTS mitigation_status VARCHAR(50);

-- 添加更新时间字段
ALTER TABLE threat_assessments 
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

-- 创建索引以优化查询
CREATE INDEX IF NOT EXISTS idx_threat_assessments_attack_ip ON threat_assessments(attack_ip);
CREATE INDEX IF NOT EXISTS idx_threat_assessments_mitigation_status ON threat_assessments(mitigation_status);

-- 添加注释
COMMENT ON COLUMN threat_assessments.attack_ip IS '被诱捕者IP地址 (内网失陷主机IP)';
COMMENT ON COLUMN threat_assessments.time_weight IS '时间权重因子 (基于攻击发生时间段)';
COMMENT ON COLUMN threat_assessments.ip_weight IS 'IP权重因子 (基于唯一IP数量)';
COMMENT ON COLUMN threat_assessments.port_weight IS '端口权重因子 (基于唯一端口数量)';
COMMENT ON COLUMN threat_assessments.device_weight IS '设备权重因子 (基于唯一设备数量)';
COMMENT ON COLUMN threat_assessments.mitigation_recommendations IS '缓解建议 (JSON数组格式)';
COMMENT ON COLUMN threat_assessments.mitigation_status IS '缓解状态: PENDING/IN_PROGRESS/COMPLETED/FAILED';
COMMENT ON COLUMN threat_assessments.updated_at IS '记录最后更新时间';

-- 验证字段添加结果
DO $$ 
DECLARE
    field_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO field_count
    FROM information_schema.columns
    WHERE table_name = 'threat_assessments'
    AND column_name IN ('attack_ip', 'time_weight', 'ip_weight', 'port_weight', 
                       'device_weight', 'mitigation_recommendations', 
                       'mitigation_status', 'updated_at');
    
    IF field_count = 8 THEN
        RAISE NOTICE '✅ Successfully added 8 missing fields to threat_assessments table';
    ELSE
        RAISE WARNING '⚠️ Expected 8 fields but found %', field_count;
    END IF;
END $$;
