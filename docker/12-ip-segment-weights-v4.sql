-- IP网段权重配置表 V4.0 - 双维度权重设计
-- 创建日期: 2025-10-24
-- 核心改进: 双维度权重 = 攻击源权重 × 蜜罐敏感度权重
-- 设计理念: 既评估失陷设备的危害，也评估攻击者的意图

-- =============================================================================
-- 表1: attack_source_weights - 攻击源网段权重配置
-- 用途: 评估失陷设备被攻陷的后果有多严重
-- 含义: 这个设备被攻陷后，能造成多大危害？
-- =============================================================================

CREATE TABLE IF NOT EXISTS attack_source_weights (
    id SERIAL PRIMARY KEY,
    
    -- 多租户支持
    customer_id VARCHAR(50) NOT NULL,
    
    -- 网段基本信息
    segment_name VARCHAR(255) NOT NULL,
    ip_range_start VARCHAR(15) NOT NULL,
    ip_range_end VARCHAR(15) NOT NULL,
    
    -- 攻击源风险评估
    segment_type VARCHAR(50) NOT NULL,      -- OFFICE, SERVER, DATABASE, MANAGEMENT, IOT, GUEST, DMZ
    risk_level VARCHAR(20) NOT NULL,        -- VERY_LOW, LOW, MEDIUM, HIGH, CRITICAL
    weight DECIMAL(3,2) NOT NULL DEFAULT 1.00,  -- 攻击源权重 (0.5-3.0)
    
    -- 元数据
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 50,
    
    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 唯一约束
    CONSTRAINT uk_customer_source_segment UNIQUE (customer_id, segment_name)
);

-- 索引优化
CREATE INDEX IF NOT EXISTS idx_attack_source_customer_active 
    ON attack_source_weights(customer_id, is_active) 
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_attack_source_ip_range 
    ON attack_source_weights(customer_id, ip_range_start, ip_range_end);

CREATE INDEX IF NOT EXISTS idx_attack_source_risk_level 
    ON attack_source_weights(risk_level);

-- 表和字段注释
COMMENT ON TABLE attack_source_weights IS '蜜罐系统攻击源网段权重配置 - 评估失陷设备的危害程度（V4.0）';
COMMENT ON COLUMN attack_source_weights.customer_id IS '客户ID（多租户隔离）';
COMMENT ON COLUMN attack_source_weights.segment_name IS '网段名称（客户自定义标识）';
COMMENT ON COLUMN attack_source_weights.ip_range_start IS 'IP范围起始地址';
COMMENT ON COLUMN attack_source_weights.ip_range_end IS 'IP范围结束地址';
COMMENT ON COLUMN attack_source_weights.segment_type IS '网段类型：OFFICE(办公)、SERVER(服务器)、DATABASE(数据库)、MANAGEMENT(管理)、IOT(物联网)、GUEST(访客)、DMZ(半信任区)';
COMMENT ON COLUMN attack_source_weights.risk_level IS '风险等级：VERY_LOW、LOW、MEDIUM、HIGH、CRITICAL';
COMMENT ON COLUMN attack_source_weights.weight IS '攻击源权重倍数（该设备被攻陷的后果严重程度），范围0.5-3.0';
COMMENT ON COLUMN attack_source_weights.description IS '网段描述信息';
COMMENT ON COLUMN attack_source_weights.is_active IS '是否启用（软删除标记）';
COMMENT ON COLUMN attack_source_weights.priority IS '优先级（用于IP范围重叠时的匹配顺序，数值越大优先级越高）';

-- =============================================================================
-- 表2: honeypot_sensitivity_weights - 蜜罐敏感度权重配置
-- 用途: 评估诱饵的敏感度/诱惑力，反映攻击者的意图
-- 含义: 攻击者尝试访问这个诱饵，意图有多严重？
-- =============================================================================

