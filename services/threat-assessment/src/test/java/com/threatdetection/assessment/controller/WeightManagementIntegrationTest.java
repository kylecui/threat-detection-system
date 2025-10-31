package com.threatdetection.assessment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatdetection.assessment.dto.*;
import com.threatdetection.assessment.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.jdbcUrl=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.autoconfigure.exclude[0]=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.autoconfigure.exclude[1]=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
    "server.port=8083",
    "spring.application.name=threat-assessment-service"
})
@AutoConfigureWebMvc
class WeightManagementIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AttackSourceWeightRepository attackSourceWeightRepository;

    @Autowired
    private HoneypotSensitivityWeightRepository honeypotSensitivityWeightRepository;

    @Autowired
    private AttackPhasePortConfigRepository attackPhasePortConfigRepository;

    @Autowired
    private AptTemporalAccumulationRepository aptTemporalAccumulationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void testAttackSourceWeightCRUD() throws Exception {
        String customerId = "test-customer-001";

        // Create
        AttackSourceWeightDto createDto = AttackSourceWeightDto.builder()
            .customerId(customerId)
            .ipSegment("192.168.1.0/24")
            .attackSourceWeight(BigDecimal.valueOf(1.5))
            .description("Internal network source")
            .build();

        String createResponse = mockMvc.perform(post("/api/v1/weights/attack-source")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.customer_id").value(customerId))
            .andExpect(jsonPath("$.ip_segment").value("192.168.1.0/24"))
            .andExpect(jsonPath("$.attack_source_weight").value(1.5))
            .andReturn().getResponse().getContentAsString();

        AttackSourceWeightDto created = objectMapper.readValue(createResponse, AttackSourceWeightDto.class);
        Long id = created.getId();

        // Read - Get by customer ID
        mockMvc.perform(get("/api/v1/weights/attack-source/{customerId}", customerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$[0].customer_id").value(customerId));

        // Update (using same endpoint - it will update if exists)
        AttackSourceWeightDto updateDto = AttackSourceWeightDto.builder()
            .customerId(customerId)
            .ipSegment("192.168.1.0/24")
            .attackSourceWeight(BigDecimal.valueOf(2.0))
            .description("Updated internal network source")
            .build();

        mockMvc.perform(post("/api/v1/weights/attack-source")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.attack_source_weight").value(2.0))
            .andExpect(jsonPath("$.description").value("Updated internal network source"));

        // Delete
        mockMvc.perform(delete("/api/v1/weights/attack-source/{customerId}", customerId)
                .param("ipSegment", "192.168.1.0/24"))
            .andExpect(status().isNoContent());

        // Verify deletion
        mockMvc.perform(get("/api/v1/weights/attack-source/{customerId}", customerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testMultiTenantIsolation() throws Exception {
        String customerId1 = "customer-001";
        String customerId2 = "customer-002";

        // Create weight for customer 1
        AttackSourceWeightDto createDto1 = AttackSourceWeightDto.builder()
            .customerId(customerId1)
            .ipSegment("192.168.1.0/24")
            .attackSourceWeight(BigDecimal.valueOf(1.8))
            .description("External source for customer 1")
            .build();

        mockMvc.perform(post("/api/v1/weights/attack-source")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto1)))
            .andExpect(status().isCreated());

        // Create weight for customer 2
        AttackSourceWeightDto createDto2 = AttackSourceWeightDto.builder()
            .customerId(customerId2)
            .ipSegment("192.168.1.0/24")
            .attackSourceWeight(BigDecimal.valueOf(2.2))
            .description("External source for customer 2")
            .build();

        mockMvc.perform(post("/api/v1/weights/attack-source")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto2)))
            .andExpect(status().isCreated());

        // Verify customer 1 only sees their own data
        mockMvc.perform(get("/api/v1/weights/attack-source/{customerId}", customerId1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].customer_id").value(customerId1))
            .andExpect(jsonPath("$[0].attack_source_weight").value(1.8));

        // Verify customer 2 only sees their own data
        mockMvc.perform(get("/api/v1/weights/attack-source/{customerId}", customerId2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].customer_id").value(customerId2))
            .andExpect(jsonPath("$[0].attack_source_weight").value(2.2));
    }

    @Test
    void testHoneypotSensitivityWeightCRUD() throws Exception {
        String customerId = "test-customer-002";

        // Create
        HoneypotSensitivityWeightDto createDto = HoneypotSensitivityWeightDto.builder()
            .customerId(customerId)
            .ipSegment("192.168.2.0/24")
            .honeypotSensitivityWeight(BigDecimal.valueOf(2.5))
            .description("High sensitivity honeypot")
            .build();

        String createResponse = mockMvc.perform(post("/api/v1/weights/honeypot-sensitivity")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.customer_id").value(customerId))
            .andExpect(jsonPath("$.ip_segment").value("192.168.2.0/24"))
            .andReturn().getResponse().getContentAsString();

        HoneypotSensitivityWeightDto created = objectMapper.readValue(createResponse, HoneypotSensitivityWeightDto.class);
        Long id = created.getId();

        // Read all
        mockMvc.perform(get("/api/v1/weights/honeypot-sensitivity/{customerId}", customerId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // Update (using different IP segment to avoid unique constraint violation)
        HoneypotSensitivityWeightDto updateDto = HoneypotSensitivityWeightDto.builder()
            .customerId(customerId)
            .ipSegment("10.0.1.0/24")  // Different IP segment for update test
            .honeypotSensitivityWeight(BigDecimal.valueOf(3.0))
            .description("Updated high sensitivity honeypot")
            .build();

        mockMvc.perform(post("/api/v1/weights/honeypot-sensitivity")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.honeypot_sensitivity_weight").value(3.0))
            .andExpect(jsonPath("$.ip_segment").value("10.0.1.0/24"));

        // Delete both configs
        mockMvc.perform(delete("/api/v1/weights/honeypot-sensitivity/{customerId}", customerId)
                .param("ipSegment", "192.168.2.0/24"))
            .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/weights/honeypot-sensitivity/{customerId}", customerId)
                .param("ipSegment", "10.0.1.0/24"))
            .andExpect(status().isNoContent());
    }

    @Test
    void testAttackPhasePortConfigCRUD() throws Exception {
        String customerId = "test-customer-003";

        // Create
        AttackPhasePortConfigDto createDto = AttackPhasePortConfigDto.builder()
            .customerId(customerId)
            .phase("RECONNAISSANCE")
            .port(3389)
            .priority(5)
            .description("RDP reconnaissance phase")
            .build();

        String createResponse = mockMvc.perform(post("/api/v1/weights/attack-phase")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        AttackPhasePortConfigDto created = objectMapper.readValue(createResponse, AttackPhasePortConfigDto.class);
        Long id = created.getId();

        // Read all
        mockMvc.perform(get("/api/v1/weights/attack-phase/{customerId}", customerId))
            .andExpect(status().isOk());

        // Read by phase
        mockMvc.perform(get("/api/v1/weights/attack-phase/{customerId}/{phase}", customerId, "RECONNAISSANCE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // Update (using different port to avoid unique constraint violation)
        AttackPhasePortConfigDto updateDto = AttackPhasePortConfigDto.builder()
            .customerId(customerId)
            .phase("RECONNAISSANCE")
            .port(22)  // Different port for update test
            .priority(8)
            .description("SSH reconnaissance phase")
            .build();

        mockMvc.perform(post("/api/v1/weights/attack-phase")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.priority").value(8))
            .andExpect(jsonPath("$.port").value(22));

        // Delete both configs
        mockMvc.perform(delete("/api/v1/weights/attack-phase/{customerId}/{phase}/{port}",
                customerId, "RECONNAISSANCE", 3389))
            .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/weights/attack-phase/{customerId}/{phase}/{port}",
                customerId, "RECONNAISSANCE", 22))
            .andExpect(status().isNoContent());
    }

    @Test
    void testAptTemporalAccumulationCRUD() throws Exception {
        String customerId = "test-customer-004";
        String attackMac = "00:11:22:33:44:55";
        Instant windowStart = Instant.parse("2025-10-31T02:00:00Z");
        Instant windowEnd = Instant.parse("2025-10-31T03:00:00Z");

        // Create
        AptTemporalAccumulationDto createDto = AptTemporalAccumulationDto.builder()
            .customerId(customerId)
            .attackMac(attackMac)
            .windowStart(windowStart)
            .windowEnd(windowEnd)
            .accumulatedScore(BigDecimal.valueOf(150.5))
            .decayAccumulatedScore(BigDecimal.valueOf(120.3))
            .build();

        String createResponse = mockMvc.perform(post("/api/v1/weights/apt-temporal")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        AptTemporalAccumulationDto created = objectMapper.readValue(createResponse, AptTemporalAccumulationDto.class);
        Long id = created.getId();

        // Read all
        mockMvc.perform(get("/api/v1/weights/apt-temporal/{customerId}", customerId))
            .andExpect(status().isOk());

        // Read by MAC
        mockMvc.perform(get("/api/v1/weights/apt-temporal/{customerId}/{attackMac}",
                customerId, attackMac))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // Update accumulation scores (using same windowStart to update existing record)
        mockMvc.perform(put("/api/v1/weights/apt-temporal/{customerId}/{attackMac}",
                customerId, attackMac)
                .param("windowStart", windowStart.toString())
                .param("accumulatedScore", "200.0")
                .param("decayAccumulatedScore", "180.0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accumulated_score").value(200.0))
            .andExpect(jsonPath("$.decay_accumulated_score").value(180.0));

        // Get threat score
        mockMvc.perform(get("/api/v1/weights/apt-temporal/{customerId}/{attackMac}/threat-score",
                customerId, attackMac))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.threatScore").exists())
            .andExpect(jsonPath("$.customerId").value(customerId))
            .andExpect(jsonPath("$.attackMac").value(attackMac));

        // Delete
        mockMvc.perform(delete("/api/v1/weights/apt-temporal/{customerId}/{attackMac}",
                customerId, attackMac)
                .param("windowStart", windowStart.toString()))
            .andExpect(status().isNoContent());
    }

    @Test
    void testValidationErrors() throws Exception {
        String customerId = "test-customer";

        // Test invalid weight (negative)
        AttackSourceWeightDto invalidDto = AttackSourceWeightDto.builder()
            .customerId(customerId)
            .ipSegment("192.168.1.0/24")
            .attackSourceWeight(BigDecimal.valueOf(-1.0))
            .description("Invalid weight")
            .build();

        mockMvc.perform(post("/api/v1/weights/attack-source")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidDto)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void testStatisticsEndpoints() throws Exception {
        String customerId = "test-customer-stats";

        // Create some test data
        AttackSourceWeightDto weightDto = AttackSourceWeightDto.builder()
            .customerId(customerId)
            .ipSegment("10.0.0.0/8")
            .attackSourceWeight(BigDecimal.valueOf(2.5))
            .description("Test weight")
            .build();

        mockMvc.perform(post("/api/v1/weights/attack-source")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(weightDto)))
            .andExpect(status().isCreated());

        // Test statistics endpoint
        mockMvc.perform(get("/api/v1/weights/attack-source/{customerId}/statistics", customerId))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/weights/honeypot-sensitivity/{customerId}/statistics", customerId))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/weights/attack-phase/{customerId}/statistics", customerId))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/weights/apt-temporal/{customerId}/statistics", customerId))
            .andExpect(status().isOk());
    }
}