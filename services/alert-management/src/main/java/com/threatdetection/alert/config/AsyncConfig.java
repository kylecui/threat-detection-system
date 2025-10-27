package com.threatdetection.alert.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步处理配置
 * 用于告警通知的异步发送，避免阻塞主流程
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 配置异步任务执行器
     * 用于告警通知的异步发送
     */
    @Bean(name = "notificationTaskExecutor")
    public Executor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数：保持活跃的最小线程数
        executor.setCorePoolSize(4);
        
        // 最大线程数：允许的最大线程数
        executor.setMaxPoolSize(10);
        
        // 队列容量：当所有核心线程都在处理任务时，新任务会进入队列
        executor.setQueueCapacity(100);
        
        // 线程名前缀
        executor.setThreadNamePrefix("notification-async-");
        
        // 拒绝策略：队列满时由调用线程执行
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        
        // 等待时间（秒）
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }
}
