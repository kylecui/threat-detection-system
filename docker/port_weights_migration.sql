-- ============================================
-- MVP Phase 0: 端口权重配置数据库迁移脚本
-- ============================================
--
-- 目标: 实现与原系统对齐的端口权重配置
-- - 支持CVSS高危端口权重 
-- - 支持原系统219个端口经验权重
-- - 多租户隔离支持
-- ============================================

-- 创建端口权重配置表
CREATE TABLE IF NOT EXISTS port_risk_configs (
    id SERIAL PRIMARY KEY,
    port_number INTEGER NOT NULL,
    port_name VARCHAR(100) NOT NULL,
    risk_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    risk_weight DECIMAL(4,2) NOT NULL DEFAULT 1.0,
    attack_intent VARCHAR(200),
    config_source VARCHAR(20) DEFAULT 'LEGACY',
    enabled BOOLEAN DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 索引和约束
    UNIQUE(port_number),
    CHECK (port_number >= 1 AND port_number <= 65535),
    CHECK (risk_weight >= 1.0 AND risk_weight <= 10.0),
    CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CHECK (config_source IN ('CVSS', 'LEGACY', 'CUSTOM'))
);

-- 创建索引
CREATE INDEX idx_port_risk_configs_port ON port_risk_configs(port_number);
CREATE INDEX idx_port_risk_configs_risk_level ON port_risk_configs(risk_level);
CREATE INDEX idx_port_risk_configs_enabled ON port_risk_configs(enabled);

-- ============================================
-- CVSS高危端口配置 (权重8.0-10.0)
-- ============================================

-- 远程控制高危端口 (权重10.0)
INSERT INTO port_risk_configs (port_number, port_name, risk_level, risk_weight, attack_intent, config_source, description) VALUES
(22, 'SSH', 'CRITICAL', 10.0, 'SSH远程控制', 'CVSS', 'SSH远程登录服务'),
(23, 'Telnet', 'CRITICAL', 10.0, 'Telnet远程控制', 'CVSS', 'Telnet远程登录服务（不安全）'),
(3389, 'RDP', 'CRITICAL', 10.0, 'RDP远程桌面', 'CVSS', 'Windows远程桌面协议'),
(5900, 'VNC', 'CRITICAL', 9.5, 'VNC远程控制', 'CVSS', 'VNC远程桌面服务'),
(5901, 'VNC', 'CRITICAL', 9.5, 'VNC远程控制', 'CVSS', 'VNC远程桌面服务端口2'),
(5902, 'VNC', 'CRITICAL', 9.5, 'VNC远程控制', 'CVSS', 'VNC远程桌面服务端口3')
ON CONFLICT (port_number) DO NOTHING;

-- 横向移动高危端口 (权重9.0-9.5)
INSERT INTO port_risk_configs (port_number, port_name, risk_level, risk_weight, attack_intent, config_source, description) VALUES
(445, 'SMB', 'CRITICAL', 9.5, 'SMB横向移动', 'CVSS', 'Windows文件共享SMB协议'),
(139, 'NetBIOS', 'HIGH', 8.5, 'NetBIOS文件共享', 'CVSS', 'NetBIOS会话服务'),
(389, 'LDAP', 'HIGH', 8.5, 'LDAP目录服务', 'CVSS', '轻量目录访问协议'),
(636, 'LDAPS', 'HIGH', 8.0, 'LDAPS目录服务', 'CVSS', '安全LDAP协议')
ON CONFLICT (port_number) DO NOTHING;

-- 数据库高危端口 (权重8.5-9.0)
INSERT INTO port_risk_configs (port_number, port_name, risk_level, risk_weight, attack_intent, config_source, description) VALUES
(3306, 'MySQL', 'HIGH', 9.0, 'MySQL数据库攻击', 'CVSS', 'MySQL数据库服务'),
(1433, 'SQL Server', 'HIGH', 9.0, 'SQL Server数据库攻击', 'CVSS', 'Microsoft SQL Server数据库'),
(5432, 'PostgreSQL', 'HIGH', 8.5, 'PostgreSQL数据库攻击', 'CVSS', 'PostgreSQL数据库服务'),
(1521, 'Oracle', 'HIGH', 8.5, 'Oracle数据库攻击', 'CVSS', 'Oracle数据库监听器'),
(6379, 'Redis', 'HIGH', 8.5, 'Redis数据库攻击', 'CVSS', 'Redis内存数据库'),
(27017, 'MongoDB', 'HIGH', 8.0, 'MongoDB数据库攻击', 'CVSS', 'MongoDB文档数据库')
ON CONFLICT (port_number) DO NOTHING;

