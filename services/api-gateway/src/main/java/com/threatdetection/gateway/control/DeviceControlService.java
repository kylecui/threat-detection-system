package com.threatdetection.gateway.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.dao.DataAccessException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Independent control client. The ingestion MQTT/Kafka path is never reused. */
@Service
@Slf4j
public class DeviceControlService implements DeviceControlPort {
    static final String PENDING_PHASES = "('queued','broker_acked','accepted','publish_unknown','device_pending_confirm')";
    static final String PERSIST_RESULT_SQL = "UPDATE mqtt_control_requests SET phase=:phase, result_json=CAST(:result AS jsonb), " +
            "result_version=:version,updated_at=NOW(),reconcile_claim_token=NULL,reconcile_claim_until=NULL," +
            "next_reconcile_at=CASE WHEN :phase IN " + PENDING_PHASES + " THEN NOW()+INTERVAL '30 seconds' ELSE NULL END " +
            "WHERE request_id=:id AND customer_id=:customer AND (result_version<:version OR " +
            "(result_version=0 AND :version=0 AND (phase IN " + PENDING_PHASES + " OR phase=:phase)))";
    private final DatabaseClient db;
    private final ObjectMapper json;
    private final MqttControlTransport transport;
    private final ControlEventStore events;
    @Value("${device-control.enabled:false}") private boolean enabled;
    @Value("${device-control.initialize-schema:true}") private boolean initializeSchema;
    @Value("${device-control.controller-id:}") private String controllerId;
    @Value("${device-control.platform-id:}") private String platformId;
    @Value("${device-control.signing-secret:}") private String secret;
    private volatile boolean schemaReady;
    private volatile boolean stopped;
    private ScheduledExecutorService recovery;
    private final AtomicBoolean recovering = new AtomicBoolean();
    private final AtomicLong resultDecodeFailures = new AtomicLong();

    public DeviceControlService(DatabaseClient db, ObjectMapper json, MqttControlTransport transport, ControlEventStore events) {
        this.db = db;
        this.json = json;
        this.transport = transport;
        this.events = events;
    }