CREATE TABLE IF NOT EXISTS honeypot_sensitivity_weights (
    id SERIAL PRIMARY KEY,
    
    -- 多租户支持
    customer_id VARCHAR(50) NOT NULL,
    
    -- 蜜罐基本信息
    honeypot_name VARCHAR(255) NOT NULL,
    ip_range_start VARCHAR(15) NOT NULL,
    ip_range_end VARCHAR(15) NOT NULL,
    
    -- 蜜罐敏感度评估
    honeypot_tier VARCHAR(50) NOT NULL,    -- CRITICAL_ASSET, HIGH_VALUE, MEDIUM_VALUE, LOW_VALUE, DECOY
    deployment_zone VARCHAR(50) NOT NULL,  -- MANAGEMENT, DATABASE, CORE_SERVER, APP_SERVER, OFFICE, DMZ
    sensitivity_level VARCHAR(20) NOT NULL, -- CRITICAL, HIGH, MEDIUM, LOW, VERY_LOW
    weight DECIMAL(3,2) NOT NULL DEFAULT 1.00,  -- 敏感度权重 (1.0-3.5)
    
    -- 蜜罐特性
    simulated_service VARCHAR(100),        -- 模拟的服务类型 (如: SSH, RDP, Database, FileShare)
    attack_intent VARCHAR(100),            -- 反映的攻击意图 (如: 全网控制, 数据窃取, 横向移动)
    
    -- 元数据
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 50,
    
    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 唯一约束
    CONSTRAINT uk_customer_honeypot UNIQUE (customer_id, honeypot_name)
);

-- 索引优化
CREATE INDEX IF NOT EXISTS idx_honeypot_customer_active 
    ON honeypot_sensitivity_weights(customer_id, is_active) 
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_honeypot_ip_range 
    ON honeypot_sensitivity_weights(customer_id, ip_range_start, ip_range_end);

CREATE INDEX IF NOT EXISTS idx_honeypot_sensitivity_level 
    ON honeypot_sensitivity_weights(sensitivity_level);

CREATE INDEX IF NOT EXISTS idx_honeypot_deployment_zone 
    ON honeypot_sensitivity_weights(deployment_zone);

-- 表和字段注释
COMMENT ON TABLE honeypot_sensitivity_weights IS '蜜罐敏感度权重配置 - 评估诱饵的敏感度和攻击者意图（V4.0）';
COMMENT ON COLUMN honeypot_sensitivity_weights.customer_id IS '客户ID（多租户隔离）';
COMMENT ON COLUMN honeypot_sensitivity_weights.honeypot_name IS '蜜罐名称（客户自定义标识）';
COMMENT ON COLUMN honeypot_sensitivity_weights.ip_range_start IS '蜜罐IP范围起始地址';
COMMENT ON COLUMN honeypot_sensitivity_weights.ip_range_end IS '蜜罐IP范围结束地址';
COMMENT ON COLUMN honeypot_sensitivity_weights.honeypot_tier IS '蜜罐层级：CRITICAL_ASSET(关键资产诱饵)、HIGH_VALUE(高价值诱饵)、MEDIUM_VALUE(中等诱饵)、LOW_VALUE(低价值诱饵)、DECOY(普通诱饵)';
COMMENT ON COLUMN honeypot_sensitivity_weights.deployment_zone IS '部署区域：MANAGEMENT(管理区)、DATABASE(数据库区)、CORE_SERVER(核心服务器)、APP_SERVER(应用服务器)、OFFICE(办公区)、DMZ(半信任区)';
COMMENT ON COLUMN honeypot_sensitivity_weights.sensitivity_level IS '敏感度等级：CRITICAL、HIGH、MEDIUM、LOW、VERY_LOW';
COMMENT ON COLUMN honeypot_sensitivity_weights.weight IS '敏感度权重倍数（攻击者意图的严重程度），范围1.0-3.5';
COMMENT ON COLUMN honeypot_sensitivity_weights.simulated_service IS '蜜罐模拟的服务类型（如SSH跳板机、数据库、文件共享）';
COMMENT ON COLUMN honeypot_sensitivity_weights.attack_intent IS '访问该蜜罐反映的攻击意图（如全网控制、数据窃取、横向移动）';
COMMENT ON COLUMN honeypot_sensitivity_weights.description IS '蜜罐描述信息';
COMMENT ON COLUMN honeypot_sensitivity_weights.is_active IS '是否启用（软删除标记）';
COMMENT ON COLUMN honeypot_sensitivity_weights.priority IS '优先级（用于IP范围重叠时的匹配顺序，数值越大优先级越高）';

