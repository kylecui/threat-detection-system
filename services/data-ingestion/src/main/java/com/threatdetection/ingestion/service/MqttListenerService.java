package com.threatdetection.ingestion.service;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.threatdetection.ingestion.config.MqttProperties;
import com.threatdetection.ingestion.model.AttackEvent;
import com.threatdetection.ingestion.model.HeartbeatEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class MqttListenerService {

    private static final Logger logger = LoggerFactory.getLogger(MqttListenerService.class);

    private final MqttProperties mqttProperties;
    private final V2EventParserService v2EventParserService;
    private final KafkaProducerService kafkaProducerService;

    private volatile Mqtt3AsyncClient mqttClient;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public MqttListenerService(
            MqttProperties mqttProperties,
            V2EventParserService v2EventParserService,
            KafkaProducerService kafkaProducerService
    ) {
        this.mqttProperties = mqttProperties;
        this.v2EventParserService = v2EventParserService;
        this.kafkaProducerService = kafkaProducerService;
    }

    @PostConstruct
    public void init() {
        try {
            URI brokerUri = URI.create(mqttProperties.getBrokerUrl());
            int port = brokerUri.getPort() > 0 ? brokerUri.getPort() : 1883;
            String clientId = resolveClientId(mqttProperties.getClientId());

            mqttClient = MqttClient.builder()
                    .useMqttVersion3()
                    .identifier(clientId)
                    .serverHost(brokerUri.getHost())
                    .serverPort(port)
                    .buildAsync();

            logger.info("Connecting MQTT client: broker={}, clientId={}", mqttProperties.getBrokerUrl(), clientId);

            var connectBuilder = mqttClient.connectWith()
                    .cleanSession(mqttProperties.isCleanStart())
                    .keepAlive(mqttProperties.getKeepAliveSeconds());

            if (mqttProperties.getUsername() != null && !mqttProperties.getUsername().isBlank()) {
                byte[] password = mqttProperties.getPassword() == null
                        ? new byte[0]
                        : mqttProperties.getPassword().getBytes(StandardCharsets.UTF_8);
                connectBuilder = connectBuilder.simpleAuth()
                        .username(mqttProperties.getUsername())
                        .password(password)
                        .applySimpleAuth();
            }

            connectBuilder.send()
                    .whenComplete((connAck, connectEx) -> {
                        if (connectEx != null) {
                            connected.set(false);
                            logger.error("MQTT connection failed: broker={}", mqttProperties.getBrokerUrl(), connectEx);
                            return;
                        }

                        connected.set(true);
                        logger.info("MQTT connected successfully: broker={}", mqttProperties.getBrokerUrl());
                        subscribeToLogs();
                    });

        } catch (Exception e) {
            connected.set(false);
            logger.error("Failed to initialize MQTT listener", e);
        }
    }

    private void subscribeToLogs() {
        if (mqttClient == null) {
            logger.warn("MQTT client not initialized, skipping subscribe");
            return;
        }

        mqttClient.subscribeWith()
                .topicFilter(mqttProperties.getTopicFilter())
                .qos(toQos(mqttProperties.getQos()))
                .callback(publish -> {
                    String topic = publish.getTopic().toString();
                    String payload = new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8);

                    logger.info("MQTT message received: topic={}, payloadSize={}", topic, payload.length());

                    v2EventParserService.parseV2Event(topic, payload).ifPresent(event -> {
                        if (event instanceof AttackEvent attackEvent) {
                            kafkaProducerService.sendAttackEvent(attackEvent);
                        } else if (event instanceof HeartbeatEvent heartbeatEvent) {
                            logger.info(
                                    "Parsed heartbeat event, currently not forwarded to status-events: deviceId={}, totalGuards={}, onlineDevices={}",
                                    heartbeatEvent.getDeviceId(),
                                    heartbeatEvent.getTotalGuards(),
                                    heartbeatEvent.getOnlineDevices()
                            );
                        } else {
                            logger.debug("Unsupported parsed event type: {}", event.getClass().getName());
                        }
                    });
                })
                .send()
                .whenComplete((subAck, subscribeEx) -> {
                    if (subscribeEx != null) {
                        logger.error("MQTT subscribe failed: topicFilter={}", mqttProperties.getTopicFilter(), subscribeEx);
                        return;
                    }
                    logger.info("MQTT subscribed successfully: topicFilter={}, qos={}",
                            mqttProperties.getTopicFilter(), mqttProperties.getQos());
                });
    }

    private MqttQos toQos(int qos) {
        return switch (qos) {
            case 0 -> MqttQos.AT_MOST_ONCE;
            case 2 -> MqttQos.EXACTLY_ONCE;
            default -> MqttQos.AT_LEAST_ONCE;
        };
    }

    private String resolveClientId(String configuredClientId) {
        if (configuredClientId == null || configuredClientId.isBlank()) {
            return "data-ingestion-" + java.util.UUID.randomUUID();
        }
        return configuredClientId.replace("${random.uuid}", java.util.UUID.randomUUID().toString());
    }

    @PreDestroy
    public void shutdown() {
        if (mqttClient == null) {
            return;
        }

        try {
            mqttClient.disconnect()
                    .whenComplete((unused, disconnectEx) -> {
                        connected.set(false);
                        if (disconnectEx != null) {
                            logger.warn("MQTT disconnect completed with error", disconnectEx);
                            return;
                        }
                        logger.info("MQTT disconnected gracefully");
                    });
        } catch (Exception e) {
            connected.set(false);
            logger.warn("MQTT disconnect failed", e);
        }
    }

    public boolean isConnected() {
        return connected.get();
    }

    public String getBrokerUrl() {
        return mqttProperties.getBrokerUrl();
    }

    public String getTopicFilter() {
        return mqttProperties.getTopicFilter();
    }
}
