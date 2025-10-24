-- IP Segment Weight Configuration V2
-- 基于蜜罐机制的多租户网段权重系统
-- 支持客户自定义网段和方向性权重
-- 
-- ⚠️ 重要：此脚本仅在初始化时执行一次
-- 后续升级请使用独立的ALTER TABLE迁移脚本

-- ============================================
-- 表结构检查：仅在表不存在时创建
-- ============================================

-- 检查表是否已存在
DO $$ 
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables 
               WHERE table_name = 'ip_segment_weight_config') THEN
        RAISE NOTICE '⚠️ Table ip_segment_weight_config already exists, skipping creation';
        RAISE NOTICE '💡 To upgrade existing table, use migration script: 10-ip-segment-weights-v2-migration.sql';
    ELSE
        RAISE NOTICE '✅ Creating new table: ip_segment_weight_config';
    END IF;
END $$;

-- 仅在表不存在时创建（保护客户数据）
CREATE TABLE IF NOT EXISTS ip_segment_weight_config (
    id SERIAL PRIMARY KEY,
    
    -- 多租户支持
    customer_id VARCHAR(50) NOT NULL,
    
    -- 网段基本信息
    segment_name VARCHAR(255) NOT NULL,
    ip_range_start VARCHAR(15) NOT NULL,
    ip_range_end VARCHAR(15) NOT NULL,
    
    -- 网段分类（基于企业网络拓扑）
    zone_type VARCHAR(50) NOT NULL,  -- OFFICE, SERVER, DMZ, MANAGEMENT, IOT, GUEST, PRODUCTION
    zone_level VARCHAR(20) NOT NULL, -- LOW, MEDIUM, HIGH, CRITICAL
    
    -- 方向性权重（核心创新）
    weight_as_source DECIMAL(3,2) NOT NULL DEFAULT 1.00,  -- 作为攻击源的权重
    weight_as_target DECIMAL(3,2) NOT NULL DEFAULT 1.00,  -- 作为攻击目标的权重
    
    -- 横向移动场景权重
    weight_lateral_same_zone DECIMAL(3,2) DEFAULT 1.00,   -- 同区域内横向移动
    weight_lateral_cross_zone DECIMAL(3,2) DEFAULT 1.50,  -- 跨区域横向移动
    weight_escalation DECIMAL(3,2) DEFAULT 2.00,          -- 权限提升（低→高）
    weight_exfiltration DECIMAL(3,2) DEFAULT 1.80,        -- 数据外泄（高→低）
    
    -- 元数据
    description TEXT,
    is_honeypot BOOLEAN DEFAULT FALSE,  -- 标记蜜罐网段
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 50,
    
    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 唯一约束：同一客户下网段名称唯一
    CONSTRAINT uk_customer_segment UNIQUE (customer_id, segment_name)
);

-- 索引优化
CREATE INDEX idx_customer_zone ON ip_segment_weight_config(customer_id, zone_type);
CREATE INDEX idx_ip_range_customer ON ip_segment_weight_config(customer_id, ip_range_start, ip_range_end);
CREATE INDEX idx_zone_level ON ip_segment_weight_config(zone_level);
CREATE INDEX idx_active ON ip_segment_weight_config(is_active) WHERE is_active = TRUE;

-- ============================================
-- 默认参考配置（客户可以基于此定制）
-- ⚠️ 仅在表为空时插入，保护已有配置
-- ============================================