-- =============================================================================
-- 默认配置数据 - 攻击源权重
-- 用途: 为default客户提供标准攻击源权重配置模板
-- =============================================================================

INSERT INTO attack_source_weights (
    customer_id, segment_name, ip_range_start, ip_range_end,
    segment_type, risk_level, weight,
    description, priority
)
SELECT 
    'default', 'Database-Servers-10.0.3.0/24', '10.0.3.0', '10.0.3.255',
    'DATABASE', 'CRITICAL', 3.00,
    '数据库服务器区 - 被攻陷后可直接访问数据，影响极严重', 100
WHERE NOT EXISTS (
    SELECT 1 FROM attack_source_weights 
    WHERE customer_id = 'default' AND segment_name = 'Database-Servers-10.0.3.0/24'
)

UNION ALL

SELECT 
    'default', 'Management-OPS-10.0.100.0/24', '10.0.100.0', '10.0.100.255',
    'MANAGEMENT', 'CRITICAL', 3.00,
    '运维管理网段 - 跳板机/堡垒机，被攻陷后可控制全网所有资产', 100
WHERE NOT EXISTS (
    SELECT 1 FROM attack_source_weights 
    WHERE customer_id = 'default' AND segment_name = 'Management-OPS-10.0.100.0/24'
)

UNION ALL

SELECT 
    'default', 'Server-App-10.0.2.0/24', '10.0.2.0', '10.0.2.255',
    'SERVER', 'CRITICAL', 2.50,
    '应用服务器区 - 核心业务系统，被攻陷后影响业务连续性', 90
WHERE NOT EXISTS (
    SELECT 1 FROM attack_source_weights 
    WHERE customer_id = 'default' AND segment_name = 'Server-App-10.0.2.0/24'
)

UNION ALL

SELECT 
    'default', 'Server-FileShare-10.0.4.0/24', '10.0.4.0', '10.0.4.255',
    'SERVER', 'HIGH', 2.20,
    '文件服务器区 - 存储大量文件，是勒索软件的主要攻击目标', 85
WHERE NOT EXISTS (
    SELECT 1 FROM attack_source_weights 
    WHERE customer_id = 'default' AND segment_name = 'Server-FileShare-10.0.4.0/24'
)

UNION ALL

SELECT 
    'default', 'Server-Web-10.0.1.0/24', '10.0.1.0', '10.0.1.255',
    'SERVER', 'HIGH', 2.00,
    'Web服务器区 - 对外暴露服务，易被攻破后作为跳板进入内网', 80
WHERE NOT EXISTS (
    SELECT 1 FROM attack_source_weights 
    WHERE customer_id = 'default' AND segment_name = 'Server-Web-10.0.1.0/24'
)

UNION ALL

SELECT 
    'default', 'DMZ-Public-172.16.1.0/24', '172.16.1.0', '172.16.1.255',
    'DMZ', 'HIGH', 2.00,
    'DMZ区域 - 半信任区，易被攻陷后作为进入内网的跳板', 80
WHERE NOT EXISTS (
    SELECT 1 FROM attack_source_weights 
    WHERE customer_id = 'default' AND segment_name = 'DMZ-Public-172.16.1.0/24'
)

UNION ALL

SELECT 
    'default', 'Office-Executive-192.168.20.0/24', '192.168.20.0', '192.168.20.255',
    'OFFICE', 'HIGH', 1.80,
    '高管/财务办公区 - APT高价值目标，被攻陷后危害大，可能导致社工攻击升级', 70
WHERE NOT EXISTS (
    SELECT 1 FROM attack_source_weights 
    WHERE customer_id = 'default' AND segment_name = 'Office-Executive-192.168.20.0/24'
)

UNION ALL

