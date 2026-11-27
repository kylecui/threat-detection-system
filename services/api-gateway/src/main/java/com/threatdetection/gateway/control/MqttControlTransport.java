package com.threatdetection.gateway.control;

import reactor.core.publisher.Mono;

import java.util.function.Function;

interface MqttControlTransport {
    void start(Function<byte[], Mono<Void>> results, Function<byte[], Mono<Void>> events, Runnable onReady);
    boolean ready();
    Mono<Void> publish(byte[] payload);
    void stop();
}
