-- =====================================================
-- APT Temporal Accumulations Table
-- 用于存储APT攻击的长期累积数据和指数衰减评分
-- 创建时间: 2025-10-30
-- =====================================================

-- APT时序累积表 (APT Temporal Accumulations)
-- 存储30-90天内的攻击者行为累积数据，支持指数衰减计算
CREATE TABLE IF NOT EXISTS apt_temporal_accumulations (
    id BIGSERIAL PRIMARY KEY,

    -- 租户和攻击者标识
    customer_id VARCHAR(100) NOT NULL,
    attack_mac VARCHAR(17) NOT NULL,
    attack_ip VARCHAR(45),  -- 可选，用于关联攻击源

    -- 累积统计数据 (30-90天窗口)
    total_attack_count BIGINT NOT NULL DEFAULT 0,        -- 总攻击次数
    unique_ips_count INTEGER NOT NULL DEFAULT 0,         -- 唯一诱饵IP数量
    unique_ports_count INTEGER NOT NULL DEFAULT 0,       -- 唯一端口数量
    unique_devices_count INTEGER NOT NULL DEFAULT 0,     -- 唯一设备数量

    -- 指数衰减累积评分
    decay_accumulated_score DECIMAL(12,4) NOT NULL DEFAULT 0.0,  -- 衰减累积威胁评分
    half_life_days INTEGER NOT NULL DEFAULT 30,          -- 半衰期天数 (默认30天)

    -- 攻击阶段推断
    inferred_attack_phase VARCHAR(30),                   -- 推断的攻击阶段: RECON/EXPLOITATION/PERSISTENCE/UNKNOWN
    phase_confidence DECIMAL(3,2),                       -- 阶段推断置信度 (0.0-1.0)

    -- 时间窗口信息
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,      -- 累积窗口开始时间
    window_end TIMESTAMP WITH TIME ZONE NOT NULL,        -- 累积窗口结束时间
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL,      -- 最后更新时间

    -- 性能优化字段
    cache_key VARCHAR(255),                              -- Redis缓存键
    cache_expiry TIMESTAMP WITH TIME ZONE,               -- 缓存过期时间

    -- 元数据
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- 约束
    CONSTRAINT chk_attack_phase CHECK (inferred_attack_phase IN ('RECON', 'EXPLOITATION', 'PERSISTENCE', 'UNKNOWN')),
    CONSTRAINT chk_phase_confidence CHECK (phase_confidence >= 0.0 AND phase_confidence <= 1.0),
    CONSTRAINT chk_half_life CHECK (half_life_days > 0),
    CONSTRAINT chk_window_order CHECK (window_start <= window_end),
    CONSTRAINT uk_apt_temporal_customer_mac UNIQUE (customer_id, attack_mac)
);

-- 创建索引以优化查询性能
CREATE INDEX IF NOT EXISTS idx_apt_temporal_customer_mac ON apt_temporal_accumulations(customer_id, attack_mac);
CREATE INDEX IF NOT EXISTS idx_apt_temporal_attack_ip ON apt_temporal_accumulations(attack_ip) WHERE attack_ip IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_apt_temporal_window ON apt_temporal_accumulations(window_start, window_end);
CREATE INDEX IF NOT EXISTS idx_apt_temporal_phase ON apt_temporal_accumulations(inferred_attack_phase);
CREATE INDEX IF NOT EXISTS idx_apt_temporal_updated ON apt_temporal_accumulations(last_updated DESC);
CREATE INDEX IF NOT EXISTS idx_apt_temporal_cache ON apt_temporal_accumulations(cache_key) WHERE cache_key IS NOT NULL;

-- 创建复合索引用于常见查询模式
CREATE INDEX IF NOT EXISTS idx_apt_temporal_customer_phase_time ON apt_temporal_accumulations(customer_id, inferred_attack_phase, last_updated DESC);
CREATE INDEX IF NOT EXISTS idx_apt_temporal_score_threshold ON apt_temporal_accumulations(decay_accumulated_score DESC) WHERE decay_accumulated_score > 0;

-- 创建分区表支持 (按月份分区以优化历史数据查询)
-- 注意: PostgreSQL分区需要单独的DDL语句，这里先创建基础表

-- 授予权限
GRANT SELECT, INSERT, UPDATE, DELETE ON apt_temporal_accumulations TO threat_user;
GRANT USAGE, SELECT ON SEQUENCE apt_temporal_accumulations_id_seq TO threat_user;

-- 添加表注释
COMMENT ON TABLE apt_temporal_accumulations IS 'APT时序累积表 - 存储长期攻击行为累积数据，支持指数衰减威胁评分';
COMMENT ON COLUMN apt_temporal_accumulations.customer_id IS '客户ID (多租户隔离)';
COMMENT ON COLUMN apt_temporal_accumulations.attack_mac IS '被诱捕者MAC地址 (攻击者标识)';
COMMENT ON COLUMN apt_temporal_accumulations.attack_ip IS '被诱捕者IP地址 (可选，用于关联)';
COMMENT ON COLUMN apt_temporal_accumulations.decay_accumulated_score IS '指数衰减累积威胁评分 (考虑时间衰减)';
COMMENT ON COLUMN apt_temporal_accumulations.half_life_days IS '半衰期天数 (默认30天，用于指数衰减计算)';
COMMENT ON COLUMN apt_temporal_accumulations.inferred_attack_phase IS '推断的攻击阶段: RECON/EXPLOITATION/PERSISTENCE/UNKNOWN';
COMMENT ON COLUMN apt_temporal_accumulations.phase_confidence IS '阶段推断置信度 (0.0-1.0)';
COMMENT ON COLUMN apt_temporal_accumulations.window_start IS '累积时间窗口开始时间';
COMMENT ON COLUMN apt_temporal_accumulations.window_end IS '累积时间窗口结束时间';
COMMENT ON COLUMN apt_temporal_accumulations.cache_key IS 'Redis缓存键 (用于性能优化)';

-- 创建更新触发器
CREATE OR REPLACE FUNCTION update_apt_temporal_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    NEW.last_updated = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_apt_temporal_updated_at
    BEFORE UPDATE ON apt_temporal_accumulations
    FOR EACH ROW EXECUTE FUNCTION update_apt_temporal_updated_at();

-- 数据完整性检查
DO $$
BEGIN
    RAISE NOTICE 'APT Temporal Accumulations Table Created Successfully';
    RAISE NOTICE 'Table: apt_temporal_accumulations';
    RAISE NOTICE 'Indexes: 7 indexes total';
    RAISE NOTICE 'Triggers: 1 update trigger';
END $$;