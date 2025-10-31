-- ============================================================================
-- 添加缺失的字段到 threat_assessments 表 (安全版本)
-- 用于修复 POST /assessment/evaluate API 的数据库错误
-- 注意: 从 04-threat-assessment-tables.sql 开始，这些字段已经预先创建
-- 此脚本仅作为后备，确保字段存在
-- ============================================================================

-- 检查 threat_assessments 表是否存在
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'threat_assessments') THEN
        RAISE EXCEPTION 'Table threat_assessments does not exist. Please ensure 04-threat-assessment-tables.sql has been executed first.';
    END IF;
END $$;

-- 添加 attack_ip 字段 (如果不存在)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'threat_assessments' AND column_name = 'attack_ip') THEN
        ALTER TABLE threat_assessments ADD COLUMN attack_ip VARCHAR(45);
        RAISE NOTICE 'Added attack_ip column to threat_assessments';
    ELSE
        RAISE NOTICE 'attack_ip column already exists in threat_assessments';
    END IF;
END $$;

-- 添加权重字段 (如果不存在)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'threat_assessments' AND column_name = 'time_weight') THEN
        ALTER TABLE threat_assessments ADD COLUMN time_weight DECIMAL(5,2);
        RAISE NOTICE 'Added time_weight column to threat_assessments';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'threat_assessments' AND column_name = 'ip_weight') THEN
        ALTER TABLE threat_assessments ADD COLUMN ip_weight DECIMAL(5,2);
        RAISE NOTICE 'Added ip_weight column to threat_assessments';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'threat_assessments' AND column_name = 'port_weight') THEN
        ALTER TABLE threat_assessments ADD COLUMN port_weight DECIMAL(5,2);
        RAISE NOTICE 'Added port_weight column to threat_assessments';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'threat_assessments' AND column_name = 'device_weight') THEN
        ALTER TABLE threat_assessments ADD COLUMN device_weight DECIMAL(5,2);
        RAISE NOTICE 'Added device_weight column to threat_assessments';
    END IF;
END $$;

-- 添加缓解相关字段 (如果不存在)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'threat_assessments' AND column_name = 'mitigation_recommendations') THEN
        ALTER TABLE threat_assessments ADD COLUMN mitigation_recommendations TEXT;
        RAISE NOTICE 'Added mitigation_recommendations column to threat_assessments';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'threat_assessments' AND column_name = 'mitigation_status') THEN
        ALTER TABLE threat_assessments ADD COLUMN mitigation_status VARCHAR(50);
        RAISE NOTICE 'Added mitigation_status column to threat_assessments';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name = 'threat_assessments' AND column_name = 'updated_at') THEN
        ALTER TABLE threat_assessments ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;
        RAISE NOTICE 'Added updated_at column to threat_assessments';
    END IF;
END $$;

-- 创建索引 (如果不存在)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_threat_assessments_attack_ip') THEN
        CREATE INDEX idx_threat_assessments_attack_ip ON threat_assessments(attack_ip);
        RAISE NOTICE 'Created index idx_threat_assessments_attack_ip';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_threat_assessments_mitigation_status') THEN
        CREATE INDEX idx_threat_assessments_mitigation_status ON threat_assessments(mitigation_status);
        RAISE NOTICE 'Created index idx_threat_assessments_mitigation_status';
    END IF;
END $$;

-- 添加约束 (如果不存在)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.check_constraints
                   WHERE constraint_name = 'chk_mitigation_status') THEN
        ALTER TABLE threat_assessments
        ADD CONSTRAINT chk_mitigation_status
        CHECK (mitigation_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED'));
        RAISE NOTICE 'Added constraint chk_mitigation_status';
    END IF;
END $$;

-- 更新注释
DO $$
BEGIN
    -- 只有在注释不存在时才添加
    IF NOT EXISTS (SELECT 1 FROM pg_description
                   WHERE objoid = 'threat_assessments'::regclass AND objsubid = (
                       SELECT attnum FROM pg_attribute
                       WHERE attrelid = 'threat_assessments'::regclass AND attname = 'attack_ip'
                   )) THEN
        COMMENT ON COLUMN threat_assessments.attack_ip IS '被诱捕者IP地址 (内网失陷主机IP)';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_description
                   WHERE objoid = 'threat_assessments'::regclass AND objsubid = (
                       SELECT attnum FROM pg_attribute
                       WHERE attrelid = 'threat_assessments'::regclass AND attname = 'time_weight'
                   )) THEN
        COMMENT ON COLUMN threat_assessments.time_weight IS '时间权重因子 (基于攻击发生时间段)';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_description
                   WHERE objoid = 'threat_assessments'::regclass AND objsubid = (
                       SELECT attnum FROM pg_attribute
                       WHERE attrelid = 'threat_assessments'::regclass AND attname = 'ip_weight'
                   )) THEN
        COMMENT ON COLUMN threat_assessments.ip_weight IS 'IP权重因子 (基于唯一IP数量)';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_description
                   WHERE objoid = 'threat_assessments'::regclass AND objsubid = (
                       SELECT attnum FROM pg_attribute
                       WHERE attrelid = 'threat_assessments'::regclass AND attname = 'port_weight'
                   )) THEN
        COMMENT ON COLUMN threat_assessments.port_weight IS '端口权重因子 (基于唯一端口数量)';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_description
                   WHERE objoid = 'threat_assessments'::regclass AND objsubid = (
                       SELECT attnum FROM pg_attribute
                       WHERE attrelid = 'threat_assessments'::regclass AND attname = 'device_weight'
                   )) THEN
        COMMENT ON COLUMN threat_assessments.device_weight IS '设备权重因子 (基于唯一设备数量)';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_description
                   WHERE objoid = 'threat_assessments'::regclass AND objsubid = (
                       SELECT attnum FROM pg_attribute
                       WHERE attrelid = 'threat_assessments'::regclass AND attname = 'mitigation_recommendations'
                   )) THEN
        COMMENT ON COLUMN threat_assessments.mitigation_recommendations IS '缓解建议 (JSON数组格式)';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_description
                   WHERE objoid = 'threat_assessments'::regclass AND objsubid = (
                       SELECT attnum FROM pg_attribute
                       WHERE attrelid = 'threat_assessments'::regclass AND attname = 'mitigation_status'
                   )) THEN
        COMMENT ON COLUMN threat_assessments.mitigation_status IS '缓解状态: PENDING/IN_PROGRESS/COMPLETED/FAILED';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_description
                   WHERE objoid = 'threat_assessments'::regclass AND objsubid = (
                       SELECT attnum FROM pg_attribute
                       WHERE attrelid = 'threat_assessments'::regclass AND attname = 'updated_at'
                   )) THEN
        COMMENT ON COLUMN threat_assessments.updated_at IS '记录最后更新时间';
    END IF;
END $$;

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

    RAISE NOTICE '✅ threat_assessments table has % out of 8 expected fields', field_count;

    IF field_count >= 8 THEN
        RAISE NOTICE '✅ All required fields are present in threat_assessments table';
    ELSE
        RAISE WARNING '⚠️ Expected 8 fields but found %, some fields may be missing', field_count;
    END IF;
END $$;
