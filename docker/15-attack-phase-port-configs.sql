-- ============================================
-- 攻击阶段端口配置表 - 多租户支持
-- ============================================
--
-- 目标: 支持客户独立的攻击阶段端口配置
-- 设计理念:
--   1. 支持每个客户自定义攻击阶段端口配置
--   2. 继承全局默认配置 (customer_id IS NULL)
--   3. 优先级: 客户自定义 > 全局默认
--   4. 支持动态配置更新
--
-- 参考: customer_port_weights 和 ip_segment_weight_config 表设计
-- ============================================

-- 创建攻击阶段端口配置表 (多租户版本)
CREATE TABLE IF NOT EXISTS attack_phase_port_configs (
    id SERIAL PRIMARY KEY,

    -- 多租户支持 (NULL表示全局默认配置)
    customer_id VARCHAR(50),

    -- 阶段配置
    phase VARCHAR(20) NOT NULL,
    port_number INTEGER NOT NULL,
    port_name VARCHAR(100) NOT NULL,
    priority INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    description TEXT,

    -- 审计字段
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 约束条件
    CONSTRAINT uk_customer_phase_port UNIQUE(customer_id, phase, port_number),
    CONSTRAINT ck_phase CHECK (phase IN ('RECON', 'EXPLOITATION', 'PERSISTENCE')),
    CONSTRAINT ck_port_number CHECK (port_number >= 1 AND port_number <= 65535),
    CONSTRAINT ck_priority CHECK (priority >= 0 AND priority <= 100)
);

-- 创建索引优化查询
CREATE INDEX idx_attack_phase_port_configs_customer ON attack_phase_port_configs(customer_id);
CREATE INDEX idx_attack_phase_port_configs_phase ON attack_phase_port_configs(phase);
CREATE INDEX idx_attack_phase_port_configs_port ON attack_phase_port_configs(port_number);
CREATE INDEX idx_attack_phase_port_configs_enabled ON attack_phase_port_configs(enabled);
CREATE INDEX idx_attack_phase_port_configs_composite ON attack_phase_port_configs(customer_id, phase, enabled, priority DESC);

-- 创建更新时间戳触发器
CREATE OR REPLACE FUNCTION update_attack_phase_port_configs_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_attack_phase_port_configs_updated
    BEFORE UPDATE ON attack_phase_port_configs
    FOR EACH ROW
    EXECUTE FUNCTION update_attack_phase_port_configs_timestamp();

-- ============================================
-- 默认配置数据 - 全局默认配置 (customer_id = NULL)
-- ============================================

-- RECON阶段端口 (侦察阶段)
INSERT INTO attack_phase_port_configs (customer_id, phase, port_number, port_name, priority, description) VALUES
(NULL, 'RECON', 21, 'FTP', 90, 'FTP文件传输协议 - 常见扫描目标'),
(NULL, 'RECON', 22, 'SSH', 95, 'SSH远程登录服务 - 高频扫描端口'),
(NULL, 'RECON', 23, 'Telnet', 90, 'Telnet远程登录服务 - 不安全但常见'),
(NULL, 'RECON', 25, 'SMTP', 80, 'SMTP邮件服务 - 邮件服务器扫描'),
(NULL, 'RECON', 53, 'DNS', 85, 'DNS域名服务 - 网络基础设施'),
(NULL, 'RECON', 80, 'HTTP', 100, 'HTTP超文本传输协议 - 最常见Web端口'),
(NULL, 'RECON', 110, 'POP3', 75, 'POP3邮件接收协议 - 邮件服务扫描'),
(NULL, 'RECON', 143, 'IMAP', 75, 'IMAP邮件访问协议 - 邮件服务扫描'),
(NULL, 'RECON', 443, 'HTTPS', 100, 'HTTPS安全超文本传输协议 - SSL Web端口'),
(NULL, 'RECON', 993, 'IMAPS', 70, 'IMAPS安全邮件协议 - 加密邮件访问'),
(NULL, 'RECON', 995, 'POP3S', 70, 'POP3S安全邮件协议 - 加密邮件接收')
ON CONFLICT (customer_id, phase, port_number) DO NOTHING;