DO $$ 
BEGIN
    -- 仅当表为空时才插入默认配置
    IF (SELECT COUNT(*) FROM ip_segment_weight_config) = 0 THEN
        
        -- 1. 办公网段 (OFFICE Zone)
        INSERT INTO ip_segment_weight_config (
            customer_id, segment_name, ip_range_start, ip_range_end,
            zone_type, zone_level,
            weight_as_source, weight_as_target,
            weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
            description, priority
        ) VALUES
            -- 普通办公区（低风险）
            ('default', 'Office-General-192.168.10.0/24', '192.168.10.0', '192.168.10.255',
             'OFFICE', 'LOW',
             0.80, 1.00,
             0.90, 1.30, 1.50, 1.20,
             '普通办公区 - 日常办公人员网段，被诱捕概率较低', 60),
            
            -- 高管办公区（中风险）
            ('default', 'Office-Executive-192.168.11.0/24', '192.168.11.0', '192.168.11.255',
             'OFFICE', 'MEDIUM',
             1.20, 1.40,
             1.00, 1.60, 2.00, 1.80,
             '高管办公区 - 高价值目标，攻陷后危害大', 70),
            
            -- 访客网络（极低风险）
            ('default', 'Office-Guest-192.168.20.0/24', '192.168.20.0', '192.168.20.255',
             'GUEST', 'LOW',
             0.50, 0.60,
             0.60, 0.80, 1.00, 0.70,
             '访客网络 - 物理隔离，攻击价值低', 40);

        -- 2. 服务器网段 (SERVER Zone)
        INSERT INTO ip_segment_weight_config (
            customer_id, segment_name, ip_range_start, ip_range_end,
            zone_type, zone_level,
            weight_as_source, weight_as_target,
            weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
            description, priority
        ) VALUES
            -- 应用服务器（高风险）
            ('default', 'Server-Application-10.0.1.0/24', '10.0.1.0', '10.0.1.255',
             'SERVER', 'HIGH',
             1.80, 1.90,
             1.50, 2.00, 2.50, 2.30,
             '应用服务器区 - 核心业务系统，横向移动高危', 90),
            
            -- 数据库服务器（关键风险）
            ('default', 'Server-Database-10.0.2.0/24', '10.0.2.0', '10.0.2.255',
             'SERVER', 'CRITICAL',
             2.00, 2.00,
             1.80, 2.50, 3.00, 2.80,
             '数据库服务器区 - 数据核心，任何异常都是严重威胁', 100),
            
            -- Web服务器（中高风险）
            ('default', 'Server-Web-10.0.3.0/24', '10.0.3.0', '10.0.3.255',
             'SERVER', 'MEDIUM',
             1.50, 1.60,
             1.30, 1.80, 2.00, 1.70,
             'Web服务器区 - 对外暴露，易被攻陷后作为跳板', 85);

        -- 3. DMZ区域 (DMZ Zone)
        INSERT INTO ip_segment_weight_config (
            customer_id, segment_name, ip_range_start, ip_range_end,
            zone_type, zone_level,
            weight_as_source, weight_as_target,
            weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
            description, priority
        ) VALUES
            ('default', 'DMZ-Public-172.16.1.0/24', '172.16.1.0', '172.16.1.255',
             'DMZ', 'MEDIUM',
             1.40, 1.20,
             1.20, 2.00, 2.20, 1.60,
             'DMZ区域 - 半信任区，横向到内网是严重威胁', 80);

        -- 4. 管理网段 (MANAGEMENT Zone)
        INSERT INTO ip_segment_weight_config (
            customer_id, segment_name, ip_range_start, ip_range_end,
            zone_type, zone_level,
            weight_as_source, weight_as_target,
            weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
            description, priority
        ) VALUES
            ('default', 'Management-Network-10.0.100.0/24', '10.0.100.0', '10.0.100.255',
             'MANAGEMENT', 'CRITICAL',
             2.00, 2.00,
             1.80, 2.50, 3.00, 2.50,
             '管理网段 - 运维跳板机/堡垒机，攻陷后可控制全网', 100);

        -- 5. 生产网段 (PRODUCTION Zone)
        INSERT INTO ip_segment_weight_config (
            customer_id, segment_name, ip_range_start, ip_range_end,
            zone_type, zone_level,
            weight_as_source, weight_as_target,
            weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
            description, priority
        ) VALUES
            ('default', 'Production-OT-10.10.0.0/16', '10.10.0.0', '10.10.255.255',
             'PRODUCTION', 'CRITICAL',
             2.00, 2.00,
             1.80, 2.50, 3.00, 2.80,
             '生产网络 - 工控/OT系统，安全事故影响实体生产', 100);

        -- 6. IoT设备网段 (IOT Zone)
        INSERT INTO ip_segment_weight_config (
            customer_id, segment_name, ip_range_start, ip_range_end,
            zone_type, zone_level,
            weight_as_source, weight_as_target,
            weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
            description, priority
        ) VALUES
            ('default', 'IoT-Devices-192.168.50.0/24', '192.168.50.0', '192.168.50.255',
             'IOT', 'LOW',
             1.20, 0.80,
             1.00, 1.50, 1.70, 1.20,
             'IoT设备网段 - 摄像头/打印机等，易被劫持但本身价值低', 50);

        -- 7. 蜜罐网段 (HONEYPOT)
        INSERT INTO ip_segment_weight_config (
            customer_id, segment_name, ip_range_start, ip_range_end,
            zone_type, zone_level,
            weight_as_source, weight_as_target,
            weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
            description, is_honeypot, priority
        ) VALUES
            ('default', 'Honeypot-Fake-Finance-10.0.99.0/24', '10.0.99.0', '10.0.99.255',
             'SERVER', 'CRITICAL',
             0.00, 3.00,
             0.00, 3.00, 3.00, 3.00,
             '蜜罐网段 - 虚假财务服务器，任何访问都是确认的恶意行为', TRUE, 100);

        -- 8. 客户自定义示例 (供参考)
        INSERT INTO ip_segment_weight_config (
            customer_id, segment_name, ip_range_start, ip_range_end,
            zone_type, zone_level,
            weight_as_source, weight_as_target,
            weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
            description, priority
        ) VALUES
            ('customer-A', 'Office-Beijing-192.168.100.0/24', '192.168.100.0', '192.168.100.255',
             'OFFICE', 'LOW',
             0.80, 1.00, 0.90, 1.30, 1.50, 1.20,
             '北京办公室 - 客户A定制配置', 60),
            
            ('customer-A', 'Server-Core-10.100.1.0/24', '10.100.1.0', '10.100.1.255',
             'SERVER', 'CRITICAL',
             2.00, 2.00, 1.80, 2.50, 3.00, 2.80,
             '核心服务器 - 客户A定制配置', 100);
        
        RAISE NOTICE '✅ Inserted default IP segment weight configurations';
    ELSE
        RAISE NOTICE '⚠️ Table already contains data, skipping default configuration insertion';
        RAISE NOTICE '💡 Customer configurations are preserved';
    END IF;
