package com.threatdetection.gateway.control;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface DeviceControlPort {
    Map<String, Object> readiness();

    Mono<Map<String, Object>> submit(String customer, String serial, long actor, String action,
                                    String idempotencyKey, Map<String, Object> params);

    Mono<List<Map<String, Object>>> history(String customer, String serial);

    Mono<ControlEventStore.Page> events(String customer, String serial, int limit, Long after);

    Flux<String> eventNotifications(String customer, String serial);
}