-- EXPLOITATION阶段端口 (利用阶段)
INSERT INTO attack_phase_port_configs (customer_id, phase, port_number, port_name, priority, description) VALUES
(NULL, 'EXPLOITATION', 135, 'RPC', 90, 'Windows远程过程调用 - 常见漏洞利用'),
(NULL, 'EXPLOITATION', 139, 'NetBIOS', 85, 'NetBIOS会话服务 - Windows网络服务'),
(NULL, 'EXPLOITATION', 445, 'SMB', 95, 'SMB文件共享协议 - EternalBlue等漏洞'),
(NULL, 'EXPLOITATION', 3389, 'RDP', 100, 'RDP远程桌面协议 - 远程控制利用'),
(NULL, 'EXPLOITATION', 5985, 'WinRM-HTTP', 80, 'Windows远程管理HTTP - PowerShell远程'),
(NULL, 'EXPLOITATION', 5986, 'WinRM-HTTPS', 85, 'Windows远程管理HTTPS - 安全远程管理')
ON CONFLICT (customer_id, phase, port_number) DO NOTHING;

-- PERSISTENCE阶段端口 (持久化阶段)
INSERT INTO attack_phase_port_configs (customer_id, phase, port_number, port_name, priority, description) VALUES
(NULL, 'PERSISTENCE', 3306, 'MySQL', 95, 'MySQL数据库服务 - 数据窃取目标'),
(NULL, 'PERSISTENCE', 5432, 'PostgreSQL', 90, 'PostgreSQL数据库服务 - 数据窃取目标'),
(NULL, 'PERSISTENCE', 6379, 'Redis', 85, 'Redis内存数据库 - 缓存数据窃取'),
(NULL, 'PERSISTENCE', 27017, 'MongoDB', 85, 'MongoDB文档数据库 - NoSQL数据窃取'),
(NULL, 'PERSISTENCE', 1433, 'SQL Server', 90, 'Microsoft SQL Server - 企业数据库')
ON CONFLICT (customer_id, phase, port_number) DO NOTHING;

-- ============================================
-- 视图和查询函数 (多租户版本)
-- ============================================

-- 阶段端口配置统计视图 (支持多租户)
CREATE OR REPLACE VIEW v_attack_phase_port_stats AS
SELECT
    customer_id,
    phase,
    COUNT(*) as port_count,
    COUNT(CASE WHEN enabled = TRUE THEN 1 END) as enabled_count,
    AVG(priority) as avg_priority,
    MAX(priority) as max_priority,
    MIN(priority) as min_priority,
    MAX(updated_at) as last_updated
FROM attack_phase_port_configs
GROUP BY customer_id, phase
ORDER BY
    customer_id NULLS FIRST,
    CASE phase
        WHEN 'RECON' THEN 1
        WHEN 'EXPLOITATION' THEN 2
        WHEN 'PERSISTENCE' THEN 3
    END;

-- 函数: 获取指定客户和阶段的所有端口 (优先级: 客户自定义 > 全局默认)
CREATE OR REPLACE FUNCTION get_customer_phase_ports(p_customer_id VARCHAR(50), p_phase VARCHAR(20))
RETURNS TABLE(
    port_number INTEGER,
    port_name VARCHAR(100),
    priority INTEGER,
    description TEXT,
    source TEXT
) AS $$
BEGIN
    RETURN QUERY
    -- 首先返回客户自定义配置
    SELECT
        apc.port_number,
        apc.port_name,
        apc.priority,
        apc.description,
        'CUSTOM'::TEXT as source
    FROM attack_phase_port_configs apc
    WHERE apc.customer_id = p_customer_id
      AND apc.phase = p_phase
      AND apc.enabled = TRUE

    UNION ALL

    -- 然后返回全局默认配置 (排除客户已自定义的端口)
    SELECT
        apc.port_number,
        apc.port_name,
        apc.priority,
        apc.description,
        'GLOBAL'::TEXT as source
    FROM attack_phase_port_configs apc
    WHERE apc.customer_id IS NULL
      AND apc.phase = p_phase
      AND apc.enabled = TRUE
      AND NOT EXISTS (
          SELECT 1 FROM attack_phase_port_configs custom
          WHERE custom.customer_id = p_customer_id
            AND custom.phase = p_phase
            AND custom.port_number = apc.port_number
            AND custom.enabled = TRUE
      )
    ORDER BY priority DESC, port_number;
