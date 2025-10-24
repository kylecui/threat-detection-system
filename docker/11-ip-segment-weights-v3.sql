-- IP网段权重配置表 V3.0 - 基于蜜罐机制的正确理解
-- 创建日期: 2025-10-24
-- 核心改进: 删除目标网段和方向性权重，只保留攻击源网段权重

-- =============================================================================
-- 表结构：ip_segment_weight_config
-- 用途：存储攻击源IP网段的风险权重配置（多租户）
-- 核心理解：所有response_ip都是诱饵，只需评估attack_ip的来源风险
-- =============================================================================

CREATE TABLE IF NOT EXISTS ip_segment_weight_config (
    id SERIAL PRIMARY KEY,
    
    -- 多租户支持
    customer_id VARCHAR(50) NOT NULL,
    
    -- 网段基本信息
    segment_name VARCHAR(255) NOT NULL,
    ip_range_start VARCHAR(15) NOT NULL,
    ip_range_end VARCHAR(15) NOT NULL,
    
    -- 攻击源风险评估（简化设计）
    segment_type VARCHAR(50) NOT NULL,      -- OFFICE, SERVER, DMZ, MANAGEMENT, IOT, GUEST
    risk_level VARCHAR(20) NOT NULL,        -- VERY_LOW, LOW, MEDIUM, HIGH, CRITICAL
    weight DECIMAL(3,2) NOT NULL DEFAULT 1.00,  -- 统一权重（1个值）
    
    -- 元数据
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 50,
    
    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 唯一约束
    CONSTRAINT uk_customer_segment UNIQUE (customer_id, segment_name)
);

-- 索引优化
CREATE INDEX IF NOT EXISTS idx_ip_segment_customer_active 
    ON ip_segment_weight_config(customer_id, is_active) 
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_ip_segment_range 
    ON ip_segment_weight_config(customer_id, ip_range_start, ip_range_end);

CREATE INDEX IF NOT EXISTS idx_ip_segment_risk_level 
    ON ip_segment_weight_config(risk_level);

-- 表和字段注释
COMMENT ON TABLE ip_segment_weight_config IS '蜜罐系统攻击源网段权重配置 - 仅评估attack_ip来源风险（V3.0）';
COMMENT ON COLUMN ip_segment_weight_config.customer_id IS '客户ID（多租户隔离）';
COMMENT ON COLUMN ip_segment_weight_config.segment_name IS '网段名称（客户自定义标识）';
COMMENT ON COLUMN ip_segment_weight_config.ip_range_start IS 'IP范围起始地址';
COMMENT ON COLUMN ip_segment_weight_config.ip_range_end IS 'IP范围结束地址';
COMMENT ON COLUMN ip_segment_weight_config.segment_type IS '网段类型：OFFICE(办公)、SERVER(服务器)、DMZ(半信任区)、MANAGEMENT(管理)、IOT(物联网)、GUEST(访客)';
COMMENT ON COLUMN ip_segment_weight_config.risk_level IS '风险等级：VERY_LOW、LOW、MEDIUM、HIGH、CRITICAL';
COMMENT ON COLUMN ip_segment_weight_config.weight IS '攻击源权重倍数（基于网段被攻陷的风险和危害），范围0.5-3.0';
COMMENT ON COLUMN ip_segment_weight_config.description IS '网段描述信息';
COMMENT ON COLUMN ip_segment_weight_config.is_active IS '是否启用（软删除标记）';
COMMENT ON COLUMN ip_segment_weight_config.priority IS '优先级（用于IP范围重叠时的匹配顺序，数值越大优先级越高）';

-- =============================================================================
-- 默认配置数据
-- 用途：为default客户提供标准网段权重配置模板
-- 说明：客户可以基于此模板创建自己的配置
-- =============================================================================

-- 只在表为空时插入默认数据，避免覆盖客户配置
INSERT INTO ip_segment_weight_config (
    customer_id, segment_name, ip_range_start, ip_range_end,
    segment_type, risk_level, weight,
    description, priority
)
SELECT 
    'default', 'Office-General-192.168.10.0/24', '192.168.10.0', '192.168.10.255',
    'OFFICE', 'LOW', 1.00,
    '普通办公区 - 日常办公人员，可能被钓鱼邮件攻陷，但危害可控', 50
WHERE NOT EXISTS (
    SELECT 1 FROM ip_segment_weight_config 
    WHERE customer_id = 'default' AND segment_name = 'Office-General-192.168.10.0/24'
)

UNION ALL