END $$;

-- 2. 服务器网段 (SERVER Zone)
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    zone_type, zone_level,
    weight_as_source, weight_as_target,
    weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
    description, priority
) VALUES
    -- 应用服务器（高风险）
    ('default', 'Server-Application-10.0.1.0/24', '10.0.1.0', '10.0.1.255',
     'SERVER', 'HIGH',
     1.80, 1.90,  -- 作为源：极高权重（核心资产被攻陷），作为目标：极高
     1.50, 2.00, 2.50, 2.30,  -- 服务器横向移动危害极大
     '应用服务器区 - 核心业务系统，横向移动高危', 90),
    
    -- 数据库服务器（关键风险）
    ('default', 'Server-Database-10.0.2.0/24', '10.0.2.0', '10.0.2.255',
     'SERVER', 'CRITICAL',
     2.00, 2.00,  -- 作为源和目标都是最高权重（数据核心）
     1.80, 2.50, 3.00, 2.80,  -- 数据库横向移动/提权/外泄都是最高危
     '数据库服务器区 - 数据核心，任何异常都是严重威胁', 100),
    
    -- Web服务器（中高风险）
    ('default', 'Server-Web-10.0.3.0/24', '10.0.3.0', '10.0.3.255',
     'SERVER', 'MEDIUM',
     1.50, 1.60,  -- 对外服务，被攻陷风险较高
     1.30, 1.80, 2.00, 1.70,  -- 从Web服务器横向到内网危害大
     'Web服务器区 - 对外暴露，易被攻陷后作为跳板', 85);

-- 3. DMZ区域 (DMZ Zone)
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    zone_type, zone_level,
    weight_as_source, weight_as_target,
    weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
    description, priority
) VALUES
    ('default', 'DMZ-Public-172.16.1.0/24', '172.16.1.0', '172.16.1.255',
     'DMZ', 'MEDIUM',
     1.40, 1.20,  -- 作为源：高（已在DMZ被攻陷），作为目标：中（本就暴露）
     1.20, 2.00, 2.20, 1.60,  -- 从DMZ横向到内网危害极大
     'DMZ区域 - 半信任区，横向到内网是严重威胁', 80);