SELECT 
    'default', 'Office-Dev-192.168.30.0/24', '192.168.30.0', '192.168.30.255',
    'OFFICE', 'MEDIUM', 1.30,
    '开发人员办公区 - 有代码和系统访问权限，被攻陷后可能导致供应链攻击', 60
WHERE NOT EXISTS (
    SELECT 1 FROM attack_source_weights 
    WHERE customer_id = 'default' AND segment_name = 'Office-Dev-192.168.30.0/24'
)

UNION ALL

SELECT 
    'default', 'IoT-Industrial-192.168.51.0/24', '192.168.51.0', '192.168.51.255',
    'IOT', 'MEDIUM', 1.20,
    '工控设备网段 - PLC/SCADA设备，被攻陷后可能影响生产运营', 65
WHERE NOT EXISTS (
    SELECT 1 FROM attack_source_weights 
    WHERE customer_id = 'default' AND segment_name = 'IoT-Industrial-192.168.51.0/24'
)

UNION ALL

SELECT 
    'default', 'Office-General-192.168.10.0/24', '192.168.10.0', '192.168.10.255',
    'OFFICE', 'LOW', 1.00,
    '普通办公区 - 日常办公人员，可能被钓鱼邮件攻陷，但危害相对可控', 50
WHERE NOT EXISTS (
    SELECT 1 FROM attack_source_weights 
    WHERE customer_id = 'default' AND segment_name = 'Office-General-192.168.10.0/24'
)

UNION ALL

SELECT 
    'default', 'IoT-Devices-192.168.50.0/24', '192.168.50.0', '192.168.50.255',
    'IOT', 'LOW', 0.90,
    'IoT设备网段 - 摄像头/打印机/门禁，易被劫持但权限有限，难以横向移动', 40
WHERE NOT EXISTS (
    SELECT 1 FROM attack_source_weights 
    WHERE customer_id = 'default' AND segment_name = 'IoT-Devices-192.168.50.0/24'
)

UNION ALL

SELECT 
    'default', 'Office-Guest-192.168.100.0/24', '192.168.100.0', '192.168.100.255',
    'GUEST', 'VERY_LOW', 0.60,
    '访客网络 - 物理隔离，无法访问内网资源，危害非常有限', 30
WHERE NOT EXISTS (
    SELECT 1 FROM attack_source_weights 
    WHERE customer_id = 'default' AND segment_name = 'Office-Guest-192.168.100.0/24'
)

UNION ALL

SELECT 
    'default', 'Default-Source-Catch-All', '0.0.0.0', '255.255.255.255',
    'OFFICE', 'MEDIUM', 1.00,
    '默认兜底规则 - 未配置攻击源网段使用基准权重', 10
WHERE NOT EXISTS (
    SELECT 1 FROM attack_source_weights 
    WHERE customer_id = 'default' AND segment_name = 'Default-Source-Catch-All'
);

-- =============================================================================
-- 默认配置数据 - 蜜罐敏感度权重
-- 用途: 为default客户提供标准蜜罐敏感度配置模板
-- =============================================================================

INSERT INTO honeypot_sensitivity_weights (
    customer_id, honeypot_name, ip_range_start, ip_range_end,
    honeypot_tier, deployment_zone, sensitivity_level, weight,
    simulated_service, attack_intent,
    description, priority
)
SELECT 
    'default', 'Honeypot-Management-Bastion', '10.0.100.50', '10.0.100.59',
    'CRITICAL_ASSET', 'MANAGEMENT', 'CRITICAL', 3.50,
    'SSH跳板机/堡垒机', '尝试控制全网，获取最高权限',
    '管理区蜜罐 - 模拟跳板机，访问意图：全网控制', 100
WHERE NOT EXISTS (
    SELECT 1 FROM honeypot_sensitivity_weights 
    WHERE customer_id = 'default' AND honeypot_name = 'Honeypot-Management-Bastion'
)

UNION ALL

