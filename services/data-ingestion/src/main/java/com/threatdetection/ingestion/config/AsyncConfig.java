package com.threatdetection.ingestion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** Bounded ingestion executor. CallerRunsPolicy provides natural backpressure. */
@Configuration
public class AsyncConfig {

    @Bean(name = "ingestionExecutor")
    public Executor ingestionExecutor(
            @Value("${ingestion.executor.core-size:8}") int coreSize,
            @Value("${ingestion.executor.max-size:16}") int maxSize,
            @Value("${ingestion.executor.queue-capacity:2000}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ingest-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
