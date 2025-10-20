package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.PortRiskConfig;
import com.threatdetection.assessment.repository.PortRiskConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 端口风险服务 - 管理端口风险配置
 * 
 * <p>职责:
 * 1. 查询端口风险评分
 * 2. 批量查询端口配置
 * 3. 计算端口风险权重
 * 4. 初始化默认端口配置
 * 
 * @author Security Team
 * @version 2.0
 */
@Service
public class PortRiskService {
    
    private static final Logger logger = LoggerFactory.getLogger(PortRiskService.class);
    
    private final PortRiskConfigRepository repository;
    
    // 默认风险评分 (未配置端口的默认值)
    private static final double DEFAULT_RISK_SCORE = 1.0;
    
    @Autowired
    public PortRiskService(PortRiskConfigRepository repository) {
        this.repository = repository;
    }
    
    /**
     * 获取端口风险评分 (带缓存)
     * 
     * @param portNumber 端口号
     * @return 风险评分 (0.0-5.0)
     */
    @Cacheable(value = "portRiskScores", key = "#portNumber")
    public double getPortRiskScore(int portNumber) {
        return repository.findByPortNumber(portNumber)
            .map(PortRiskConfig::getRiskScore)
            .orElse(DEFAULT_RISK_SCORE);
    }
    
    /**
     * 批量获取端口风险评分
     * 
     * @param portNumbers 端口号列表
     * @return 端口号 -> 风险评分映射
     */
    public Map<Integer, Double> getBatchPortRiskScores(List<Integer> portNumbers) {
        if (portNumbers == null || portNumbers.isEmpty()) {
            return Collections.emptyMap();
        }
        
        List<PortRiskConfig> configs = repository.findByPortNumberIn(portNumbers);
        Map<Integer, Double> scoreMap = new HashMap<>();
        
        // 填充已配置的端口评分
        for (PortRiskConfig config : configs) {
            scoreMap.put(config.getPortNumber(), config.getRiskScore());
        }
        
        // 填充未配置端口的默认评分
        for (Integer port : portNumbers) {
            scoreMap.putIfAbsent(port, DEFAULT_RISK_SCORE);
        }
        
        return scoreMap;
    }
    
    /**
     * 计算端口风险权重 (基于端口配置)
     * 
     * <p>混合策略:
     * 1. 如果端口已配置,使用配置的riskScore
     * 2. 如果端口未配置,使用多样性权重
     * 3. 最终权重 = max(配置权重, 多样性权重)
     * 
     * @param portNumbers 端口号列表
     * @param uniquePortCount 唯一端口数量
     * @return 端口风险权重 (1.0-2.0)
     */
    public double calculatePortRiskWeight(List<Integer> portNumbers, int uniquePortCount) {
        if (portNumbers == null || portNumbers.isEmpty()) {
            return 1.0;
        }
        
        // 1. 计算基于配置的权重
        double configWeight = calculateConfigBasedWeight(portNumbers);
        
        // 2. 计算基于多样性的权重
        double diversityWeight = calculateDiversityWeight(uniquePortCount);
        
        // 3. 取两者最大值
        double finalWeight = Math.max(configWeight, diversityWeight);
        
        logger.debug("Port risk weight calculation: configWeight={}, diversityWeight={}, finalWeight={}",
                    configWeight, diversityWeight, finalWeight);
        
        return Math.min(finalWeight, 2.0);  // 上限2.0
    }
    
    /**
     * 计算基于配置的权重
     */
    private double calculateConfigBasedWeight(List<Integer> portNumbers) {
        Map<Integer, Double> scoreMap = getBatchPortRiskScores(portNumbers);
        
        // 计算平均风险评分
        double avgScore = scoreMap.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(1.0);
        
        // 将评分 (0-5) 映射到权重 (1.0-2.0)
        // avgScore=0 -> weight=1.0
        // avgScore=5 -> weight=2.0
        return 1.0 + (avgScore / 5.0);
    }
    