SELECT 
    'default', 'Honeypot-Database-ProdDB', '10.0.3.50', '10.0.3.59',
    'CRITICAL_ASSET', 'DATABASE', 'CRITICAL', 3.50,
    'MySQL/PostgreSQL数据库', '尝试窃取数据，数据泄露风险',
    '数据库区蜜罐 - 模拟生产数据库，访问意图：数据窃取', 100
WHERE NOT EXISTS (
    SELECT 1 FROM honeypot_sensitivity_weights 
    WHERE customer_id = 'default' AND honeypot_name = 'Honeypot-Database-ProdDB'
)

UNION ALL

SELECT 
    'default', 'Honeypot-CoreServer-AppMaster', '10.0.2.50', '10.0.2.59',
    'HIGH_VALUE', 'CORE_SERVER', 'CRITICAL', 3.00,
    '核心业务应用服务器', '尝试破坏业务，影响业务连续性',
    '核心服务器蜜罐 - 模拟核心业务系统，访问意图：业务破坏', 95
WHERE NOT EXISTS (
    SELECT 1 FROM honeypot_sensitivity_weights 
    WHERE customer_id = 'default' AND honeypot_name = 'Honeypot-CoreServer-AppMaster'
)

UNION ALL

SELECT 
    'default', 'Honeypot-FileServer-NAS', '10.0.4.50', '10.0.4.59',
    'HIGH_VALUE', 'CORE_SERVER', 'HIGH', 2.50,
    'SMB/NFS文件共享服务器', '勒索软件加密目标，大规模数据窃取',
    '文件服务器蜜罐 - 模拟NAS存储，访问意图：勒索软件攻击', 85
WHERE NOT EXISTS (
    SELECT 1 FROM honeypot_sensitivity_weights 
    WHERE customer_id = 'default' AND honeypot_name = 'Honeypot-FileServer-NAS'
)

UNION ALL

SELECT 
    'default', 'Honeypot-AppServer-Business', '10.0.2.100', '10.0.2.109',
    'MEDIUM_VALUE', 'APP_SERVER', 'HIGH', 2.20,
    '业务应用服务器', '尝试访问业务系统，可能影响部分业务',
    '应用服务器蜜罐 - 模拟业务系统，访问意图：业务访问', 80
WHERE NOT EXISTS (
    SELECT 1 FROM honeypot_sensitivity_weights 
    WHERE customer_id = 'default' AND honeypot_name = 'Honeypot-AppServer-Business'
)

UNION ALL

SELECT 
    'default', 'Honeypot-WebServer-Public', '10.0.1.50', '10.0.1.59',
    'MEDIUM_VALUE', 'APP_SERVER', 'HIGH', 2.00,
    'Web应用服务器', '尝试利用Web漏洞，作为跳板进入内网',
    'Web服务器蜜罐 - 模拟对外Web服务，访问意图：跳板攻击', 80
WHERE NOT EXISTS (
    SELECT 1 FROM honeypot_sensitivity_weights 
    WHERE customer_id = 'default' AND honeypot_name = 'Honeypot-WebServer-Public'
)

UNION ALL

SELECT 
    'default', 'Honeypot-DMZ-External', '172.16.1.50', '172.16.1.59',
    'MEDIUM_VALUE', 'DMZ', 'MEDIUM', 1.80,
    'DMZ区域对外服务', '从DMZ突破进入内网，提权尝试',
    'DMZ蜜罐 - 模拟对外服务，访问意图：内网渗透跳板', 70
WHERE NOT EXISTS (
    SELECT 1 FROM honeypot_sensitivity_weights 
    WHERE customer_id = 'default' AND honeypot_name = 'Honeypot-DMZ-External'
)

UNION ALL

SELECT 
    'default', 'Honeypot-Office-Executive', '192.168.20.50', '192.168.20.59',
    'MEDIUM_VALUE', 'OFFICE', 'MEDIUM', 1.50,
    '高管办公区工作站', '尝试访问高价值目标，社工攻击升级',
    '高管办公区蜜罐 - 模拟高管工作站，访问意图：高价值目标攻击', 70
WHERE NOT EXISTS (
    SELECT 1 FROM honeypot_sensitivity_weights 
    WHERE customer_id = 'default' AND honeypot_name = 'Honeypot-Office-Executive'
)

