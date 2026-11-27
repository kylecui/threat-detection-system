package com.threatdetection.gateway.control;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

@Component
@Slf4j
final class HiveMqControlTransport implements MqttControlTransport {
    @Value("${device-control.broker-url:}") private String brokerUrl;
    @Value("${device-control.controller-id:}") private String controllerId;
    @Value("${device-control.platform-id:}") private String platformId;
    @Value("${device-control.username:}") private String username;
    @Value("${device-control.password:}") private String password;
    @Value("${device-control.key-store:}") private String keyStore;
    @Value("${device-control.key-store-password:}") private String keyPassword;
    @Value("${device-control.trust-store:}") private String trustStore;
    @Value("${device-control.trust-store-password:}") private String trustPassword;
    private final AtomicLong connection = new AtomicLong();
    // Serializes event commits (and therefore the incremental cursor) off the MQTT I/O thread.
    private final ExecutorService deliveries = Executors.newSingleThreadExecutor(task -> {
        Thread worker = new Thread(task, "mqtt-control-delivery");
        worker.setDaemon(true);
        return worker;
    });
    private volatile Mqtt3AsyncClient client;
    private volatile boolean ready;
    private volatile boolean stopped;

    @Override
    public void start(Function<byte[], Mono<Void>> results, Function<byte[], Mono<Void>> events, Runnable onReady) {
        try {
            URI uri = URI.create(brokerUrl);
            if (!"ssl".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || !uri.getPath().isEmpty()
                    || !controllerId.matches("[A-Za-z0-9_-]{1,64}") || !platformId.matches("[A-Za-z0-9_-]{1,64}")
                    || !username.startsWith("control-") || password.isBlank()) {
                throw new IllegalArgumentException("Invalid control broker configuration");
            }
            KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(load(keyStore, keyPassword), keyPassword.toCharArray());
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(load(trustStore, trustPassword));
            client = MqttClient.builder().useMqttVersion3().identifier("control-" + platformId)
                    .serverHost(uri.getHost()).serverPort(uri.getPort() > 0 ? uri.getPort() : 8883)
                    // HiveMQ's default hostname verifier is kept alongside the PKCS12 trust chain.
                    .sslConfig().keyManagerFactory(keys).trustManagerFactory(trust).applySslConfig()
                    .automaticReconnectWithDefaultConfig()
                    .addDisconnectedListener(context -> {
                        connection.incrementAndGet();
                        ready = false;
                        if (stopped) context.getReconnector().reconnect(false);
                    })
                    .addConnectedListener(context -> subscribe(onReady))
                    .buildAsync();
            client.publishes(MqttGlobalPublishFilter.ALL, message -> {
                Function<byte[], Mono<Void>> handler = switch (message.getTopic().toString()) {
                    case String topic when topic.equals(topic("result")) -> results;
                    case String topic when topic.equals(topic("event")) -> events;
                    default -> null;
                };
                try {
                    deliver(message, handler);
                } catch (RuntimeException error) {
                    log.warn("MQTT control delivery could not be persisted");
                }
            }, deliveries, true);
            client.connectWith().cleanSession(false).keepAlive(30).simpleAuth().username(username)
                    .password(password.getBytes(StandardCharsets.UTF_8)).applySimpleAuth().send()
                    .whenComplete((ack, error) -> { if (error != null) log.warn("Control connection unavailable"); });
        } catch (Exception ignored) {
            log.error("Control configuration invalid; check server-side TLS and identity material");
        }
    }

    static void deliver(Mqtt3Publish message, Function<byte[], Mono<Void>> handler) {
        if (message.getQos() != MqttQos.AT_LEAST_ONCE) {
            if (message.getQos() != MqttQos.AT_MOST_ONCE) message.acknowledge();
            return;
        }
        if (!message.isRetain() && handler != null) {
            handler.apply(message.getPayloadAsBytes()).block(Duration.ofSeconds(15));
        }
        message.acknowledge();
    }

    private static KeyStore load(String path, String password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(Path.of(path))) {
            store.load(input, password.toCharArray());
        }
        return store;
    }

    private String topic(String suffix) {
        return "jz/control/v1/" + controllerId + "/" + platformId + "/" + suffix;
    }

    private void subscribe(Runnable onReady) {
        long generation = connection.incrementAndGet();
        var subscription = Mqtt3Subscribe.builder().topicFilter(topic("result")).qos(MqttQos.AT_LEAST_ONCE)
                .addSubscription().topicFilter(topic("event")).qos(MqttQos.AT_LEAST_ONCE)
                .applySubscription()
                .build();
        client.subscribe(subscription).whenComplete((ack, error) -> {
                    if (stopped || connection.get() != generation) return;
                    ready = error == null && ack.getReturnCodes().size() == 2
                            && ack.getReturnCodes().stream().allMatch(code -> code.getCode() == 1);
                    if (ready) onReady.run();
                    else log.warn("MQTT control result/event subscriptions require QoS 1");
                });
    }

    @Override public boolean ready() { return ready; }

    @Override
    public Mono<Void> publish(byte[] payload) {
        return Mono.defer(() -> {
            if (!ready) return Mono.error(new IllegalStateException("Control transport unavailable"));
            return Mono.fromFuture(client.publishWith().topic(topic("request")).qos(MqttQos.AT_LEAST_ONCE)
                    .retain(false).payload(payload).send()).timeout(Duration.ofSeconds(8)).then();
        });
    }

    @Override
    public void stop() {
        stopped = true;
        ready = false;
        if (client != null) client.disconnect();
        deliveries.shutdownNow();
    }
}
