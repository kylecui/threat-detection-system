package com.threatdetection.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = DataIngestionApplication.class)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092",
    "logging.level.com.threatdetection.ingestion=DEBUG"
})
class DataIngestionServiceApplicationTests {

    @Test
    void contextLoads() {
        // Test that the Spring context loads successfully
    }
}