UNION ALL

SELECT 
    'default', 'Honeypot-Office-General', '192.168.10.50', '192.168.10.59',
    'LOW_VALUE', 'OFFICE', 'MEDIUM', 1.30,
    '普通办公区工作站', '横向移动探测，寻找更高价值目标',
    '办公区蜜罐 - 模拟普通工作站，访问意图：横向移动探测', 60
WHERE NOT EXISTS (
    SELECT 1 FROM honeypot_sensitivity_weights 
    WHERE customer_id = 'default' AND honeypot_name = 'Honeypot-Office-General'
)

UNION ALL

SELECT 
    'default', 'Honeypot-Office-Dev', '192.168.30.50', '192.168.30.59',
    'MEDIUM_VALUE', 'OFFICE', 'MEDIUM', 1.40,
    '开发人员工作站', '尝试访问开发环境，可能导致供应链攻击',
    '开发区蜜罐 - 模拟开发工作站，访问意图：开发环境渗透', 65
WHERE NOT EXISTS (
    SELECT 1 FROM honeypot_sensitivity_weights 
    WHERE customer_id = 'default' AND honeypot_name = 'Honeypot-Office-Dev'
)

UNION ALL

SELECT 
    'default', 'Default-Honeypot-Catch-All', '0.0.0.0', '255.255.255.255',
    'DECOY', 'OFFICE', 'LOW', 1.00,
    '通用诱饵', '未分类的探测行为',
    '默认兜底规则 - 未配置蜜罐使用基准敏感度权重', 10
WHERE NOT EXISTS (
    SELECT 1 FROM honeypot_sensitivity_weights 
    WHERE customer_id = 'default' AND honeypot_name = 'Default-Honeypot-Catch-All'
);

-- =============================================================================
-- 查询函数1：获取攻击源权重
-- 用途: 根据 customer_id 和 attack_ip 查询对应的攻击源网段权重
-- 返回: 权重值（默认1.00）
-- =============================================================================

CREATE OR REPLACE FUNCTION get_attack_source_weight(
    p_customer_id VARCHAR(50),
    p_attack_ip VARCHAR(15)
) RETURNS DECIMAL(3,2) AS $$
DECLARE
    v_weight DECIMAL(3,2);
BEGIN
    -- 查询匹配的攻击源网段权重（优先级从高到低）
    SELECT weight INTO v_weight
    FROM attack_source_weights
    WHERE customer_id = p_customer_id
      AND is_active = TRUE
      AND CAST(p_attack_ip AS inet) BETWEEN CAST(ip_range_start AS inet) AND CAST(ip_range_end AS inet)
    ORDER BY priority DESC, id DESC
    LIMIT 1;
    
    -- 如果没有匹配到，返回默认权重1.00
    RETURN COALESCE(v_weight, 1.00);
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_attack_source_weight(VARCHAR, VARCHAR) IS '根据客户ID和攻击源IP查询攻击源网段权重（V4.0）';

-- =============================================================================
-- 查询函数2：获取蜜罐敏感度权重
-- 用途: 根据 customer_id 和 honeypot_ip (response_ip) 查询对应的蜜罐敏感度权重
-- 返回: 权重值（默认1.00）
-- =============================================================================

CREATE OR REPLACE FUNCTION get_honeypot_sensitivity_weight(
    p_customer_id VARCHAR(50),
    p_honeypot_ip VARCHAR(15)
) RETURNS DECIMAL(3,2) AS $$
DECLARE
    v_weight DECIMAL(3,2);
BEGIN
    -- 查询匹配的蜜罐敏感度权重（优先级从高到低）
    SELECT weight INTO v_weight
    FROM honeypot_sensitivity_weights
    WHERE customer_id = p_customer_id
      AND is_active = TRUE
      AND CAST(p_honeypot_ip AS inet) BETWEEN CAST(ip_range_start AS inet) AND CAST(ip_range_end AS inet)
    ORDER BY priority DESC, id DESC
    LIMIT 1;
    
    -- 如果没有匹配到，返回默认权重1.00
    RETURN COALESCE(v_weight, 1.00);
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_honeypot_sensitivity_weight(VARCHAR, VARCHAR) IS '根据客户ID和蜜罐IP查询蜜罐敏感度权重（V4.0）';

