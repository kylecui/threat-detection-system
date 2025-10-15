package com.threatdetection.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync // Phase 1A: 启用异步处理支持
@EnableKafka // 启用Kafka消费者监听器
public class DataIngestionApplication implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataIngestionApplication.class);
    
    private final ApplicationContext applicationContext;
    
    public DataIngestionApplication(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    public static void main(String[] args) {
        SpringApplication.run(DataIngestionApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("=== Application Started ===");
        logger.info("Checking Kafka-related beans...");
        
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            if (beanName.toLowerCase().contains("kafka") || 
                beanName.toLowerCase().contains("persistence")) {
                logger.info("Found bean: {}", beanName);
            }
        }
        
        logger.info("=== Bean check complete ===");
    }
}
