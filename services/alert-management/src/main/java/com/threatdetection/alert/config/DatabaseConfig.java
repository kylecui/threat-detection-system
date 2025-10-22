package com.threatdetection.alert.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;

import jakarta.servlet.Filter;

/**
 * 数据库配置
 */
@Configuration
@EnableJpaAuditing
public class DatabaseConfig {
    // JPA审计配置已通过注解启用
    // 显式启用 OpenEntityManagerInViewFilter
    @Bean
    public Filter openEntityManagerInViewFilter() {
        return new OpenEntityManagerInViewFilter();
    }
}