-- =============================================================================
-- 查询函数3：获取综合网段权重（双维度）
-- 用途: 同时查询攻击源权重和蜜罐敏感度权重，返回综合权重
-- 返回: 综合权重 = attackSourceWeight × honeypotSensitivityWeight
-- =============================================================================

CREATE OR REPLACE FUNCTION get_combined_segment_weight(
    p_customer_id VARCHAR(50),
    p_attack_ip VARCHAR(15),
    p_honeypot_ip VARCHAR(15)
) RETURNS TABLE(
    attack_source_weight DECIMAL(3,2),
    honeypot_sensitivity_weight DECIMAL(3,2),
    combined_weight DECIMAL(6,4)
) AS $$
DECLARE
    v_source_weight DECIMAL(3,2);
    v_honeypot_weight DECIMAL(3,2);
BEGIN
    -- 获取攻击源权重
    v_source_weight := get_attack_source_weight(p_customer_id, p_attack_ip);
    
    -- 获取蜜罐敏感度权重
    v_honeypot_weight := get_honeypot_sensitivity_weight(p_customer_id, p_honeypot_ip);
    
    -- 返回结果
    RETURN QUERY SELECT 
        v_source_weight,
        v_honeypot_weight,
        (v_source_weight * v_honeypot_weight)::DECIMAL(6,4);
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_combined_segment_weight(VARCHAR, VARCHAR, VARCHAR) IS '获取综合网段权重（攻击源权重 × 蜜罐敏感度权重）（V4.0）';

-- =============================================================================
-- 测试查询示例
-- 用途: 验证配置是否正确
-- =============================================================================

-- 1. 查询所有默认攻击源配置（按权重排序）
-- SELECT segment_name, segment_type, risk_level, weight, description
-- FROM attack_source_weights
-- WHERE customer_id = 'default' AND is_active = TRUE
-- ORDER BY weight DESC, segment_name;

-- 2. 查询所有默认蜜罐配置（按敏感度排序）
-- SELECT honeypot_name, honeypot_tier, deployment_zone, sensitivity_level, weight, simulated_service, attack_intent
-- FROM honeypot_sensitivity_weights
-- WHERE customer_id = 'default' AND is_active = TRUE
-- ORDER BY weight DESC, honeypot_name;

-- 3. 测试攻击源权重查询函数
-- SELECT get_attack_source_weight('default', '192.168.10.50');  -- 应返回 1.00 (普通办公区)
-- SELECT get_attack_source_weight('default', '192.168.20.100'); -- 应返回 1.80 (高管区)
-- SELECT get_attack_source_weight('default', '10.0.3.10');      -- 应返回 3.00 (数据库服务器)
-- SELECT get_attack_source_weight('default', '192.168.50.20');  -- 应返回 0.90 (IoT设备)

-- 4. 测试蜜罐敏感度权重查询函数
-- SELECT get_honeypot_sensitivity_weight('default', '10.0.100.50'); -- 应返回 3.50 (管理区蜜罐)
-- SELECT get_honeypot_sensitivity_weight('default', '10.0.3.50');   -- 应返回 3.50 (数据库蜜罐)
-- SELECT get_honeypot_sensitivity_weight('default', '192.168.10.50'); -- 应返回 1.30 (办公区蜜罐)

-- 5. 测试综合权重查询函数（关键场景）
-- 场景1: IoT设备扫描管理区蜜罐（高危）
-- SELECT * FROM get_combined_segment_weight('default', '192.168.50.10', '10.0.100.50');
-- 预期: attack_source_weight=0.90, honeypot_sensitivity_weight=3.50, combined_weight=3.15 (高威胁！)

