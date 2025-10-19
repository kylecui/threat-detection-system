package com.threatdetection.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

/**
 * CORS跨域配置
 * 
 * <p>允许前端应用跨域访问API Gateway
 * 
 * @author Threat Detection Team
 * @version 1.0
 */
@Configuration
public class CorsConfig {

    /**
     * 配置CORS策略
     * 
     * <p>生产环境应限制allowedOrigins为具体的前端域名
     * 
     * @return CorsWebFilter
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // 允许的来源 (开发环境允许所有，生产环境应指定具体域名)
        corsConfig.setAllowedOriginPatterns(Collections.singletonList("*"));
        
        // 允许的HTTP方法
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        
        // 允许的请求头
        corsConfig.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "Accept",
            "X-Requested-With",
            "X-Customer-Id"
        ));
        
        // 暴露的响应头
        corsConfig.setExposedHeaders(Arrays.asList(
            "X-Total-Count",
            "X-Page-Number",
            "X-Page-Size"
        ));
        
        // 允许发送Cookie
        corsConfig.setAllowCredentials(true);
        
        // 预检请求缓存时间（秒）
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