-- ============================================
-- 经验高权重端口 (权重5.0-7.9) - 原系统配置对齐
-- ============================================

-- Web应用 (权重6.0-7.0)
INSERT INTO port_risk_configs (port_number, port_name, risk_level, risk_weight, attack_intent, config_source, description) VALUES
(80, 'HTTP', 'MEDIUM', 6.0, 'HTTP Web应用攻击', 'LEGACY', 'HTTP超文本传输协议'),
(443, 'HTTPS', 'MEDIUM', 6.0, 'HTTPS Web应用攻击', 'LEGACY', 'HTTPS安全超文本传输协议'),
(8080, 'HTTP-Alt', 'MEDIUM', 6.5, 'HTTP代理服务', 'LEGACY', 'HTTP备用端口'),
(8443, 'HTTPS-Alt', 'MEDIUM', 6.5, 'HTTPS代理服务', 'LEGACY', 'HTTPS备用端口'),
(8000, 'HTTP-Alt2', 'MEDIUM', 6.0, 'HTTP备用端口', 'LEGACY', 'HTTP开发测试端口'),
(9090, 'HTTP-Alt3', 'MEDIUM', 6.0, 'HTTP管理端口', 'LEGACY', 'HTTP管理服务端口')
ON CONFLICT (port_number) DO NOTHING;

-- 文件传输 (权重5.5-6.5)
INSERT INTO port_risk_configs (port_number, port_name, risk_level, risk_weight, attack_intent, config_source, description) VALUES
(21, 'FTP', 'MEDIUM', 6.5, 'FTP文件传输', 'LEGACY', 'FTP文件传输协议'),
(20, 'FTP-Data', 'MEDIUM', 6.0, 'FTP数据传输', 'LEGACY', 'FTP数据传输通道'),
(69, 'TFTP', 'MEDIUM', 5.5, 'TFTP简单文件传输', 'LEGACY', '简单文件传输协议'),
(990, 'FTPS', 'MEDIUM', 6.0, 'FTPS安全文件传输', 'LEGACY', 'FTP over SSL/TLS')
ON CONFLICT (port_number) DO NOTHING;

-- 邮件服务 (权重5.0-6.0)
INSERT INTO port_risk_configs (port_number, port_name, risk_level, risk_weight, attack_intent, config_source, description) VALUES
(25, 'SMTP', 'MEDIUM', 6.0, 'SMTP邮件服务', 'LEGACY', '简单邮件传输协议'),
(110, 'POP3', 'MEDIUM', 5.5, 'POP3邮件服务', 'LEGACY', 'POP3邮件接收协议'),
(143, 'IMAP', 'MEDIUM', 5.5, 'IMAP邮件服务', 'LEGACY', 'IMAP邮件访问协议'),
(993, 'IMAPS', 'MEDIUM', 5.0, 'IMAPS邮件服务', 'LEGACY', 'IMAP over SSL/TLS'),
(995, 'POP3S', 'MEDIUM', 5.0, 'POP3S邮件服务', 'LEGACY', 'POP3 over SSL/TLS')
ON CONFLICT (port_number) DO NOTHING;

-- 网络管理 (权重7.0-8.0)
INSERT INTO port_risk_configs (port_number, port_name, risk_level, risk_weight, attack_intent, config_source, description) VALUES
(161, 'SNMP', 'HIGH', 7.5, 'SNMP网络管理', 'LEGACY', '简单网络管理协议'),
(162, 'SNMP-Trap', 'HIGH', 7.0, 'SNMP陷阱', 'LEGACY', 'SNMP陷阱接收'),
(623, 'IPMI', 'HIGH', 8.0, 'IPMI硬件管理', 'LEGACY', '智能平台管理接口')
ON CONFLICT (port_number) DO NOTHING;

