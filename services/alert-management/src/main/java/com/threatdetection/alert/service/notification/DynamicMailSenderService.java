package com.threatdetection.alert.service.notification;

import com.threatdetection.alert.model.SmtpConfig;
import com.threatdetection.alert.repository.SmtpConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态邮件发送器服务
 * 从数据库读取SMTP配置，支持运行时动态更新配置
 */
@Service
public class DynamicMailSenderService {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamicMailSenderService.class);
    
    @Autowired
    private SmtpConfigRepository smtpConfigRepository;
    
    /**
     * 缓存的JavaMailSender实例
     * Key: SMTP配置ID
     */
    private final ConcurrentHashMap<Long, JavaMailSender> mailSenderCache = new ConcurrentHashMap<>();
    
    /**
     * 缓存的SMTP配置
     */
    private volatile SmtpConfig cachedDefaultConfig = null;
    
    /**
     * 缓存时间戳
     */
    private volatile long lastCacheUpdate = 0;
    
    /**
     * 缓存有效期（毫秒）- 5分钟
     */
    private static final long CACHE_VALIDITY_MS = 5 * 60 * 1000;
    
    /**
     * 获取默认的JavaMailSender
     * @return JavaMailSender实例
     */
    public JavaMailSender getDefaultMailSender() {
        SmtpConfig config = getDefaultSmtpConfig();
        
        if (config == null) {
            logger.error("No default SMTP configuration found in database");
            throw new IllegalStateException("SMTP configuration not found");
        }
        
        return getOrCreateMailSender(config);
    }
    
    /**
     * 获取指定配置名称的JavaMailSender
     * @param configName 配置名称
     * @return JavaMailSender实例
     */
    public JavaMailSender getMailSender(String configName) {
        Optional<SmtpConfig> configOpt = smtpConfigRepository.findByConfigName(configName);
        
        if (configOpt.isEmpty()) {
            logger.warn("SMTP config '{}' not found, using default", configName);
            return getDefaultMailSender();
        }
        
        return getOrCreateMailSender(configOpt.get());
    }
    
    /**
     * 发送邮件
     * @param fromAddress 发件人地址（如果为null则使用配置中的默认地址）
     * @param toAddress 收件人地址
     * @param subject 主题
     * @param content 内容
     */
    public void sendMail(String fromAddress, String toAddress, String subject, String content) {
        JavaMailSender mailSender = getDefaultMailSender();
        SmtpConfig config = getDefaultSmtpConfig();
        
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress != null ? fromAddress : config.getFromAddress());
        message.setTo(toAddress);
        message.setSubject(subject);
        message.setText(content);
        
        try {
            mailSender.send(message);
            logger.info("Email sent successfully to {} from {}", toAddress, 
                       fromAddress != null ? fromAddress : config.getFromAddress());
        } catch (Exception e) {
            logger.error("Failed to send email to {}: {}", toAddress, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 刷新配置缓存
     */
    public void refreshCache() {
        logger.info("Refreshing SMTP configuration cache");
        cachedDefaultConfig = null;
        mailSenderCache.clear();
        lastCacheUpdate = 0;
    }
    
    /**
     * 获取或创建JavaMailSender实例
     */
    private JavaMailSender getOrCreateMailSender(SmtpConfig config) {
        // 检查缓存
        JavaMailSender cached = mailSenderCache.get(config.getId());
        if (cached != null) {
            return cached;
        }
        
        // 创建新的JavaMailSender
        JavaMailSenderImpl mailSender = createMailSender(config);
        
        // 缓存
        mailSenderCache.put(config.getId(), mailSender);
        
        logger.info("Created new JavaMailSender for SMTP config: {} ({}:{})", 
                   config.getConfigName(), config.getHost(), config.getPort());
        
        return mailSender;
    }
    
    /**
     * 根据SmtpConfig创建JavaMailSenderImpl
     */
    private JavaMailSenderImpl createMailSender(SmtpConfig config) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        
        // 基础配置
        mailSender.setHost(config.getHost());
        mailSender.setPort(config.getPort());
        mailSender.setUsername(config.getUsername());
        mailSender.setPassword(config.getPassword());
        mailSender.setDefaultEncoding("UTF-8");
        
        // 设置邮件属性
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        
        // TLS/SSL配置
        if (Boolean.TRUE.equals(config.getEnableStarttls())) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "false");
        }
        
        if (Boolean.TRUE.equals(config.getEnableSsl())) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        
        if (Boolean.TRUE.equals(config.getEnableTls())) {
            props.put("mail.smtp.tls", "true");
        }
        
        // 超时配置
        props.put("mail.smtp.connectiontimeout", config.getConnectionTimeout().toString());
        props.put("mail.smtp.timeout", config.getTimeout().toString());
        props.put("mail.smtp.writetimeout", config.getWriteTimeout().toString());
        
        // 调试模式（可选）
        props.put("mail.debug", "false");
        
        logger.debug("Created JavaMailSender with config: host={}, port={}, TLS={}, SSL={}, STARTTLS={}",
                    config.getHost(), config.getPort(), config.getEnableTls(), 
                    config.getEnableSsl(), config.getEnableStarttls());
        
        return mailSender;
    }
    
    /**
     * 获取默认SMTP配置（带缓存）
     */
    private SmtpConfig getDefaultSmtpConfig() {
        long now = System.currentTimeMillis();
        
        // 检查缓存是否有效
        if (cachedDefaultConfig != null && (now - lastCacheUpdate) < CACHE_VALIDITY_MS) {
            return cachedDefaultConfig;
        }
        
        // 从数据库加载
        Optional<SmtpConfig> configOpt = smtpConfigRepository.findByIsDefaultTrue();
        
        if (configOpt.isEmpty()) {
            // 如果没有默认配置，尝试使用第一个激活的配置
            configOpt = smtpConfigRepository.findFirstByIsActiveTrue();
        }
        
        if (configOpt.isPresent()) {
            cachedDefaultConfig = configOpt.get();
            lastCacheUpdate = now;
            logger.info("Loaded default SMTP config from database: {} ({}:{})", 
                       cachedDefaultConfig.getConfigName(), 
                       cachedDefaultConfig.getHost(), 
                       cachedDefaultConfig.getPort());
        } else {
            logger.error("No active SMTP configuration found in database");
        }
        
        return cachedDefaultConfig;
    }
    
    /**
     * 测试SMTP连接
     * @param configId SMTP配置ID
     * @return 测试结果
     */
    public boolean testConnection(Long configId) {
        Optional<SmtpConfig> configOpt = smtpConfigRepository.findById(configId);
        
        if (configOpt.isEmpty()) {
            logger.error("SMTP config with ID {} not found", configId);
            return false;
        }
        
        SmtpConfig config = configOpt.get();
        JavaMailSenderImpl mailSender = createMailSender(config);
        
        try {
            mailSender.testConnection();
            logger.info("SMTP connection test successful for: {}", config.getConfigName());
            return true;
        } catch (Exception e) {
            logger.error("SMTP connection test failed for {}: {}", config.getConfigName(), e.getMessage());
            return false;
        }
    }
}
