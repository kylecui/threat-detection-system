package com.threatdetection.assessment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Threat Assessment Service Application
 *
 * Advanced threat evaluation and risk assessment service that processes threat alerts
 * and provides comprehensive risk analysis, mitigation recommendations, and historical
 * trend analysis.
 */
@SpringBootApplication
@EnableKafka
@EnableCaching
@EnableAsync
public class ThreatAssessmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThreatAssessmentApplication.class, args);
    }
}