END;
$$ LANGUAGE plpgsql;

-- 函数: 检查端口是否属于指定客户的阶段 (优先级: 客户自定义 > 全局默认)
CREATE OR REPLACE FUNCTION is_port_in_customer_phase(p_customer_id VARCHAR(50), p_port_number INTEGER, p_phase VARCHAR(20))
RETURNS BOOLEAN AS $$
DECLARE
    v_count INTEGER;
BEGIN
    -- 首先检查客户自定义配置
    SELECT COUNT(*) INTO v_count
    FROM attack_phase_port_configs
    WHERE customer_id = p_customer_id
      AND port_number = p_port_number
      AND phase = p_phase
      AND enabled = TRUE;

    -- 如果客户有自定义配置，直接返回结果
    IF v_count > 0 THEN
        RETURN TRUE;
    END IF;

    -- 否则检查全局默认配置
    SELECT COUNT(*) INTO v_count
    FROM attack_phase_port_configs
    WHERE customer_id IS NULL
      AND port_number = p_port_number
      AND phase = p_phase
      AND enabled = TRUE;

    RETURN v_count > 0;
END;
$$ LANGUAGE plpgsql;

-- 函数: 获取端口所属的所有客户阶段
CREATE OR REPLACE FUNCTION get_port_customer_phases(p_customer_id VARCHAR(50), p_port_number INTEGER)
RETURNS TABLE(phase VARCHAR(20), source TEXT) AS $$
BEGIN
    RETURN QUERY
    SELECT * FROM (
        -- 客户自定义配置
        SELECT DISTINCT apc.phase, 'CUSTOM'::TEXT as source
        FROM attack_phase_port_configs apc
        WHERE apc.customer_id = p_customer_id
          AND apc.port_number = p_port_number
          AND apc.enabled = TRUE

        UNION

        -- 全局默认配置 (仅当客户没有自定义时)
        SELECT DISTINCT apc.phase, 'GLOBAL'::TEXT as source
        FROM attack_phase_port_configs apc
        WHERE apc.customer_id IS NULL
          AND apc.port_number = p_port_number
          AND apc.enabled = TRUE
          AND NOT EXISTS (
              SELECT 1 FROM attack_phase_port_configs custom
              WHERE custom.customer_id = p_customer_id
                AND custom.port_number = p_port_number
                AND custom.phase = apc.phase
                AND custom.enabled = TRUE
          )
    ) AS combined_results(phase, source)
    ORDER BY
        CASE combined_results.phase
            WHEN 'RECON' THEN 1
            WHEN 'EXPLOITATION' THEN 2
            WHEN 'PERSISTENCE' THEN 3
        END;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- 数据验证 (多租户版本)
-- ============================================

-- 验证表结构和数据
SELECT
    'attack_phase_port_configs表创建成功' as status,
    COUNT(*) as total_records,
    COUNT(DISTINCT customer_id) as total_customers_including_global,
    COUNT(DISTINCT phase) as total_phases,
    COUNT(DISTINCT port_number) as unique_ports
FROM attack_phase_port_configs;

-- 验证各阶段配置 (全局默认)
SELECT
    '全局默认配置统计' as check_type,
    phase,
    COUNT(*) as port_count,
    COUNT(CASE WHEN enabled = TRUE THEN 1 END) as enabled_count
