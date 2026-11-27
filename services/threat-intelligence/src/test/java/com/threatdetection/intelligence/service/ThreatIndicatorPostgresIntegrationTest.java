package com.threatdetection.intelligence.service;

import com.threatdetection.intelligence.client.TireClient;
import com.threatdetection.intelligence.dto.BulkUpsertRequest;
import com.threatdetection.intelligence.dto.CreateIndicatorRequest;
import com.threatdetection.intelligence.dto.IndicatorResponse;
import com.threatdetection.intelligence.dto.UpdateIndicatorRequest;
import com.threatdetection.intelligence.model.IocType;
import com.threatdetection.intelligence.model.Severity;
import com.threatdetection.intelligence.model.ThreatIntelFeed;
import com.threatdetection.intelligence.repository.ThreatIndicatorRepository;
import com.threatdetection.intelligence.repository.ThreatIntelFeedRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ThreatIndicatorPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("threat_intelligence_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private ThreatIndicatorRepository indicatorRepository;

    @Autowired
    private ThreatIntelFeedRepository feedRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ThreatIndicatorService service;

    @BeforeEach
    void setUp() {
        service = new ThreatIndicatorService(indicatorRepository, feedRepository, mock(TireClient.class));
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_indicator_inet_gist
                ON threat_indicators USING GIST (ioc_inet inet_ops)
                WHERE ioc_inet IS NOT NULL
                """);
    }

    @Test
    void persistsNativeInetAcrossCreateUpdateAndBulkUpsertPaths() {
        IndicatorResponse created = service.createIndicator(request(
                "198.51.100.23", IocType.IP_V4, "manual", 55));
        indicatorRepository.flush();

        assertEquals("inet", columnType(created.getId()));
        assertEquals("gist", indexAccessMethod());
        assertEquals("198.51.100.23/32", storedInet(created.getId()));

        UpdateIndicatorRequest update = new UpdateIndicatorRequest();
        update.setIocType(IocType.CIDR);
        update.setIocValue("2001:db8:abcd::7/64");
        service.updateIndicator(created.getId(), update);
        indicatorRepository.flush();

        assertEquals("2001:db8:abcd::7/64", storedInet(created.getId()));

        BulkUpsertRequest bulk = new BulkUpsertRequest();
        bulk.setIndicators(List.of(
                request("2001:db8::1234", IocType.IP_V6, "abuseipdb", 70),
                request("203.0.113.128/25", IocType.CIDR, "otx", 80),
                request("example.test", IocType.DOMAIN, "otx", 40)));
        assertEquals(3, service.bulkUpsert(bulk));
        indicatorRepository.flush();

        assertEquals("2001:db8::1234/128", storedInet("2001:db8::1234", "abuseipdb"));
        assertEquals("203.0.113.128/25", storedInet("203.0.113.128/25", "otx"));
        assertNull(storedInet("example.test", "otx"));

        BulkUpsertRequest existing = new BulkUpsertRequest();
        existing.setIndicators(List.of(request(
                "203.0.113.128/25", IocType.CIDR, "otx", 90)));
        assertEquals(1, service.bulkUpsert(existing));
        indicatorRepository.flush();

        assertEquals("203.0.113.128/25", storedInet("203.0.113.128/25", "otx"));
        // Refreshing a remote subscription confirms source freshness; it is
        // not a new observed sighting. Only the explicit sighting endpoint
        // increments this counter.
        assertEquals(1, indicatorRepository
                .findByIocValueAndSourceName("203.0.113.128/25", "otx")
                .orElseThrow()
                .getSightingCount());
    }

    @Test
    void listsInternalFeedFirstThenExternalFeedsById() {
        ThreatIntelFeed firstExternal = feedRepository.save(feed("abuseipdb", "REST_API"));
        ThreatIntelFeed internal = feedRepository.save(feed("internal", "INTERNAL"));
        ThreatIntelFeed secondExternal = feedRepository.save(feed("otx", "REST_API"));
        feedRepository.flush();

        List<ThreatIntelFeed> ordered = service.listFeeds();

        assertEquals(internal.getId(), ordered.get(0).getId());
        List<Long> externalIds = new ArrayList<>(ordered.stream()
                .filter(feed -> !"INTERNAL".equalsIgnoreCase(feed.getFeedType()))
                .map(ThreatIntelFeed::getId)
                .toList());
        List<Long> expectedExternalIds = new ArrayList<>(List.of(firstExternal.getId(), secondExternal.getId()));
        expectedExternalIds.sort(Comparator.naturalOrder());
        assertEquals(expectedExternalIds, externalIds);
    }

    private CreateIndicatorRequest request(String value, IocType type, String source, int confidence) {
        CreateIndicatorRequest request = new CreateIndicatorRequest();
        request.setIocValue(value);
        request.setIocType(type);
        request.setSourceName(source);
        request.setSeverity(Severity.HIGH);
        request.setConfidence(confidence);
        return request;
    }

    private ThreatIntelFeed feed(String name, String type) {
        return ThreatIntelFeed.builder()
                .feedName(name)
                .feedType(type)
                .enabled(true)
                .build();
    }

    private String columnType(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT pg_typeof(ioc_inet)::text FROM threat_indicators WHERE id = ?",
                String.class,
                id);
    }

    private String storedInet(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT ioc_inet::text FROM threat_indicators WHERE id = ?",
                String.class,
                id);
    }

    private String storedInet(String value, String source) {
        return jdbcTemplate.queryForObject(
                "SELECT ioc_inet::text FROM threat_indicators WHERE ioc_value = ? AND source_name = ?",
                String.class,
                value,
                source);
    }

    private String indexAccessMethod() {
        return jdbcTemplate.queryForObject("""
                SELECT access_method.amname
                FROM pg_class index_class
                JOIN pg_am access_method ON access_method.oid = index_class.relam
                WHERE index_class.relname = 'idx_indicator_inet_gist'
                """, String.class);
    }
}
