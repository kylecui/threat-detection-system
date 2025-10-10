package com.threatdetection.alert.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 数据库配置
 */
@Configuration
@EnableJpaAuditing
public class DatabaseConfig {
    // JPA审计配置已通过注解启用
    // 如需要额外的数据库配置，可以在这里添加
}