SELECT 
    'default', 'Office-Executive-192.168.20.0/24', '192.168.20.0', '192.168.20.255',
    'OFFICE', 'HIGH', 1.80,
    '高管/财务办公区 - APT高价值目标，攻陷后危害大，可能导致社工攻击升级', 70
WHERE NOT EXISTS (
    SELECT 1 FROM ip_segment_weight_config 
    WHERE customer_id = 'default' AND segment_name = 'Office-Executive-192.168.20.0/24'
)

UNION ALL

SELECT 
    'default', 'Office-Dev-192.168.30.0/24', '192.168.30.0', '192.168.30.255',
    'OFFICE', 'MEDIUM', 1.20,
    '开发人员办公区 - 有代码和系统访问权限，攻陷后可能导致供应链攻击', 60
WHERE NOT EXISTS (
    SELECT 1 FROM ip_segment_weight_config 
    WHERE customer_id = 'default' AND segment_name = 'Office-Dev-192.168.30.0/24'
)

UNION ALL

SELECT 
    'default', 'Office-Guest-192.168.100.0/24', '192.168.100.0', '192.168.100.255',
    'GUEST', 'VERY_LOW', 0.60,
    '访客网络 - 物理隔离，无法访问内网资源，危害有限', 30
WHERE NOT EXISTS (
    SELECT 1 FROM ip_segment_weight_config 
    WHERE customer_id = 'default' AND segment_name = 'Office-Guest-192.168.100.0/24'
)

UNION ALL

SELECT 
    'default', 'Server-Web-10.0.1.0/24', '10.0.1.0', '10.0.1.255',
    'SERVER', 'HIGH', 2.00,
    'Web服务器区 - 对外暴露服务，易被攻破后作为跳板进入内网', 80
WHERE NOT EXISTS (
    SELECT 1 FROM ip_segment_weight_config 
    WHERE customer_id = 'default' AND segment_name = 'Server-Web-10.0.1.0/24'
)

UNION ALL

SELECT 
    'default', 'Server-App-10.0.2.0/24', '10.0.2.0', '10.0.2.255',
    'SERVER', 'CRITICAL', 2.50,
    '应用服务器区 - 核心业务系统，攻陷后果严重，影响业务连续性', 90
WHERE NOT EXISTS (
    SELECT 1 FROM ip_segment_weight_config 
    WHERE customer_id = 'default' AND segment_name = 'Server-App-10.0.2.0/24'
)

UNION ALL

SELECT 
    'default', 'Server-Database-10.0.3.0/24', '10.0.3.0', '10.0.3.255',
    'SERVER', 'CRITICAL', 3.00,
    '数据库服务器区 - 数据核心，被攻陷意味着数据泄露或勒索软件加密', 100
WHERE NOT EXISTS (
    SELECT 1 FROM ip_segment_weight_config 
    WHERE customer_id = 'default' AND segment_name = 'Server-Database-10.0.3.0/24'
)

UNION ALL

SELECT 
    'default', 'Server-FileShare-10.0.4.0/24', '10.0.4.0', '10.0.4.255',
    'SERVER', 'HIGH', 2.20,
    '文件服务器区 - 存储大量文件，是勒索软件的主要攻击目标', 85
WHERE NOT EXISTS (
    SELECT 1 FROM ip_segment_weight_config 
    WHERE customer_id = 'default' AND segment_name = 'Server-FileShare-10.0.4.0/24'
)

UNION ALL

SELECT 
    'default', 'Management-OPS-10.0.100.0/24', '10.0.100.0', '10.0.100.255',
    'MANAGEMENT', 'CRITICAL', 3.00,
    '运维管理网段 - 跳板机/堡垒机，攻陷后可控制全网所有资产', 100
WHERE NOT EXISTS (
    SELECT 1 FROM ip_segment_weight_config 
    WHERE customer_id = 'default' AND segment_name = 'Management-OPS-10.0.100.0/24'
)

UNION ALL

SELECT 
    'default', 'DMZ-Public-172.16.1.0/24', '172.16.1.0', '172.16.1.255',
    'DMZ', 'HIGH', 2.00,
    'DMZ区域 - 半信任区，易被攻陷后作为进入内网的跳板', 80
WHERE NOT EXISTS (
    SELECT 1 FROM ip_segment_weight_config 
    WHERE customer_id = 'default' AND segment_name = 'DMZ-Public-172.16.1.0/24'
)

UNION ALL

SELECT 
    'default', 'IoT-Devices-192.168.50.0/24', '192.168.50.0', '192.168.50.255',
    'IOT', 'LOW', 0.90,
    'IoT设备网段 - 摄像头/打印机/门禁，易被劫持但权限有限，难以横向移动', 40
