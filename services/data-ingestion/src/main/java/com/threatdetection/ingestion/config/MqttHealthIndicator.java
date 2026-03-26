package com.threatdetection.ingestion.config;

import com.threatdetection.ingestion.service.MqttListenerService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class MqttHealthIndicator implements HealthIndicator {

    private final MqttListenerService mqttListenerService;

    public MqttHealthIndicator(MqttListenerService mqttListenerService) {
        this.mqttListenerService = mqttListenerService;
    }

    @Override
    public Health health() {
        boolean connected = mqttListenerService.isConnected();
        if (connected) {
            return Health.up()
                    .withDetail("brokerUrl", mqttListenerService.getBrokerUrl())
                    .withDetail("topicFilter", mqttListenerService.getTopicFilter())
                    .build();
        }

        return Health.down()
                .withDetail("brokerUrl", mqttListenerService.getBrokerUrl())
                .withDetail("topicFilter", mqttListenerService.getTopicFilter())
                .build();
    }
}