-- 4. 管理网段 (MANAGEMENT Zone)
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    zone_type, zone_level,
    weight_as_source, weight_as_target,
    weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
    description, priority
) VALUES
    ('default', 'Management-Network-10.0.100.0/24', '10.0.100.0', '10.0.100.255',
     'MANAGEMENT', 'CRITICAL',
     2.00, 2.00,  -- 管理网段最高权重
     1.80, 2.50, 3.00, 2.50,  -- 管理网段的任何异常都是最高危
     '管理网段 - 运维跳板机/堡垒机，攻陷后可控制全网', 100);

-- 5. 生产网段 (PRODUCTION Zone)
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    zone_type, zone_level,
    weight_as_source, weight_as_target,
    weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
    description, priority
) VALUES
    ('default', 'Production-OT-10.10.0.0/16', '10.10.0.0', '10.10.255.255',
     'PRODUCTION', 'CRITICAL',
     2.00, 2.00,  -- 生产网络最高权重（工控/OT）
     1.80, 2.50, 3.00, 2.80,  -- 生产网络横向移动可能导致物理灾难
     '生产网络 - 工控/OT系统，安全事故影响实体生产', 100);

-- 6. IoT设备网段 (IOT Zone)
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    zone_type, zone_level,
    weight_as_source, weight_as_target,
    weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
    description, priority
) VALUES
    ('default', 'IoT-Devices-192.168.50.0/24', '192.168.50.0', '192.168.50.255',
     'IOT', 'LOW',
     1.20, 0.80,  -- 作为源：中（易被劫持作为跳板），作为目标：低
     1.00, 1.50, 1.70, 1.20,  -- IoT设备横向到办公/服务器危害较大
     'IoT设备网段 - 摄像头/打印机等，易被劫持但本身价值低', 50);

-- ============================================
-- 蜜罐网段标记（这些是诱饵，访问即告警）
-- ============================================

INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    zone_type, zone_level,
    weight_as_source, weight_as_target,
    weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
    description, is_honeypot, priority
) VALUES
    ('default', 'Honeypot-Fake-Finance-10.0.99.0/24', '10.0.99.0', '10.0.99.255',
     'SERVER', 'CRITICAL',
     0.00, 3.00,  -- 作为源：不可能（是诱饵），作为目标：最高（访问即恶意）
     0.00, 3.00, 3.00, 3.00,  -- 任何对蜜罐的访问都是最高危
     '蜜罐网段 - 虚假财务服务器，任何访问都是确认的恶意行为', TRUE, 100);

-- ============================================
-- 创建方向性权重计算函数
-- ============================================

CREATE OR REPLACE FUNCTION calculate_directional_weight(
    p_customer_id VARCHAR(50),
    p_source_ip VARCHAR(15),
    p_target_ip VARCHAR(15)
) RETURNS DECIMAL(3,2) AS $$
DECLARE
    v_source_zone VARCHAR(50);
    v_target_zone VARCHAR(50);
    v_source_level VARCHAR(20);
    v_target_level VARCHAR(20);
    v_base_weight DECIMAL(3,2) := 1.00;
    v_final_weight DECIMAL(3,2);