    @PostConstruct
    public void initialize() {
        if (!enabled) return;
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            log.error("Control requires a signing key of at least 32 bytes");
            return;
        }
        Mono<Void> schema = initializeSchema ? initializeJournal()
                : db.sql("SELECT result_version,next_reconcile_at FROM mqtt_control_requests LIMIT 0").fetch().all().then()
                .then(db.sql("SELECT sequence_id,binding_id,bound_at,data FROM mqtt_control_events LIMIT 0").fetch().all().then());
        schema.doOnSuccess(ignored -> startControl())
                .subscribe(ignored -> { }, ignored -> log.error("Control journal initialization failed; control is unavailable"));
    }

    private Mono<Void> initializeJournal() {
        return db.sql("CREATE TABLE IF NOT EXISTS mqtt_control_requests (request_id VARCHAR(36) PRIMARY KEY, " +
                "idempotency_key VARCHAR(128) NOT NULL, customer_id VARCHAR(100) NOT NULL, dev_serial VARCHAR(50) NOT NULL, " +
                "actor_id BIGINT NOT NULL, action VARCHAR(40) NOT NULL, fingerprint VARCHAR(64) NOT NULL, " +
                "phase VARCHAR(32) NOT NULL, result_json JSONB NOT NULL DEFAULT '{}'::jsonb, " +
                "created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), " +
                "UNIQUE(actor_id, idempotency_key))").fetch().rowsUpdated()
                .then(db.sql("ALTER TABLE mqtt_control_requests ADD COLUMN IF NOT EXISTS result_version BIGINT NOT NULL DEFAULT 0").fetch().rowsUpdated())
                .then(db.sql("CREATE INDEX IF NOT EXISTS idx_mqtt_control_device ON mqtt_control_requests(customer_id, dev_serial, created_at DESC)").fetch().rowsUpdated())
                .then(db.sql("ALTER TABLE mqtt_control_requests ADD COLUMN IF NOT EXISTS next_reconcile_at TIMESTAMPTZ DEFAULT NOW(), ADD COLUMN IF NOT EXISTS last_reconcile_at TIMESTAMPTZ, ADD COLUMN IF NOT EXISTS reconcile_attempts INTEGER NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS reconcile_failures INTEGER NOT NULL DEFAULT 0, ADD COLUMN IF NOT EXISTS reconcile_claim_token VARCHAR(36), ADD COLUMN IF NOT EXISTS reconcile_claim_until TIMESTAMPTZ, ADD COLUMN IF NOT EXISTS reconcile_query_id VARCHAR(36), ADD COLUMN IF NOT EXISTS reconcile_query_issued TIMESTAMPTZ, ADD COLUMN IF NOT EXISTS last_reconcile_error VARCHAR(64)").fetch().rowsUpdated())
                .then(db.sql("UPDATE mqtt_control_requests SET next_reconcile_at=NULL WHERE phase NOT IN " + PENDING_PHASES).fetch().rowsUpdated())
                .then(db.sql("UPDATE mqtt_control_requests SET next_reconcile_at=NOW() WHERE phase='device_pending_confirm' AND next_reconcile_at IS NULL").fetch().rowsUpdated())
                .then(db.sql("CREATE INDEX IF NOT EXISTS idx_control_reconcile_due_v11 ON mqtt_control_requests(next_reconcile_at,created_at,request_id) WHERE phase IN " + PENDING_PHASES).fetch().rowsUpdated())
                .then(events.initialize());
    }

    private synchronized void startControl() {
        if (stopped) return;
        schemaReady = true;
        recovery = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "mqtt-control-recovery");
            thread.setDaemon(true);
            return thread;
        });
        recovery.scheduleWithFixedDelay(() -> { if (transport.ready()) reconcilePending(); }, 30, 30, TimeUnit.SECONDS);
        transport.start(this::receive, this::receiveEvent, this::reconcilePending);
    }

    Mono<Void> receiveEvent(byte[] bytes) {
        Map<String, Object> event;
        try {
            event = ControlEventStore.decode(bytes, secret, controllerId, platformId);
        } catch (Exception ignored) {
            log.warn("Rejected unauthenticated or malformed control event");
            return Mono.empty();
        }
        return Mono.defer(() -> events.append(event));
    }

    Mono<Void> receive(byte[] bytes) {
        try {
            Map<String, Object> result = ControlEnvelope.decode(bytes, secret);
            if (!Objects.equals(platformId, result.get("platform_id")) || !Objects.equals(1, result.get("schema_version"))) {
                return Mono.empty();
            }
            String requestId = String.valueOf(result.get("request_id"));
            UUID.fromString(requestId);
            Mono<Void> persisted = persistResult(requestId, result).then();
            if ("task.get".equals(result.get("action")) && result.get("result") instanceof Map<?, ?> nested
                    && nested.get("request_id") instanceof String original) {
                UUID.fromString(original);
                @SuppressWarnings("unchecked") Map<String, Object> snapshot = (Map<String, Object>) nested;
                persisted = persisted.then(persistResult(original, snapshot).then());
            }
            return persisted;
        } catch (Exception ignored) {
            log.warn("Rejected unauthenticated or malformed control result");
            return Mono.empty();
        }
    }

    private Mono<Long> persistResult(String requestId, Map<String, Object> value) throws Exception {
        if (!(value.get("phase") instanceof String phase) || !phase.matches("[a-z_]{1,32}")) {
            throw new IllegalArgumentException("invalid_phase");
        }
        long version = resultVersion(value);
        return db.sql(PERSIST_RESULT_SQL)
                .bind("phase", phase).bind("result", json.writeValueAsString(value)).bind("version", version)
                .bind("id", requestId).bind("customer", String.valueOf(value.get("customer_id"))).fetch().rowsUpdated();
    }

    static long resultVersion(Map<String, Object> value) {
        Object version = value.getOrDefault("result_version", 0);
        if (!(version instanceof Integer || version instanceof Long) || ((Number) version).longValue() < 0)
            throw new IllegalArgumentException("invalid_result_version");
        return ((Number) version).longValue();
    }

    public Map<String, Object> readiness() {
        return Map.of("enabled", enabled, "ready", schemaReady && transport.ready(), "schemaVersion", 1, "actions", ControlActions.ALL, "resultDecodeFailures", resultDecodeFailures.get());
    }

    public Mono<Map<String, Object>> submit(String customer, String serial, long actor, String action,
                                            String idempotencyKey, Map<String, Object> params) {
        String invalid = ControlActions.validationError(action, params);
        if (invalid != null) return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid));
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128)
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid idempotency key"));
        if (!schemaReady || !transport.ready()) return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Control is not ready"));
        try {
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    json.writer().with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                            .writeValueAsBytes(Map.of("customer", customer, "serial", serial, "action", action, "params", params))));
            String id = UUID.randomUUID().toString();
            // Unique actor/key reservation occurs before publish; duplicate HTTP clicks cannot create two MQTT actions.
            return db.sql("INSERT INTO mqtt_control_requests(request_id,idempotency_key,customer_id,dev_serial,actor_id,action,fingerprint,phase) " +
                            "VALUES(:id,:key,:customer,:serial,:actor,:action,:fingerprint,'queued') ON CONFLICT(actor_id,idempotency_key) DO NOTHING")
                    .bind("id", id).bind("key", idempotencyKey).bind("customer", customer).bind("serial", serial)
                    .bind("actor", actor).bind("action", action).bind("fingerprint", fingerprint).fetch().rowsUpdated()
                    .flatMap(inserted -> db.sql("SELECT request_id,action,fingerprint,phase,result_version,created_at,updated_at,result_json::text AS result_json_text FROM mqtt_control_requests WHERE actor_id=:actor AND idempotency_key=:key")
                            .bind("actor", actor).bind("key", idempotencyKey).fetch().one().flatMap(existing -> {
                                if (!fingerprint.equals(existing.get("fingerprint"))) return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency content conflict"));
                                if (inserted == 0) return Mono.just(view(existing));
                                return publish(id, idempotencyKey, customer, serial, String.valueOf(actor), action, params)
                                        .thenReturn(Map.<String, Object>of("requestId", id, "phase", "broker_acked", "action", action))
                                        .onErrorResume(error -> db.sql("UPDATE mqtt_control_requests SET phase='publish_unknown',updated_at=NOW() WHERE request_id=:id AND phase='queued'")
                                                .bind("id", id).fetch().rowsUpdated().thenReturn(Map.<String, Object>of("requestId", id, "phase", "publish_unknown", "action", action)));
                            })).onErrorMap(DataAccessException.class, ignored -> new ResponseStatusException(
                                    HttpStatus.SERVICE_UNAVAILABLE, "control_database_unavailable"));
        } catch (Exception error) { return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid control payload")); }
    }

    private Mono<Void> publish(String id, String key, String customer, String serial, String actor, String action, Map<String, Object> params) {
        try {
            Instant now = Instant.now();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("schema_version", 1); payload.put("request_id", id); payload.put("idempotency_key", key);
            payload.put("controller_id", controllerId); payload.put("platform_id", platformId); payload.put("action", action);
            payload.put("customer_id", customer); payload.put("dev_serial", serial); payload.put("actor", actor);
            payload.put("params", params); payload.put("issued_at", now.toString()); payload.put("expires_at", now.plusSeconds(120).toString());
            byte[] wire = ControlEnvelope.encode(payload, secret);
            return transport.publish(wire).then(db.sql("UPDATE mqtt_control_requests SET phase='broker_acked',updated_at=NOW() WHERE request_id=:id AND phase='queued'")
                            .bind("id", id).fetch().rowsUpdated()).then();
        } catch (Exception error) { return Mono.error(error); }
    }

    static final String CLAIM_RECONCILE_SQL = "WITH due AS (SELECT r.request_id FROM mqtt_control_requests r " +
            "WHERE r.phase IN " + PENDING_PHASES + " AND r.next_reconcile_at<=NOW() " +
            "AND (r.reconcile_claim_until IS NULL OR r.reconcile_claim_until<=NOW()) AND EXISTS " +
            "(SELECT 1 FROM device_customer_mapping d WHERE d.customer_id=r.customer_id AND UPPER(d.dev_serial)=UPPER(r.dev_serial) " +
            "AND d.is_active=true AND d.unbind_time IS NULL) ORDER BY r.next_reconcile_at,r.created_at,r.request_id FOR UPDATE SKIP LOCKED LIMIT 1) " +
            "UPDATE mqtt_control_requests r SET reconcile_claim_token=:claim,reconcile_claim_until=NOW()+INTERVAL '30 seconds'," +
            "next_reconcile_at=NOW()+INTERVAL '30 seconds',last_reconcile_at=NOW(),reconcile_attempts=r.reconcile_attempts+1," +
            "reconcile_query_id=CASE WHEN r.reconcile_query_id IS NOT NULL AND r.reconcile_query_issued>NOW()-INTERVAL '120 seconds' THEN r.reconcile_query_id ELSE :query END," +
            "reconcile_query_issued=CASE WHEN r.reconcile_query_id IS NOT NULL AND r.reconcile_query_issued>NOW()-INTERVAL '120 seconds' THEN r.reconcile_query_issued ELSE NOW() END " +
            "FROM due WHERE r.request_id=due.request_id RETURNING r.request_id,r.customer_id,r.dev_serial,r.reconcile_query_id,r.reconcile_query_issued";

    Mono<Void> reconcileOne() {
        String claim = UUID.randomUUID().toString();
        return db.sql(CLAIM_RECONCILE_SQL).bind("claim",claim).bind("query",UUID.randomUUID().toString()).fetch().one()
            .flatMap(row -> db.sql("SELECT 1 AS authorized FROM mqtt_control_requests r WHERE r.request_id=:id AND r.reconcile_claim_token=:claim AND r.phase IN " + PENDING_PHASES + " AND EXISTS (SELECT 1 FROM device_customer_mapping d WHERE d.customer_id=r.customer_id AND UPPER(d.dev_serial)=UPPER(r.dev_serial) AND d.is_active=true AND d.unbind_time IS NULL)")
                .bind("id",row.get("request_id")).bind("claim",claim).fetch().one()
                .switchIfEmpty(Mono.error(new IllegalStateException("binding_or_phase_changed")))
                .flatMap(valid -> publishReconcile(row))
                .then(finishReconcile(row,claim,false))
                .onErrorResume(error -> finishReconcile(row,claim,true)));
    }

    static final String FINISH_RECONCILE_SQL = "UPDATE mqtt_control_requests SET reconcile_claim_token=NULL,reconcile_claim_until=NULL," +
            "next_reconcile_at=CASE WHEN phase IN " + PENDING_PHASES + " THEN NOW()+make_interval(secs => CASE WHEN :failed THEN LEAST(300,30*power(2,LEAST(reconcile_failures,4)))::integer ELSE 30 END) ELSE NULL END," +
            "reconcile_failures=CASE WHEN :failed THEN reconcile_failures+1 ELSE 0 END," +
            "last_reconcile_error=:error WHERE request_id=:id AND reconcile_claim_token=:claim";

    private Mono<Void> finishReconcile(Map<String,Object> row,String claim,boolean failed) {
        return db.sql(FINISH_RECONCILE_SQL)
            .bind("id",row.get("request_id")).bind("claim",claim).bind("failed",failed)
            .bind("error",failed?"query_publish_unconfirmed":"").fetch().rowsUpdated().then();
    }

    Mono<Void> publishReconcile(Map<String,Object> row) {
        try {
            Map<String,Object> payload = reconcileEnvelope(row);
            byte[] wire=ControlEnvelope.encode(payload,secret);
            return transport.publish(wire);
        } catch(Exception error) {return Mono.error(error);}
    }

    Map<String,Object> reconcileEnvelope(Map<String,Object> row) {
            Instant issued = Instant.parse(String.valueOf(row.get("reconcile_query_issued")));
            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("schema_version",1);payload.put("request_id",row.get("reconcile_query_id"));payload.put("idempotency_key",row.get("reconcile_query_id"));
            payload.put("controller_id",controllerId);payload.put("platform_id",platformId);payload.put("customer_id",String.valueOf(row.get("customer_id")));
            payload.put("dev_serial",String.valueOf(row.get("dev_serial")));payload.put("actor","system:reconcile");payload.put("action","task.get");
            payload.put("params",Map.of("request_id",row.get("request_id")));payload.put("issued_at",issued.toString());payload.put("expires_at",issued.plusSeconds(120).toString());
            return payload;
    }

    private void reconcilePending() {
        if (!recovering.compareAndSet(false,true)) return;
        reactor.core.publisher.Flux.range(0,100).concatMap(ignored -> reconcileOne())
            .doFinally(signal -> recovering.set(false))
            .subscribe(ignored -> {}, ignored -> log.warn("Control reconciliation unavailable"));
    }

    public Mono<List<Map<String, Object>>> history(String customer, String serial) {
        if (!schemaReady) return Mono.just(List.of());
        return db.sql("SELECT request_id,action,phase,result_version,result_json::text AS result_json_text,created_at,updated_at FROM mqtt_control_requests " +
                        "WHERE customer_id=:customer AND UPPER(dev_serial)=UPPER(:serial) ORDER BY created_at DESC, request_id DESC LIMIT 100")
                .bind("customer", customer).bind("serial", serial).fetch().all().map(this::view).collectList()
                .onErrorMap(DataAccessException.class, ignored -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "control_database_unavailable"));
    }

    Map<String, Object> view(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        Object raw = result.containsKey("result_json_text")
                ? result.remove("result_json_text") : result.remove("result_json");
        result.remove("result_json");
        result.remove("fingerprint");
        boolean waiting = Set.of("queued", "broker_acked", "accepted", "publish_unknown", "device_pending_confirm")
                .contains(String.valueOf(row.get("phase")));
        try {
            // Queries project JSONB to text. Native Json is supported explicitly for
            // the driver boundary; toString() is a debug representation, never JSON.
            String text;
            if (raw == null) text = null;
            else if (raw instanceof String value) text = value;
            else if (raw instanceof Json value) text = value.asString();
            else throw new IllegalArgumentException("unsupported_result_type");
            Object decoded = text == null ? null : json.readValue(text, Object.class);
            if (decoded == null && waiting) result.put("result", null);
            else if (decoded instanceof Map<?, ?> value && (waiting || !value.isEmpty())) {
                result.put("result", value);
            } else throw new IllegalArgumentException("result_object_required");
        } catch (Exception ignored) {
            resultDecodeFailures.incrementAndGet();
            result.put("result", null);
            result.put("result_decode_error", "result_decode_error");
            // Keep the business phase and request ID. Query the original task;
            // never suggest repeating the original device side effect.
            log.warn("Control result decode failed request_id={} action={} code=result_decode_error",
                    row.get("request_id"), row.get("action"));
        }
        return result;
    }

    @Override
    public Mono<ControlEventStore.Page> events(String customer, String serial, int limit, Long after) {
        if (!schemaReady) return Mono.just(new ControlEventStore.Page(List.of(), after == null ? "0" : after.toString(), false));
        return events.list(customer, serial, limit, after)
                .onErrorMap(DataAccessException.class, ignored -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "control_database_unavailable"));
    }

    @Override
    public Flux<String> eventNotifications(String customer, String serial) {
        return events.notifications(customer, serial);
    }

    @PreDestroy public synchronized void stop() {
        stopped = true;
        schemaReady = false;
        if (recovery != null) recovery.shutdownNow();
        transport.stop();
    }
}