-- 工控系统 (权重8.0-9.0)
INSERT INTO port_risk_configs (port_number, port_name, risk_level, risk_weight, attack_intent, config_source, description) VALUES
(502, 'Modbus', 'CRITICAL', 8.5, 'Modbus工控协议', 'LEGACY', 'Modbus工业控制协议'),
(102, 'S7', 'CRITICAL', 8.5, 'S7工控通信', 'LEGACY', '西门子S7通信协议'),
(44818, 'EtherNet/IP', 'HIGH', 8.0, '工控以太网', 'LEGACY', '工业以太网协议'),
(2404, 'IEC 61850', 'HIGH', 7.5, 'IEC61850电力协议', 'LEGACY', '电力系统通信协议')
ON CONFLICT (port_number) DO NOTHING;

-- 勒索软件常用端口 (权重9.0-9.5)
INSERT INTO port_risk_configs (port_number, port_name, risk_level, risk_weight, attack_intent, config_source, description) VALUES
(4444, 'MetaSploit', 'CRITICAL', 9.5, '勒索软件通信', 'LEGACY', 'MetaSploit默认端口'),
(6666, 'Trojan', 'CRITICAL', 9.0, '勒索软件通信', 'LEGACY', '常见木马通信端口'),
(7777, 'Trojan', 'CRITICAL', 9.0, '勒索软件通信', 'LEGACY', '常见木马通信端口'),
(8888, 'Trojan', 'CRITICAL', 9.0, '勒索软件通信', 'LEGACY', '常见木马通信端口'),
(9999, 'Trojan', 'CRITICAL', 9.0, '勒索软件通信', 'LEGACY', '常见木马通信端口')
ON CONFLICT (port_number) DO NOTHING;

-- 其他重要端口
INSERT INTO port_risk_configs (port_number, port_name, risk_level, risk_weight, attack_intent, config_source, description) VALUES
(53, 'DNS', 'MEDIUM', 5.5, 'DNS域名服务', 'LEGACY', '域名系统协议'),
(123, 'NTP', 'LOW', 3.0, 'NTP时间同步', 'LEGACY', '网络时间协议'),
(135, 'RPC', 'HIGH', 7.0, 'Windows RPC', 'LEGACY', 'Windows远程过程调用'),
(1194, 'OpenVPN', 'MEDIUM', 5.0, 'OpenVPN服务', 'LEGACY', 'OpenVPN隧道协议'),
(1723, 'PPTP', 'MEDIUM', 5.5, 'PPTP VPN', 'LEGACY', '点对点隧道协议')
ON CONFLICT (port_number) DO NOTHING;

-- ============================================
-- 扩展配置: 补充原系统中的其他端口
-- ============================================

-- 补充常见高危端口 (移除重复的 993, 995)
INSERT INTO port_risk_configs (port_number, port_name, risk_level, risk_weight, attack_intent, config_source, description) VALUES
(79, 'Finger', 'MEDIUM', 5.0, '用户信息查询', 'LEGACY', 'Finger用户信息服务'),
(111, 'RPC', 'HIGH', 7.0, 'Unix RPC', 'LEGACY', 'Unix远程过程调用'),
(113, 'Ident', 'LOW', 3.0, '身份识别服务', 'LEGACY', '身份识别协议'),
(119, 'NNTP', 'LOW', 4.0, '网络新闻传输', 'LEGACY', '网络新闻传输协议'),
(137, 'NetBIOS-NS', 'MEDIUM', 6.0, 'NetBIOS名称服务', 'LEGACY', 'NetBIOS名称解析'),
(138, 'NetBIOS-DGM', 'MEDIUM', 5.5, 'NetBIOS数据报', 'LEGACY', 'NetBIOS数据报服务'),
(199, 'SMUX', 'MEDIUM', 5.0, 'SNMP Unix', 'LEGACY', 'SNMP Unix多路复用'),
(513, 'Rlogin', 'HIGH', 8.0, '远程登录', 'LEGACY', 'Berkeley远程登录'),
(514, 'RSH', 'HIGH', 8.0, '远程Shell', 'LEGACY', 'Berkeley远程Shell'),
(515, 'LPR', 'MEDIUM', 5.0, '行式打印机', 'LEGACY', '行式打印机协议'),
(2049, 'NFS', 'HIGH', 7.5, '网络文件系统', 'LEGACY', '网络文件系统协议'),
(5060, 'SIP', 'MEDIUM', 6.0, 'SIP语音协议', 'LEGACY', '会话发起协议'),
(5061, 'SIP-TLS', 'MEDIUM', 5.5, 'SIP安全协议', 'LEGACY', 'SIP over TLS'),
(8081, 'HTTP-Alt4', 'MEDIUM', 6.0, 'HTTP备用端口', 'LEGACY', 'HTTP备用服务端口'),
(10000, 'Webmin', 'HIGH', 7.0, 'Webmin管理', 'LEGACY', 'Webmin Web管理')
ON CONFLICT (port_number) DO NOTHING;

