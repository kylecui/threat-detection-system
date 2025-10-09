package com.threatdetection.ingestion;

import com.threatdetection.ingestion.controller.LogIngestionController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class LogIngestionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    public void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/logs/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Log Ingestion Service is healthy"));
    }
}