WHERE NOT EXISTS (
    SELECT 1 FROM ip_segment_weight_config 
    WHERE customer_id = 'default' AND segment_name = 'IoT-Devices-192.168.50.0/24'
)

UNION ALL

SELECT 
    'default', 'IoT-Industrial-192.168.51.0/24', '192.168.51.0', '192.168.51.255',
    'IOT', 'MEDIUM', 1.30,
    '工控设备网段 - PLC/SCADA设备，攻陷后可能影响生产运营', 65
WHERE NOT EXISTS (
    SELECT 1 FROM ip_segment_weight_config 
    WHERE customer_id = 'default' AND segment_name = 'IoT-Industrial-192.168.51.0/24'
)

UNION ALL

SELECT 
    'default', 'Default-Catch-All', '0.0.0.0', '255.255.255.255',
    'OFFICE', 'MEDIUM', 1.00,
    '默认兜底规则 - 未配置网段使用基准权重', 10
WHERE NOT EXISTS (
    SELECT 1 FROM ip_segment_weight_config 
    WHERE customer_id = 'default' AND segment_name = 'Default-Catch-All'
);

-- =============================================================================
-- 查询函数：获取攻击源权重
-- 用途：根据 customer_id 和 attack_ip 查询对应的网段权重
-- 返回：权重值（默认1.00）
-- =============================================================================

CREATE OR REPLACE FUNCTION get_attack_source_weight(
    p_customer_id VARCHAR(50),
    p_attack_ip VARCHAR(15)
) RETURNS DECIMAL(3,2) AS $$
DECLARE
    v_weight DECIMAL(3,2);
BEGIN
    -- 查询匹配的网段权重（优先级从高到低）
    SELECT weight INTO v_weight
    FROM ip_segment_weight_config
    WHERE customer_id = p_customer_id
      AND is_active = TRUE
      AND CAST(p_attack_ip AS inet) BETWEEN CAST(ip_range_start AS inet) AND CAST(ip_range_end AS inet)
    ORDER BY priority DESC, id DESC
    LIMIT 1;
    
    -- 如果没有匹配到，返回默认权重1.00
    RETURN COALESCE(v_weight, 1.00);
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_attack_source_weight(VARCHAR, VARCHAR) IS '根据客户ID和攻击源IP查询网段权重（V3.0）';

-- =============================================================================
-- 测试查询
-- 用途：验证配置是否正确
-- =============================================================================

-- 查询所有默认配置（按风险等级排序）
-- SELECT segment_name, segment_type, risk_level, weight, description
-- FROM ip_segment_weight_config
-- WHERE customer_id = 'default' AND is_active = TRUE
-- ORDER BY weight DESC, segment_name;

-- 测试权重查询函数
-- SELECT get_attack_source_weight('default', '192.168.10.50');  -- 应返回 1.00 (普通办公区)
-- SELECT get_attack_source_weight('default', '192.168.20.100'); -- 应返回 1.80 (高管区)
-- SELECT get_attack_source_weight('default', '10.0.3.10');      -- 应返回 3.00 (数据库服务器)
-- SELECT get_attack_source_weight('default', '10.0.100.5');     -- 应返回 3.00 (运维管理)
-- SELECT get_attack_source_weight('default', '192.168.100.20'); -- 应返回 0.60 (访客网络)
-- SELECT get_attack_source_weight('default', '1.2.3.4');        -- 应返回 1.00 (未匹配，使用兜底规则)

-- =============================================================================
-- V2.0 → V3.0 迁移说明
-- =============================================================================

-- 如果您之前安装了 V2.0 版本（10-ip-segment-weights-v2.sql），请执行以下迁移：

-- 1. 备份现有数据
-- CREATE TABLE ip_segment_weight_config_v2_backup AS SELECT * FROM ip_segment_weight_config;

-- 2. 删除旧表
-- DROP TABLE IF EXISTS ip_segment_weight_config;

-- 3. 执行本脚本（11-ip-segment-weights-v3.sql）

-- 4. 手动迁移客户自定义配置（V2.0的复杂配置需要重新评估）
-- V2.0 的 weight_as_source/weight_as_target/weight_lateral 等字段已删除
-- 需要根据实际业务场景，将多个权重合并为单一的 weight 值

-- 迁移参考：
-- V2.0: weight_as_source=1.5, weight_as_target=1.0, weight_lateral_same_zone=0.8
-- V3.0: weight = 1.5 (只保留 attack_ip 的来源权重)

-- =============================================================================
-- 结束
-- =============================================================================
