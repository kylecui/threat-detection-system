-- ============================================
-- 客户端口权重配置表 - 多租户支持
-- ============================================
--
-- 目标: 实现多租户的端口权重自定义配置
-- 设计理念: 
--   1. 支持每个客户自定义端口权重
--   2. 继承全局默认配置 (port_risk_configs)
--   3. 优先级: 客户自定义 > 全局默认
--   4. 混合策略: portWeight = max(configWeight, diversityWeight)
--
-- 参考: customer_ip_segment_weights 表设计
-- ============================================

-- 创建客户端口权重配置表
CREATE TABLE IF NOT EXISTS customer_port_weights (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    port_number INTEGER NOT NULL,
    port_name VARCHAR(100),
    weight DECIMAL(4,2) NOT NULL DEFAULT 1.0,
    risk_level VARCHAR(20) DEFAULT 'MEDIUM',
    attack_intent VARCHAR(200),
    description TEXT,
    priority INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    
    -- 审计字段
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 约束条件
    CONSTRAINT uk_customer_port UNIQUE(customer_id, port_number),
    CONSTRAINT ck_port_number CHECK (port_number >= 1 AND port_number <= 65535),
    CONSTRAINT ck_weight CHECK (weight >= 0.5 AND weight <= 10.0),
    CONSTRAINT ck_priority CHECK (priority >= 0 AND priority <= 100),
    CONSTRAINT ck_risk_level CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

-- 创建索引优化查询
CREATE INDEX idx_customer_port_weights_customer ON customer_port_weights(customer_id);
CREATE INDEX idx_customer_port_weights_port ON customer_port_weights(port_number);
CREATE INDEX idx_customer_port_weights_enabled ON customer_port_weights(enabled);
CREATE INDEX idx_customer_port_weights_composite ON customer_port_weights(customer_id, enabled, priority DESC);
CREATE INDEX idx_customer_port_weights_risk ON customer_port_weights(risk_level);

-- 创建更新时间戳触发器
CREATE OR REPLACE FUNCTION update_customer_port_weights_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_customer_port_weights_updated
    BEFORE UPDATE ON customer_port_weights
    FOR EACH ROW
    EXECUTE FUNCTION update_customer_port_weights_timestamp();

-- ============================================
-- 默认配置数据 (示例客户)
-- ============================================

-- 为测试客户创建示例配置
INSERT INTO customer_port_weights (customer_id, port_number, port_name, weight, risk_level, attack_intent, description, priority) VALUES
-- 高危端口 (权重10.0)
('test', 22, 'SSH', 10.0, 'CRITICAL', 'SSH远程控制', '测试客户自定义SSH端口权重', 100),
('test', 3389, 'RDP', 10.0, 'CRITICAL', 'RDP远程桌面', '测试客户自定义RDP端口权重', 100),
('test', 445, 'SMB', 9.5, 'CRITICAL', 'SMB横向移动', '测试客户自定义SMB端口权重', 90),

-- 数据库端口 (权重8.5-9.0)
('test', 3306, 'MySQL', 9.0, 'HIGH', 'MySQL数据库攻击', '测试客户MySQL端口权重', 85),
('test', 1433, 'SQL Server', 9.0, 'HIGH', 'SQL Server攻击', '测试客户SQL Server权重', 85),

-- Web端口 (权重6.0)
('test', 80, 'HTTP', 6.0, 'MEDIUM', 'HTTP Web应用攻击', '测试客户HTTP端口权重', 60),
('test', 443, 'HTTPS', 6.0, 'MEDIUM', 'HTTPS Web应用攻击', '测试客户HTTPS端口权重', 60)

ON CONFLICT (customer_id, port_number) DO NOTHING;

-- ============================================
-- 视图: 端口权重综合查询
-- ============================================

-- 创建综合视图 (客户配置 + 全局默认)
CREATE OR REPLACE VIEW v_port_weights_combined AS
WITH customer_weights AS (
    SELECT 
        customer_id,
        port_number,
        port_name,
        weight,
        risk_level,
        attack_intent,
        description,
        priority,
        'CUSTOM' as source,
        enabled
    FROM customer_port_weights
    WHERE enabled = TRUE
),
global_weights AS (
    SELECT 
        NULL as customer_id,
        port_number,
        port_name,
        risk_weight as weight,
        risk_level,
        attack_intent,
        description,
        0 as priority,
        config_source as source,
        enabled
    FROM port_risk_configs
    WHERE enabled = TRUE
)
SELECT * FROM customer_weights
UNION ALL
SELECT * FROM global_weights
ORDER BY customer_id NULLS LAST, priority DESC, port_number;

-- ============================================
-- 端口权重查询函数
-- ============================================

-- 函数: 获取指定客户的端口权重 (优先级: 自定义 > 全局)
CREATE OR REPLACE FUNCTION get_port_weight(
    p_customer_id VARCHAR(50),
    p_port_number INTEGER
)
RETURNS DECIMAL(4,2) AS $$
DECLARE
    v_weight DECIMAL(4,2);
BEGIN
    -- 1. 优先查询客户自定义配置
    SELECT weight INTO v_weight
    FROM customer_port_weights
    WHERE customer_id = p_customer_id
      AND port_number = p_port_number
      AND enabled = TRUE
    LIMIT 1;
    
    -- 2. 如果没有自定义配置,使用全局默认
    IF v_weight IS NULL THEN
        SELECT risk_weight INTO v_weight
        FROM port_risk_configs
        WHERE port_number = p_port_number
          AND enabled = TRUE
        LIMIT 1;
    END IF;
    
    -- 3. 如果没有任何配置,返回默认权重1.0
    RETURN COALESCE(v_weight, 1.0);
END;
$$ LANGUAGE plpgsql;

-- 函数: 批量获取端口权重
CREATE OR REPLACE FUNCTION get_port_weights_batch(
    p_customer_id VARCHAR(50),
    p_port_numbers INTEGER[]
)
RETURNS TABLE(
    port_number INTEGER,
    weight DECIMAL(4,2),
    source TEXT
) AS $$
BEGIN
    RETURN QUERY
    WITH port_list AS (
        SELECT unnest(p_port_numbers) as port_num
    ),
    customer_config AS (
        SELECT 
            cpw.port_number,
            cpw.weight,
            'CUSTOM'::TEXT as source
        FROM customer_port_weights cpw
        INNER JOIN port_list pl ON cpw.port_number = pl.port_num
        WHERE cpw.customer_id = p_customer_id
          AND cpw.enabled = TRUE
    ),
    global_config AS (
        SELECT 
            prc.port_number,
            prc.risk_weight as weight,
            COALESCE(prc.config_source, 'GLOBAL')::TEXT as source
        FROM port_risk_configs prc
        INNER JOIN port_list pl ON prc.port_number = pl.port_num
        LEFT JOIN customer_config cc ON prc.port_number = cc.port_number
        WHERE prc.enabled = TRUE
          AND cc.port_number IS NULL  -- 排除已有客户配置的端口
    ),
    default_config AS (
        SELECT 
            pl.port_num as port_number,
            1.0::DECIMAL(4,2) as weight,
            'DEFAULT'::TEXT as source
        FROM port_list pl
        LEFT JOIN customer_config cc ON pl.port_num = cc.port_number
        LEFT JOIN global_config gc ON pl.port_num = gc.port_number
        WHERE cc.port_number IS NULL 
          AND gc.port_number IS NULL  -- 没有任何配置的端口
    )
    SELECT * FROM customer_config
    UNION ALL
    SELECT * FROM global_config
    UNION ALL
    SELECT * FROM default_config
    ORDER BY port_number;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- 统计视图
-- ============================================

-- 客户端口权重统计
CREATE OR REPLACE VIEW v_customer_port_weight_stats AS
SELECT 
    customer_id,
    COUNT(*) as total_configs,
    COUNT(CASE WHEN risk_level = 'CRITICAL' THEN 1 END) as critical_ports,
    COUNT(CASE WHEN risk_level = 'HIGH' THEN 1 END) as high_ports,
    COUNT(CASE WHEN risk_level = 'MEDIUM' THEN 1 END) as medium_ports,
    COUNT(CASE WHEN risk_level = 'LOW' THEN 1 END) as low_ports,
    AVG(weight) as avg_weight,
    MAX(weight) as max_weight,
    MIN(weight) as min_weight,
    COUNT(CASE WHEN enabled = TRUE THEN 1 END) as enabled_count,
    MAX(updated_at) as last_updated
FROM customer_port_weights
GROUP BY customer_id
ORDER BY customer_id;

-- ============================================
-- 数据验证
-- ============================================

-- 验证表结构
SELECT 
    'customer_port_weights表创建成功' as status,
    COUNT(*) as total_records,
    COUNT(DISTINCT customer_id) as total_customers,
    COUNT(DISTINCT port_number) as unique_ports
FROM customer_port_weights;

-- 验证函数
SELECT 
    'get_port_weight函数测试' as test_name,
    get_port_weight('test', 22) as ssh_weight_test,
    get_port_weight('test', 80) as http_weight_test,
    get_port_weight('test', 12345) as unknown_port_weight_test;

-- 验证批量查询函数
SELECT 
    'get_port_weights_batch函数测试' as test_name,
    *
FROM get_port_weights_batch('test', ARRAY[22, 80, 443, 3389, 12345]);

-- ============================================
-- 使用示例
-- ============================================

-- 示例1: 查询客户的所有端口权重配置
/*
SELECT * FROM customer_port_weights 
WHERE customer_id = 'customer-001' 
AND enabled = TRUE
ORDER BY priority DESC, weight DESC;
*/

-- 示例2: 获取单个端口的权重
/*
SELECT get_port_weight('customer-001', 22) as ssh_weight;
*/

-- 示例3: 批量获取端口权重
/*
SELECT * FROM get_port_weights_batch('customer-001', ARRAY[22,80,443,3389,3306]);
*/

-- 示例4: 插入新的客户配置
/*
INSERT INTO customer_port_weights (
    customer_id, port_number, port_name, weight, 
    risk_level, attack_intent, description, priority
) VALUES (
    'customer-001', 8080, 'Custom-HTTP-Alt', 7.0,
    'HIGH', 'Web应用攻击', '客户自定义8080端口权重', 80
);
*/

-- 示例5: 更新配置
/*
UPDATE customer_port_weights 
SET weight = 9.5, 
    updated_by = 'admin',
    updated_at = CURRENT_TIMESTAMP
WHERE customer_id = 'customer-001' 
AND port_number = 22;
*/

-- 示例6: 禁用配置
/*
UPDATE customer_port_weights 
SET enabled = FALSE 
WHERE customer_id = 'customer-001' 
AND port_number = 80;
*/

-- ============================================
-- 脚本完成
-- ============================================

SELECT 
    '✅ 客户端口权重配置表创建完成' as status,
    CURRENT_TIMESTAMP as completion_time;