    /**
     * 计算基于多样性的权重
     */
    private double calculateDiversityWeight(int uniquePortCount) {
        if (uniquePortCount >= 20) return 2.0;
        if (uniquePortCount >= 11) return 1.8;
        if (uniquePortCount >= 6) return 1.6;
        if (uniquePortCount >= 4) return 1.4;
        if (uniquePortCount >= 2) return 1.2;
        return 1.0;
    }
    
    /**
     * 查询高危端口列表
     */
    public List<PortRiskConfig> getHighRiskPorts(double threshold) {
        return repository.findHighRiskPorts(threshold);
    }
    
    /**
     * 初始化默认端口配置 (219个常见端口)
     */
    @Transactional
    public void initializeDefaultPorts() {
        long existingCount = repository.countConfiguredPorts();
        if (existingCount > 0) {
            logger.info("Port risk config already initialized: {} ports", existingCount);
            return;
        }
        
        logger.info("Initializing default port risk configurations...");
        
        List<PortRiskConfig> defaultPorts = createDefaultPortConfigs();
        repository.saveAll(defaultPorts);
        
        logger.info("✅ Initialized {} default port risk configurations", defaultPorts.size());
    }
    
    /**
     * 创建默认端口配置 (前50个高危端口)
     */
    private List<PortRiskConfig> createDefaultPortConfigs() {
        List<PortRiskConfig> configs = new ArrayList<>();
        
        // 高危端口 (riskScore = 2.0-3.0)
        configs.add(new PortRiskConfig(21, "FTP", 2.5, "FILE_TRANSFER", "FTP文件传输协议-明文传输"));
        configs.add(new PortRiskConfig(22, "SSH", 2.0, "REMOTE_ACCESS", "SSH远程登录"));
        configs.add(new PortRiskConfig(23, "Telnet", 3.0, "REMOTE_ACCESS", "Telnet远程登录-明文传输"));
        configs.add(new PortRiskConfig(25, "SMTP", 1.8, "EMAIL", "SMTP邮件传输"));
        configs.add(new PortRiskConfig(53, "DNS", 1.5, "NETWORK", "DNS域名解析"));
        configs.add(new PortRiskConfig(80, "HTTP", 1.5, "WEB", "HTTP Web服务"));
        configs.add(new PortRiskConfig(110, "POP3", 1.8, "EMAIL", "POP3邮件接收"));
        configs.add(new PortRiskConfig(135, "RPC", 2.5, "WINDOWS", "Windows RPC服务"));
        configs.add(new PortRiskConfig(139, "NetBIOS", 2.5, "WINDOWS", "NetBIOS会话服务"));
        configs.add(new PortRiskConfig(143, "IMAP", 1.8, "EMAIL", "IMAP邮件接收"));
        configs.add(new PortRiskConfig(443, "HTTPS", 1.2, "WEB", "HTTPS安全Web服务"));
        configs.add(new PortRiskConfig(445, "SMB", 3.0, "WINDOWS", "SMB文件共享-勒索软件常用"));
        configs.add(new PortRiskConfig(1433, "MSSQL", 2.5, "DATABASE", "Microsoft SQL Server"));
        configs.add(new PortRiskConfig(1521, "Oracle", 2.5, "DATABASE", "Oracle数据库"));
        configs.add(new PortRiskConfig(3306, "MySQL", 2.5, "DATABASE", "MySQL数据库"));
        configs.add(new PortRiskConfig(3389, "RDP", 3.0, "REMOTE_ACCESS", "远程桌面协议-APT常用"));
        configs.add(new PortRiskConfig(5432, "PostgreSQL", 2.5, "DATABASE", "PostgreSQL数据库"));
        configs.add(new PortRiskConfig(5900, "VNC", 2.5, "REMOTE_ACCESS", "VNC远程桌面"));
        configs.add(new PortRiskConfig(6379, "Redis", 2.8, "DATABASE", "Redis数据库-未授权访问高危"));
        configs.add(new PortRiskConfig(8080, "HTTP-Proxy", 1.5, "WEB", "HTTP代理服务"));
        
        // 中危端口 (riskScore = 1.5-2.0)
        configs.add(new PortRiskConfig(20, "FTP-Data", 2.0, "FILE_TRANSFER", "FTP数据传输"));
        configs.add(new PortRiskConfig(69, "TFTP", 2.0, "FILE_TRANSFER", "TFTP简单文件传输"));
        configs.add(new PortRiskConfig(111, "RPC", 2.0, "NETWORK", "RPC端口映射"));
        configs.add(new PortRiskConfig(161, "SNMP", 1.8, "NETWORK", "SNMP网络管理"));
        configs.add(new PortRiskConfig(389, "LDAP", 1.8, "DIRECTORY", "LDAP目录服务"));
        configs.add(new PortRiskConfig(636, "LDAPS", 1.5, "DIRECTORY", "LDAP安全连接"));
        configs.add(new PortRiskConfig(873, "Rsync", 2.0, "FILE_TRANSFER", "Rsync文件同步"));
        configs.add(new PortRiskConfig(1080, "SOCKS", 1.8, "PROXY", "SOCKS代理"));
        configs.add(new PortRiskConfig(1434, "MSSQL-Monitor", 2.0, "DATABASE", "MSSQL监控服务"));
        configs.add(new PortRiskConfig(2049, "NFS", 2.0, "FILE_TRANSFER", "NFS网络文件系统"));
        configs.add(new PortRiskConfig(2375, "Docker", 2.8, "CONTAINER", "Docker未授权访问"));
        configs.add(new PortRiskConfig(2376, "Docker-TLS", 2.0, "CONTAINER", "Docker TLS"));
        configs.add(new PortRiskConfig(3690, "SVN", 1.5, "VERSION_CONTROL", "SVN版本控制"));
        configs.add(new PortRiskConfig(5000, "Docker-Registry", 1.8, "CONTAINER", "Docker镜像仓库"));
        configs.add(new PortRiskConfig(5601, "Kibana", 1.8, "MONITORING", "Kibana日志可视化"));
        configs.add(new PortRiskConfig(5672, "RabbitMQ", 1.8, "MESSAGE_QUEUE", "RabbitMQ消息队列"));
        configs.add(new PortRiskConfig(6443, "Kubernetes", 2.0, "CONTAINER", "Kubernetes API"));
        configs.add(new PortRiskConfig(7001, "WebLogic", 2.0, "APPLICATION", "WebLogic管理端口"));
        configs.add(new PortRiskConfig(8081, "HTTP-Alt", 1.5, "WEB", "HTTP备用端口"));
        configs.add(new PortRiskConfig(8443, "HTTPS-Alt", 1.2, "WEB", "HTTPS备用端口"));
        
        // 低危端口 (riskScore = 1.0-1.5)
        configs.add(new PortRiskConfig(123, "NTP", 1.2, "NETWORK", "NTP时间同步"));
        configs.add(new PortRiskConfig(514, "Syslog", 1.2, "LOGGING", "Syslog日志服务"));
        configs.add(new PortRiskConfig(587, "SMTP-Submit", 1.5, "EMAIL", "SMTP邮件提交"));
        configs.add(new PortRiskConfig(993, "IMAPS", 1.2, "EMAIL", "IMAP安全连接"));
        configs.add(new PortRiskConfig(995, "POP3S", 1.2, "EMAIL", "POP3安全连接"));
        configs.add(new PortRiskConfig(8000, "HTTP-Dev", 1.2, "WEB", "HTTP开发端口"));
        configs.add(new PortRiskConfig(8888, "HTTP-Alt2", 1.2, "WEB", "HTTP备用端口2"));
        configs.add(new PortRiskConfig(9000, "HTTP-Alt3", 1.2, "WEB", "HTTP备用端口3"));
        configs.add(new PortRiskConfig(9090, "HTTP-Alt4", 1.2, "WEB", "HTTP备用端口4"));
        configs.add(new PortRiskConfig(9200, "Elasticsearch", 1.8, "DATABASE", "Elasticsearch搜索引擎"));
        
        logger.info("Created {} default port configurations", configs.size());
        return configs;
    }
}