BEGIN
    -- 获取源IP的区域信息
    SELECT zone_type, zone_level INTO v_source_zone, v_source_level
    FROM ip_segment_weight_config
    WHERE customer_id = p_customer_id
      AND is_active = TRUE
      AND CAST(p_source_ip AS inet) BETWEEN CAST(ip_range_start AS inet) AND CAST(ip_range_end AS inet)
    ORDER BY priority DESC LIMIT 1;
    
    -- 获取目标IP的区域信息
    SELECT zone_type, zone_level INTO v_target_zone, v_target_level
    FROM ip_segment_weight_config
    WHERE customer_id = p_customer_id
      AND is_active = TRUE
      AND CAST(p_target_ip AS inet) BETWEEN CAST(ip_range_start AS inet) AND CAST(ip_range_end AS inet)
    ORDER BY priority DESC LIMIT 1;
    
    -- 场景1: 蜜罐被访问（最高优先级）
    IF EXISTS (
        SELECT 1 FROM ip_segment_weight_config
        WHERE customer_id = p_customer_id
          AND is_honeypot = TRUE
          AND CAST(p_target_ip AS inet) BETWEEN CAST(ip_range_start AS inet) AND CAST(ip_range_end AS inet)
    ) THEN
        RETURN 3.00;
    END IF;
    
    -- 场景2: 同区域横向移动
    IF v_source_zone = v_target_zone THEN
        SELECT weight_lateral_same_zone INTO v_final_weight
        FROM ip_segment_weight_config
        WHERE customer_id = p_customer_id
          AND zone_type = v_source_zone
        ORDER BY priority DESC LIMIT 1;
        RETURN COALESCE(v_final_weight, 1.00);
    END IF;
    
    -- 场景3: 权限提升（低→高）
    IF (v_source_level IN ('LOW', 'MEDIUM') AND v_target_level IN ('HIGH', 'CRITICAL')) THEN
        SELECT weight_escalation INTO v_final_weight
        FROM ip_segment_weight_config
        WHERE customer_id = p_customer_id
          AND zone_type = v_target_zone
        ORDER BY priority DESC LIMIT 1;
        RETURN COALESCE(v_final_weight, 2.00);
    END IF;
    
    -- 场景4: 数据外泄（高→低）
    IF (v_source_level IN ('HIGH', 'CRITICAL') AND v_target_level IN ('LOW', 'MEDIUM')) THEN
        SELECT weight_exfiltration INTO v_final_weight
        FROM ip_segment_weight_config
        WHERE customer_id = p_customer_id
          AND zone_type = v_source_zone
        ORDER BY priority DESC LIMIT 1;
        RETURN COALESCE(v_final_weight, 1.80);
    END IF;
    
    -- 场景5: 跨区域横向移动（默认）
    SELECT weight_lateral_cross_zone INTO v_final_weight
    FROM ip_segment_weight_config
    WHERE customer_id = p_customer_id
      AND zone_type = v_source_zone
    ORDER BY priority DESC LIMIT 1;
    
    RETURN COALESCE(v_final_weight, 1.50);
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- 使用示例和测试查询
-- ============================================

-- 示例1: 查询办公区横向到数据库服务器的权重
-- SELECT calculate_directional_weight('default', '192.168.10.50', '10.0.2.100');
-- 预期结果: 2.50 (跨区域提权)

-- 示例2: 查询同办公区内横向移动的权重
-- SELECT calculate_directional_weight('default', '192.168.10.50', '192.168.10.100');
-- 预期结果: 0.90 (同区域横向)

-- 示例3: 查询访问蜜罐的权重
-- SELECT calculate_directional_weight('default', '192.168.10.50', '10.0.99.50');
-- 预期结果: 3.00 (蜜罐访问)

-- ============================================
-- 客户自定义配置示例
-- ============================================

-- 客户A的定制配置
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    zone_type, zone_level,
    weight_as_source, weight_as_target,
    weight_lateral_same_zone, weight_lateral_cross_zone, weight_escalation, weight_exfiltration,
    description, priority
) VALUES
    ('customer-A', 'Office-Beijing-192.168.100.0/24', '192.168.100.0', '192.168.100.255',
     'OFFICE', 'LOW',
     0.80, 1.00, 0.90, 1.30, 1.50, 1.20,
     '北京办公室 - 客户A定制配置', 60),
    
    ('customer-A', 'Server-Core-10.100.1.0/24', '10.100.1.0', '10.100.1.255',
     'SERVER', 'CRITICAL',
     2.00, 2.00, 1.80, 2.50, 3.00, 2.80,
     '核心服务器 - 客户A定制配置', 100);

COMMENT ON TABLE ip_segment_weight_config IS '基于蜜罐机制的多租户网段权重配置表 - 支持方向性权重和横向移动场景分析';
COMMENT ON COLUMN ip_segment_weight_config.weight_as_source IS '作为攻击源的权重倍数';
COMMENT ON COLUMN ip_segment_weight_config.weight_as_target IS '作为攻击目标（蜜罐）的权重倍数';
COMMENT ON COLUMN ip_segment_weight_config.weight_lateral_same_zone IS '同区域内横向移动的权重倍数';
COMMENT ON COLUMN ip_segment_weight_config.weight_lateral_cross_zone IS '跨区域横向移动的权重倍数';
COMMENT ON COLUMN ip_segment_weight_config.weight_escalation IS '权限提升场景（低→高）的权重倍数';
COMMENT ON COLUMN ip_segment_weight_config.weight_exfiltration IS '数据外泄场景（高→低）的权重倍数';
COMMENT ON COLUMN ip_segment_weight_config.is_honeypot IS '是否为蜜罐网段（访问即告警）';