FROM attack_phase_port_configs
WHERE customer_id IS NULL
GROUP BY phase
ORDER BY
    CASE phase
        WHEN 'RECON' THEN 1
        WHEN 'EXPLOITATION' THEN 2
        WHEN 'PERSISTENCE' THEN 3
    END;

-- 验证函数测试
SELECT
    'get_customer_phase_ports函数测试 - 全局配置 RECON阶段' as test_name,
    *
FROM get_customer_phase_ports(NULL, 'RECON')
LIMIT 5;

SELECT
    'is_port_in_customer_phase函数测试' as test_name,
    22 as test_port,
    'RECON' as test_phase,
    is_port_in_customer_phase(NULL, 22, 'RECON') as is_recon_port_global,
    is_port_in_customer_phase(NULL, 22, 'EXPLOITATION') as is_exploitation_port_global;

SELECT
    'get_port_customer_phases函数测试' as test_name,
    445 as test_port,
    phase,
    source
FROM get_port_customer_phases(NULL, 445);

-- ============================================
-- 使用示例 (多租户版本)
-- ============================================

-- 示例1: 查询所有客户的阶段端口配置
/*
SELECT customer_id, phase, port_number, port_name, priority, description
FROM attack_phase_port_configs
WHERE enabled = TRUE
ORDER BY
    customer_id NULLS FIRST,
    CASE phase
        WHEN 'RECON' THEN 1
        WHEN 'EXPLOITATION' THEN 2
        WHEN 'PERSISTENCE' THEN 3
    END,
    priority DESC;
*/

-- 示例2: 获取特定客户的阶段端口列表
/*
SELECT port_number FROM get_customer_phase_ports('customer-001', 'EXPLOITATION');
*/

-- 示例3: 检查端口是否属于特定客户的某个阶段
/*
SELECT is_port_in_customer_phase('customer-001', 3389, 'EXPLOITATION') as is_rdp_exploitation;
*/

-- 示例4: 获取端口所属的所有客户阶段
/*
SELECT phase, source FROM get_port_customer_phases('customer-001', 3306);
*/

-- 示例5: 为客户添加自定义配置
/*
INSERT INTO attack_phase_port_configs (customer_id, phase, port_number, port_name, priority, description, created_by)
VALUES ('customer-001', 'EXPLOITATION', 8080, 'Custom-Web', 85, '客户自定义Web漏洞利用端口', 'admin');
*/

-- 示例6: 更新客户配置优先级
/*
UPDATE attack_phase_port_configs
SET priority = 98, updated_by = 'admin', updated_at = CURRENT_TIMESTAMP
WHERE customer_id = 'customer-001' AND phase = 'RECON' AND port_number = 22;
*/

-- 示例7: 禁用客户的某个端口配置
/*
UPDATE attack_phase_port_configs
SET enabled = FALSE, updated_by = 'admin'
WHERE customer_id = 'customer-001' AND phase = 'RECON' AND port_number = 23;
*/

-- 示例8: 查看客户配置覆盖情况
/*
SELECT
    c.customer_id,
    c.phase,
    c.port_number,
    c.port_name as custom_name,
    g.port_name as global_name,
    c.priority as custom_priority,
    g.priority as global_priority,
    CASE WHEN c.id IS NOT NULL THEN 'CUSTOM' ELSE 'GLOBAL' END as source
FROM attack_phase_port_configs g
LEFT JOIN attack_phase_port_configs c ON (
    c.customer_id = 'customer-001'
    AND c.phase = g.phase
    AND c.port_number = g.port_number
)
WHERE g.customer_id IS NULL
  AND g.enabled = TRUE
ORDER BY g.phase, g.priority DESC;
*/

-- ============================================
-- 脚本完成
-- ============================================

SELECT
    '✅ 攻击阶段端口配置表创建完成' as status,
    CURRENT_TIMESTAMP as completion_time;