-- 场景2: 数据库服务器扫描办公区蜜罐（双重风险）
-- SELECT * FROM get_combined_segment_weight('default', '10.0.3.10', '192.168.10.50');
-- 预期: attack_source_weight=3.00, honeypot_sensitivity_weight=1.30, combined_weight=3.90 (极高威胁！)

-- 场景3: 办公区设备扫描办公区蜜罐（低-中威胁）
-- SELECT * FROM get_combined_segment_weight('default', '192.168.10.100', '192.168.10.50');
-- 预期: attack_source_weight=1.00, honeypot_sensitivity_weight=1.30, combined_weight=1.30 (中等威胁)

-- =============================================================================
-- V3.0 → V4.0 迁移说明
-- =============================================================================

-- 如果您之前安装了 V3.0 版本（11-ip-segment-weights-v3.sql），请执行以下迁移：

-- 1. 备份现有数据
-- CREATE TABLE ip_segment_weight_config_v3_backup AS SELECT * FROM ip_segment_weight_config;

-- 2. V3.0表可以直接映射到V4.0的attack_source_weights表
-- INSERT INTO attack_source_weights (
--     customer_id, segment_name, ip_range_start, ip_range_end,
--     segment_type, risk_level, weight, description, priority
-- )
-- SELECT 
--     customer_id, segment_name, ip_range_start, ip_range_end,
--     segment_type, risk_level, weight, description, priority
-- FROM ip_segment_weight_config_v3_backup
-- WHERE customer_id != 'default';

-- 3. 蜜罐敏感度配置需要客户手动创建（V3.0没有这个概念）
-- 客户需要根据实际蜜罐部署情况，配置honeypot_sensitivity_weights表

-- 4. 删除旧表（可选）
-- DROP TABLE IF EXISTS ip_segment_weight_config;

-- =============================================================================
-- 配置示例 - 客户自定义
-- =============================================================================

-- 示例: 客户A配置攻击源权重
-- INSERT INTO attack_source_weights (
--     customer_id, segment_name, ip_range_start, ip_range_end,
--     segment_type, risk_level, weight, description
-- ) VALUES
--     ('customer-A', 'HQ-Office-Floor1', '192.168.1.0', '192.168.1.255',
--      'OFFICE', 'MEDIUM', 1.2, '总部办公区1楼'),
--     ('customer-A', 'Core-DataCenter', '10.100.0.0', '10.100.255.255',
--      'DATABASE', 'CRITICAL', 3.0, '核心机房 - 所有生产数据库');

-- 示例: 客户A配置蜜罐敏感度
-- INSERT INTO honeypot_sensitivity_weights (
--     customer_id, honeypot_name, ip_range_start, ip_range_end,
--     honeypot_tier, deployment_zone, sensitivity_level, weight,
--     simulated_service, attack_intent, description
-- ) VALUES
--     ('customer-A', 'Honeypot-DB-Master', '10.100.1.50', '10.100.1.59',
--      'CRITICAL_ASSET', 'DATABASE', 'CRITICAL', 3.5,
--      'Oracle数据库主库', '尝试窃取生产数据',
--      '核心数据库蜜罐 - 模拟生产环境主库');

-- =============================================================================
-- 批量导入示例（CSV格式）
-- =============================================================================

-- 攻击源权重CSV格式:
-- customer_id,segment_name,ip_range_start,ip_range_end,segment_type,risk_level,weight,description
-- customer-B,Office-HQ,192.168.1.0,192.168.1.255,OFFICE,MEDIUM,1.2,总部办公区
-- customer-B,Server-Prod,10.0.1.0,10.0.1.255,SERVER,CRITICAL,2.8,生产服务器

-- 蜜罐敏感度CSV格式:
-- customer_id,honeypot_name,ip_range_start,ip_range_end,honeypot_tier,deployment_zone,sensitivity_level,weight,simulated_service,attack_intent,description
-- customer-B,HP-DB-Prod,10.0.1.50,10.0.1.59,CRITICAL_ASSET,DATABASE,CRITICAL,3.5,MySQL数据库,数据窃取,生产数据库蜜罐

-- =============================================================================
-- 结束
-- =============================================================================
