package com.threatdetection.gateway.control;

import com.threatdetection.gateway.auth.entity.AuthUser;
import com.threatdetection.gateway.auth.repository.AuthRoleRepository;
import com.threatdetection.gateway.auth.repository.AuthUserRepository;
import com.threatdetection.gateway.auth.service.AccountLifecycleService;
import com.threatdetection.gateway.auth.service.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.*;

/** Local Gateway controller: authenticates JWT itself, never trusts caller identity headers. */
@RestController
@RequestMapping("/api/v1/device-control/{customerId}/{devSerial}")
@RequiredArgsConstructor
public class DeviceControlController {
    private final DeviceControlPort control;
    private final JwtTokenProvider tokens;
    private final AuthUserRepository users;
    private final AuthRoleRepository roles;
    private final AccountLifecycleService lifecycle;
    private final DatabaseClient db;

    public record Operation(String action, String idempotencyKey, Map<String, Object> params) { }
    record AuthorizedDevice(AuthUser user, String serial) { }

    @GetMapping("/readiness")
    public Mono<Map<String, Object>> readiness(@PathVariable String customerId, @PathVariable String devSerial,
                                             @RequestHeader(value="Authorization", required=false) String authorization) {
        return authorize(authorization, customerId, devSerial, "capabilities").map(user -> control.readiness());
    }

    @GetMapping("/requests")
    public Mono<List<Map<String, Object>>> history(@PathVariable String customerId, @PathVariable String devSerial,
                                                 @RequestHeader(value="Authorization", required=false) String authorization) {
        return authorize(authorization, customerId, devSerial, "task.get")
                .flatMap(device -> control.history(customerId, device.serial()));
    }

    @GetMapping("/events")
    public Mono<ControlEventStore.Page> events(@PathVariable String customerId, @PathVariable String devSerial,
            @RequestHeader(value="Authorization", required=false) String authorization,
            @RequestParam(name="limit", defaultValue="50") int limit,
            @RequestParam(name="after", required=false) Long after) {
        return authorize(authorization, customerId, devSerial, "device.status").flatMap(device -> {
            if (limit < 1 || limit > 100 || (after != null && after < 0)) {
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event pagination"));
            }
            return control.events(customerId, device.serial(), limit, after);
        });
    }

    @GetMapping(value="/events/stream", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<ServerSentEvent<Map<String, String>>>>> stream(
            @PathVariable String customerId, @PathVariable String devSerial,
            @RequestHeader(value="Authorization", required=false) String authorization) {
        return authorize(authorization, customerId, devSerial, "device.status").map(device -> {
            Flux<String> heartbeats = Flux.interval(Duration.ofSeconds(10)).map(tick -> "");
            var body = Flux.merge(control.eventNotifications(customerId, device.serial()), heartbeats)
                    .onBackpressureLatest()
                    .concatMap(cursor -> authorize(authorization, customerId, devSerial, "device.status")
                            .map(allowed -> cursor.isEmpty()
                                    ? ServerSentEvent.<Map<String, String>>builder().comment("heartbeat").build()
                                    : ServerSentEvent.builder(Map.of("cursor", cursor)).id(cursor).event("control-event").build()))
                    .take(Duration.ofMinutes(5))
                    .onErrorResume(error -> Flux.empty());
            return ResponseEntity.ok().header("Cache-Control", "no-store")
                    .header("X-Accel-Buffering", "no").contentType(MediaType.TEXT_EVENT_STREAM).body(body);
        });
    }

    @PostMapping("/requests")
    public Mono<Map<String, Object>> submit(@PathVariable String customerId, @PathVariable String devSerial,
                                           @RequestHeader(value="Authorization", required=false) String authorization,
                                           @RequestBody Operation operation) {
        Map<String, Object> params = operation.params() == null ? Map.of() : operation.params();
        String invalid = ControlActions.validationError(operation.action(), params);
        if (invalid != null) return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid));
        return authorize(authorization, customerId, devSerial, operation.action())
                .flatMap(device -> control.submit(customerId, device.serial(), device.user().getId(),
                        operation.action(), operation.idempotencyKey(), params));
    }

    Mono<AuthorizedDevice> authorize(String authorization, String customerId, String serial, String action) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        try {
            var claims = tokens.parseToken(authorization.substring(7));
            if (!(claims.get("userId") instanceof Number id) || "refresh".equals(claims.get("type")))
                return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
            return users.findById(id.longValue()).filter(user -> user.getUsername().equals(claims.getSubject()))
                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED)))
                    .flatMap(user -> lifecycle.isOperational(user).filter(Boolean.TRUE::equals)
                            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN)))
                            .then(roles.findRolesByUserId(user.getId()).map(role -> role.getName()).collectList())
                            .flatMap(currentRoles -> {
                                boolean root = currentRoles.contains("SUPER_ADMIN");
                                boolean tenant = currentRoles.contains("TENANT_ADMIN");
                                boolean customerAdmin = currentRoles.contains("CUSTOMER_ADMIN");
                                boolean reader = currentRoles.contains("CUSTOMER_USER");
                                if ((!root && !tenant && !customerAdmin && !reader)
                                        || (!ControlActions.READ.contains(action) && !root && !tenant && !customerAdmin)
                                        || ("channel.repair".equals(action) && !root && !tenant)
                                        || (!root && !tenant && !Objects.equals(customerId, user.getCustomerId())))
                                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN));
                                String scope = root ? "" : " AND c.tenant_id=:tenant";
                                if (!root && user.getTenantId() == null)
                                    return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN));
                                var query = db.sql("SELECT d.dev_serial FROM device_customer_mapping d JOIN customers c ON c.customer_id=d.customer_id " +
                                                "JOIN tenants t ON t.id=c.tenant_id WHERE d.customer_id=:customer AND UPPER(d.dev_serial)=UPPER(:serial) " +
                                                "AND d.is_active=true AND d.unbind_time IS NULL AND c.status='ACTIVE' AND t.status='ACTIVE' " +
                                                "AND NOT EXISTS (SELECT 1 FROM device_customer_mapping other WHERE other.id<>d.id " +
                                                "AND UPPER(other.dev_serial)=UPPER(d.dev_serial) AND other.is_active=true AND other.unbind_time IS NULL)" + scope)
                                        .bind("customer", customerId).bind("serial", serial);
                                if (!root) query = query.bind("tenant", user.getTenantId());
                                return query.map((row, metadata) -> row.get("dev_serial", String.class)).one()
                                        .onErrorMap(org.springframework.dao.IncorrectResultSizeDataAccessException.class,
                                                error -> new ResponseStatusException(HttpStatus.FORBIDDEN))
                                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN)))
                                        .map(boundSerial -> new AuthorizedDevice(user, boundSerial));
                            }));
        } catch (Exception error) { return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED)); }
    }
}
