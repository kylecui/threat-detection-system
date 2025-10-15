package com.threatdetection.stream.model;

/**
 * MVP Phase 0: 端口风险配置模型
 * 
 * 实现与原系统对齐的端口权重配置:
 * - 50个高风险端口 (CVSS驱动)
 * - 169个经验权重端口 (原系统配置)
 * - 动态权重计算支持
 */
public class PortRiskConfig {
    
    /**
     * 端口号 (1-65535)
     */
    private int port;
    
    /**
     * 端口名称/服务描述
     */
    private String portName;
    
    /**
     * 风险等级 (HIGH/MEDIUM/LOW)
     */
    private String riskLevel;
    
    /**
     * 权重分数 (1.0-10.0)
     * - CVSS高危端口: 8.0-10.0  
     * - 经验高权重端口: 5.0-7.9
     * - 普通端口: 1.0-4.9
     */
    private double riskWeight;
    
    /**
     * 攻击意图分类
     * 用于AttackEvent.getAttackIntentByPort()映射
     */
    private String attackIntent;
    
    /**
     * 配置来源 (CVSS/LEGACY/CUSTOM)
     */
    private String configSource;
    
    /**
     * 是否启用该配置
     */
    private boolean enabled;
    
    /**
     * 备注信息
     */
    private String description;

    // 构造函数
    public PortRiskConfig() {}

    public PortRiskConfig(int port, String portName, String riskLevel, double riskWeight, 
                         String attackIntent, String configSource, boolean enabled, String description) {
        this.port = port;
        this.portName = portName;
        this.riskLevel = riskLevel;
        this.riskWeight = riskWeight;
        this.attackIntent = attackIntent;
        this.configSource = configSource;
        this.enabled = enabled;
        this.description = description;
    }

    // Getter 和 Setter 方法
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public String getPortName() { return portName; }
    public void setPortName(String portName) { this.portName = portName; }
    
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    
    public double getRiskWeight() { return riskWeight; }
    public void setRiskWeight(double riskWeight) { this.riskWeight = riskWeight; }
    
    public String getAttackIntent() { return attackIntent; }
    public void setAttackIntent(String attackIntent) { this.attackIntent = attackIntent; }
    
    public String getConfigSource() { return configSource; }
    public void setConfigSource(String configSource) { this.configSource = configSource; }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /**
     * 获取端口攻击意图描述
     * @param port 端口号
     * @return 攻击意图描述
     */
    public static String getAttackIntentByPort(int port) {
        switch (port) {
            // 远程控制类
            case 22: return "SSH远程控制";
            case 23: return "Telnet远程控制";
            case 3389: return "RDP远程桌面";
            case 5900: case 5901: case 5902: return "VNC远程控制";
            
            // 文件共享/横向移动
            case 445: return "SMB文件共享/横向移动";
            case 139: return "NetBIOS文件共享";
            case 21: return "FTP文件传输";
            case 20: return "FTP数据传输";
            
            // 数据库攻击
            case 3306: return "MySQL数据库攻击";
            case 1433: return "SQL Server数据库攻击";
            case 5432: return "PostgreSQL数据库攻击";
            case 1521: return "Oracle数据库攻击";
            case 6379: return "Redis数据库攻击";
            case 27017: return "MongoDB数据库攻击";
            
            // Web应用攻击
            case 80: return "HTTP Web应用攻击";
            case 443: return "HTTPS Web应用攻击";
            case 8080: return "HTTP代理/Web应用";
            case 8443: return "HTTPS代理/Web应用";
            
            // 邮件服务
            case 25: return "SMTP邮件服务";
            case 110: return "POP3邮件服务";
            case 143: return "IMAP邮件服务";
            case 993: return "IMAPS邮件服务";
            case 995: return "POP3S邮件服务";
            
            // 网络基础设施
            case 53: return "DNS域名服务";
            case 161: case 162: return "SNMP网络管理";
            case 389: return "LDAP目录服务";
            case 636: return "LDAPS目录服务";
            
            // 勒索软件常用端口
            case 4444: case 6666: case 7777: case 8888: case 9999: return "勒索软件通信端口";
            
            // 工控系统
            case 102: return "S7工控通信";
            case 502: return "Modbus工控协议";
            case 44818: return "工控以太网";
            
            default:
                if (port >= 1 && port <= 1023) {
                    return "系统保留端口探测";
                } else if (port >= 1024 && port <= 49151) {
                    return "注册端口探测";
                } else {
                    return "动态端口探测";
                }
        }
    }
}