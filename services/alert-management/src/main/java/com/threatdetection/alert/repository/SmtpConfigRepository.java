package com.threatdetection.alert.repository;

import com.threatdetection.alert.model.SmtpConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * SMTP配置数据访问层
 */
@Repository
public interface SmtpConfigRepository extends JpaRepository<SmtpConfig, Long> {
    
    /**
     * 查找默认SMTP配置
     * @return 默认SMTP配置
     */
    Optional<SmtpConfig> findByIsDefaultTrue();
    
    /**
     * 根据配置名称查找
     * @param configName 配置名称
     * @return SMTP配置
     */
    Optional<SmtpConfig> findByConfigName(String configName);
    
    /**
     * 查找第一个激活的配置
     * @return SMTP配置
     */
    Optional<SmtpConfig> findFirstByIsActiveTrue();
}