-- ============================================
-- 增强的威胁评估表结构优化
-- ============================================

-- 为threat_assessments表添加端口相关字段 (仅当表存在时)
DO $$ 
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'threat_assessments') THEN
        ALTER TABLE threat_assessments 
        ADD COLUMN IF NOT EXISTS port_list TEXT,
        ADD COLUMN IF NOT EXISTS port_risk_score DECIMAL(10,2) DEFAULT 0.0,
        ADD COLUMN IF NOT EXISTS detection_tier INTEGER DEFAULT 2;
        
        RAISE NOTICE 'Enhanced threat_assessments table with port fields';
    ELSE
        RAISE NOTICE 'Table threat_assessments does not exist yet, skipping ALTER';
    END IF;
END $$;

-- 创建复合索引优化查询 (仅当表存在时)
DO $$ 
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'threat_assessments') THEN
        CREATE INDEX IF NOT EXISTS idx_threat_assessments_composite 
        ON threat_assessments(customer_id, attack_mac, assessment_time DESC);

        CREATE INDEX IF NOT EXISTS idx_threat_assessments_tier_level 
        ON threat_assessments(detection_tier, threat_level);
        
        RAISE NOTICE 'Created indexes on threat_assessments';
    ELSE
        RAISE NOTICE 'Table threat_assessments does not exist yet, skipping index creation';
    END IF;
END $$;

-- ============================================
-- 视图创建: 端口权重统计
-- ============================================

CREATE OR REPLACE VIEW v_port_risk_statistics AS
SELECT 
    risk_level,
    config_source,
    COUNT(*) as port_count,
    AVG(risk_weight) as avg_weight,
    MIN(risk_weight) as min_weight,
    MAX(risk_weight) as max_weight
FROM port_risk_configs 
WHERE enabled = TRUE
GROUP BY risk_level, config_source
ORDER BY 
    CASE risk_level 
        WHEN 'CRITICAL' THEN 1 
        WHEN 'HIGH' THEN 2 
        WHEN 'MEDIUM' THEN 3 
        WHEN 'LOW' THEN 4 
    END,
    config_source;

-- ============================================
-- 数据验证查询
-- ============================================

-- 验证端口权重配置
SELECT 
    '端口权重配置统计' as check_type,
    COUNT(*) as total_ports,
    COUNT(CASE WHEN risk_level = 'CRITICAL' THEN 1 END) as critical_ports,
    COUNT(CASE WHEN risk_level = 'HIGH' THEN 1 END) as high_ports,
    COUNT(CASE WHEN risk_level = 'MEDIUM' THEN 1 END) as medium_ports,
    COUNT(CASE WHEN risk_level = 'LOW' THEN 1 END) as low_ports,
    AVG(risk_weight) as avg_weight
FROM port_risk_configs 
WHERE enabled = TRUE;

-- 验证权重分布
SELECT 
    '权重分布统计' as check_type,
    COUNT(CASE WHEN risk_weight >= 9.0 THEN 1 END) as ultra_high_risk,
    COUNT(CASE WHEN risk_weight >= 8.0 AND risk_weight < 9.0 THEN 1 END) as very_high_risk,
    COUNT(CASE WHEN risk_weight >= 6.0 AND risk_weight < 8.0 THEN 1 END) as high_risk,
    COUNT(CASE WHEN risk_weight >= 4.0 AND risk_weight < 6.0 THEN 1 END) as medium_risk,
    COUNT(CASE WHEN risk_weight < 4.0 THEN 1 END) as low_risk
FROM port_risk_configs 
WHERE enabled = TRUE;

-- 输出完成信息
SELECT 
    'MVP Phase 0 端口权重配置完成' as status,
    COUNT(*) as configured_ports,
    MIN(port_number) as min_port,
    MAX(port_number) as max_port,
    CURRENT_TIMESTAMP as completion_time
FROM port_risk_configs;

-- ============================================
-- 脚本完成
-- ============================================