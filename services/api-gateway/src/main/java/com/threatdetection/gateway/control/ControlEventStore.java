package com.threatdetection.gateway.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public final class ControlEventStore {
    static final String TABLE = """
            CREATE TABLE IF NOT EXISTS mqtt_control_events (
              event_id VARCHAR(128) PRIMARY KEY,
              sequence_id BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
              event_type VARCHAR(32) NOT NULL,
              controller_id VARCHAR(64) NOT NULL,
              platform_id VARCHAR(64) NOT NULL,
              device_id VARCHAR(128) NOT NULL,
              dev_serial VARCHAR(50) NOT NULL,
              customer_id VARCHAR(100) NOT NULL,
              binding_id BIGINT NOT NULL,
              bound_at TIMESTAMPTZ NOT NULL,
              occurred_at TIMESTAMPTZ NOT NULL,
              received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
              data JSONB NOT NULL
            )
            """;
    static final String DEVICE_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_mqtt_control_event_device
              ON mqtt_control_events (UPPER(dev_serial), occurred_at DESC, event_id)
            """;
    static final String CURSOR_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_mqtt_control_event_cursor
              ON mqtt_control_events (customer_id, binding_id, UPPER(dev_serial), sequence_id DESC)
            """;
    static final String INSERT = """
            WITH candidates AS (
              SELECT id, customer_id, dev_serial, bind_time FROM device_customer_mapping
              WHERE UPPER(dev_serial)=UPPER(:serial) AND is_active=true AND unbind_time IS NULL
              LIMIT 2
            )
            INSERT INTO mqtt_control_events(event_id,event_type,controller_id,platform_id,device_id,
              dev_serial,customer_id,binding_id,bound_at,occurred_at,data)
            SELECT :id,:type,:controller,:platform,:device,dev_serial,customer_id,id,bind_time,:occurred,:data
            FROM candidates WHERE (SELECT COUNT(*) FROM candidates)=1 AND bind_time<=:occurred
            ON CONFLICT (event_id) DO NOTHING RETURNING customer_id, dev_serial, sequence_id
            """;
    private static final String LIST = """
            SELECT e.sequence_id,e.event_id,e.event_type,e.controller_id,e.platform_id,e.device_id,
              e.dev_serial,e.occurred_at,e.data FROM mqtt_control_events e
            JOIN device_customer_mapping d ON d.id=e.binding_id AND d.bind_time=e.bound_at
            WHERE e.customer_id=:customer AND UPPER(e.dev_serial)=UPPER(:serial)
              AND d.customer_id=e.customer_id AND UPPER(d.dev_serial)=UPPER(e.dev_serial)
              AND d.is_active=true AND d.unbind_time IS NULL
            """;
    private static final Set<String> TYPES = Set.of(
            "device.presence", "config.reported", "lock.changed", "task.terminal", "violation.created");

    private final DatabaseClient db;
    private final ObjectMapper json;
    private final Sinks.Many<Notification> notifications = Sinks.many().multicast().directBestEffort();

    public record Event(String eventId, String eventType, String controllerId, String platformId, String deviceId,
                        String devSerial, OffsetDateTime occurredAt, JsonNode data, String cursor) {}
    public record Page(List<Event> items, String nextCursor, boolean hasMore) {}
    private record Notification(String customer, String serial, String cursor) {}

    public ControlEventStore(DatabaseClient db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    Mono<Void> initialize() {
        return db.sql(TABLE).fetch().rowsUpdated()
                .then(db.sql(DEVICE_INDEX).fetch().rowsUpdated())
                .then(db.sql(CURSOR_INDEX).fetch().rowsUpdated()).then();
    }

    static Map<String, Object> decode(byte[] wire, String secret, String controller, String platform) throws Exception {
        var event = ControlEnvelope.decode(wire, secret);
        if (!Objects.equals(1, event.get("schema_version")) || !Objects.equals(controller, event.get("controller_id"))
                || !Objects.equals(platform, event.get("platform_id"))
                || !(event.get("event_id") instanceof String id) || !id.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                || !(event.get("event_type") instanceof String type) || !TYPES.contains(type)
                || !validIdentifier(event.get("device_id"), 128) || !validIdentifier(event.get("dev_serial"), 50)
                || !(event.get("occurred_at") instanceof String occurred) || !(event.get("data") instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Invalid control event");
        }
        OffsetDateTime.parse(occurred);
        return event;
    }

    private static boolean validIdentifier(Object value, int max) {
        return value instanceof String text && !text.isBlank() && text.length() <= max
                && text.chars().noneMatch(Character::isISOControl);
    }

    Mono<Void> append(Map<String, Object> event) {
        return db.sql(INSERT).bind("id", event.get("event_id")).bind("type", event.get("event_type"))
                .bind("controller", event.get("controller_id")).bind("platform", event.get("platform_id"))
                .bind("device", event.get("device_id")).bind("serial", event.get("dev_serial"))
                .bind("occurred", OffsetDateTime.parse((String) event.get("occurred_at")))
                .bind("data", Json.of(json.valueToTree(event.get("data")).toString()))
                .map((row, meta) -> new Notification(row.get("customer_id", String.class),
                        row.get("dev_serial", String.class), row.get("sequence_id", Long.class).toString()))
                .one().doOnNext(notifications::tryEmitNext).then();
    }

    Mono<Page> list(String customer, String serial, int limit, Long after) {
        var query = db.sql(LIST + (after == null ? " ORDER BY e.sequence_id DESC"
                : " AND e.sequence_id>:after ORDER BY e.sequence_id ASC") + " LIMIT :limit")
                .bind("customer", customer).bind("serial", serial).bind("limit", limit + 1);
        if (after != null) query = query.bind("after", after);
        return query.map((row, meta) -> new Event(
                        row.get("event_id", String.class), row.get("event_type", String.class),
                        row.get("controller_id", String.class), row.get("platform_id", String.class),
                        row.get("device_id", String.class), row.get("dev_serial", String.class),
                        row.get("occurred_at", OffsetDateTime.class), readData(row.get("data", Json.class)),
                        row.get("sequence_id", Long.class).toString()))
                .all().collectList().map(all -> {
                    List<Event> items = all.subList(0, Math.min(limit, all.size()));
                    String next = items.isEmpty() ? (after == null ? "0" : after.toString())
                            : items.get(after == null ? 0 : items.size() - 1).cursor();
                    return new Page(items, next, after != null && all.size() > limit);
                });
    }

    Flux<String> notifications(String customer, String serial) {
        return notifications.asFlux().filter(event -> event.customer().equals(customer)
                && event.serial().equalsIgnoreCase(serial)).map(Notification::cursor);
    }

    private JsonNode readData(Json data) {
        try {
            return json.readTree(data.asString());
        } catch (Exception error) {
            throw new IllegalStateException("Invalid persisted control event data", error);
        }
    }
}
