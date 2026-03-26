package com.threatdetection.configserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerConfigTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void shouldServeSeededConfigurationFromTestRepo() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/data-ingestion/default", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("name")).isEqualTo("data-ingestion");

        List<String> profiles = (List<String>) body.get("profiles");
        assertThat(profiles).contains("default");

        List<Map<String, Object>> propertySources = (List<Map<String, Object>>) body.get("propertySources");
        assertThat(propertySources).isNotNull().isNotEmpty();

        boolean foundSeedProperties = propertySources.stream()
                .map(source -> (Map<String, Object>) source.get("source"))
                .filter(source -> source != null)
                .anyMatch(source ->
                        "jdbc:postgresql://localhost:5432/threat_detection".equals(source.get("sample.datasource.url"))
                                && Integer.valueOf(30).equals(source.get("sample.timeout-seconds"))
                                && Boolean.TRUE.equals(source.get("sample.feature.enabled")));

        assertThat(foundSeedProperties).isTrue();
    }
}
