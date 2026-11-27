package com.threatdetection.gateway.control;

import com.threatdetection.gateway.auth.entity.AuthRole;
import com.threatdetection.gateway.auth.entity.AuthUser;
import com.threatdetection.gateway.auth.repository.AuthRoleRepository;
import com.threatdetection.gateway.auth.repository.AuthUserRepository;
import com.threatdetection.gateway.auth.service.AccountLifecycleService;
import com.threatdetection.gateway.auth.service.JwtTokenProvider;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceControlControllerTest {
    private final DeviceControlPort control = mock(DeviceControlPort.class);
    private final JwtTokenProvider tokens = mock(JwtTokenProvider.class);
    private final AuthUserRepository users = mock(AuthUserRepository.class);
    private final AuthRoleRepository roles = mock(AuthRoleRepository.class);
    private final AccountLifecycleService lifecycle = mock(AccountLifecycleService.class);
    private final DatabaseClient db = mock(DatabaseClient.class);
    private final DeviceControlController controller = new DeviceControlController(control, tokens, users, roles, lifecycle, db);
    private AuthUser user;

    @BeforeEach void setup() {
        var claims = Jwts.claims(); claims.setSubject("viewer"); claims.put("userId", 18L);
        when(tokens.parseToken("token")).thenReturn(claims);
        user = AuthUser.builder().id(18L).username("viewer").customerId("customer-a").tenantId(8L).enabled(true).build();
        when(users.findById(18L)).thenReturn(Mono.just(user));
        when(lifecycle.isOperational(user)).thenReturn(Mono.just(true));
        when(roles.findRolesByUserId(18L)).thenReturn(Flux.just(AuthRole.builder().name("CUSTOMER_USER").build()));
    }

    private void denied(Mono<?> value, HttpStatus status) {
        StepVerifier.create(value).expectErrorMatches(error -> error instanceof ResponseStatusException response
                && response.getStatusCode() == status).verify();
        verifyNoInteractions(control, db);
    }

    @Test void callerHeadersCannotReplaceBearerIdentity() { denied(controller.authorize(null, "customer-a", "SERIAL-A", "device.status"), HttpStatus.UNAUTHORIZED); }
    @Test void customerCannotReadAnotherCustomerDevice() { denied(controller.authorize("Bearer token", "customer-b", "SERIAL-B", "device.status"), HttpStatus.FORBIDDEN); }
    @Test void databaseViewerRoleCannotMutateEvenWithOldAdminToken() { denied(controller.authorize("Bearer token", "customer-a", "SERIAL-A", "config.push"), HttpStatus.FORBIDDEN); }
    @Test void disabledAccountIsDenied() {
        when(lifecycle.isOperational(user)).thenReturn(Mono.just(false));
        denied(controller.authorize("Bearer token", "customer-a", "SERIAL-A", "device.status"), HttpStatus.FORBIDDEN);
    }
    @Test void tenantAdminWithoutTenantCannotEnumerateOrControl() {
        user.setTenantId(null);
        when(roles.findRolesByUserId(18L)).thenReturn(Flux.just(AuthRole.builder().name("TENANT_ADMIN").build()));
        denied(controller.authorize("Bearer token", "customer-a", "SERIAL-A", "config.push"), HttpStatus.FORBIDDEN);
    }
    @Test void deletedAccountIsDenied() {
        when(users.findById(18L)).thenReturn(Mono.empty());
        denied(controller.authorize("Bearer token", "customer-a", "SERIAL-A", "device.status"), HttpStatus.UNAUTHORIZED);
    }

    @Test void customerAdminCannotRepair() {
        role("CUSTOMER_ADMIN");
        denied(controller.authorize("Bearer token", "customer-a", "serial-a", "channel.repair"), HttpStatus.FORBIDDEN);
    }

    @Test void genericCommandsCannotApproveContainmentAndReviewRequiresBooleanTrue() {
        for (var params : List.<Map<String, Object>>of(Map.of(), Map.of("human_confirmed", false), Map.of("human_confirmed", "true"))) {
            denied(controller.submit("customer-a", "SERIAL-A", "Bearer token",
                    new DeviceControlController.Operation("channel.reconcile.review", "key", params)), HttpStatus.BAD_REQUEST);
        }
        denied(controller.submit("customer-a", "SERIAL-A", "Bearer token",
                new DeviceControlController.Operation("cmd.send", "key", Map.of("cmd", " Containment_Approve "))), HttpStatus.BAD_REQUEST);
        denied(controller.submit("customer-a", "SERIAL-A", "Bearer token",
                new DeviceControlController.Operation("cmd.send", "key",
                        Map.of("payload", Map.of("cmd", "containment_approve")))), HttpStatus.BAD_REQUEST);
    }

    @Test void genericCommandsCannotPullContainmentCandidates() {
        for (var params : List.<Map<String, Object>>of(
                Map.of("cmd", " CONTAINMENT_CANDIDATES ", "params", Map.of()),
                Map.of("cmd", "diag", "params", Map.of("cmd", "containment_candidates")))) {
            StepVerifier.create(controller.submit("customer-a", "SERIAL-A", "Bearer token",
                            new DeviceControlController.Operation("cmd.send", "key", params)))
                    .expectErrorMatches(error -> error instanceof ResponseStatusException response
                            && response.getStatusCode() == HttpStatus.BAD_REQUEST
                            && response.getReason().contains("candidates.pull"))
                    .verify();
        }
        verifyNoInteractions(control, db);
    }

    @ParameterizedTest
    @CsvSource({
            "channel.diagnostics,CUSTOMER_USER", "policy.list,CUSTOMER_USER", "violation.list,CUSTOMER_USER",
            "cmd.send,CUSTOMER_ADMIN", "task.retry,CUSTOMER_ADMIN", "task.cancel,CUSTOMER_ADMIN",
            "channel.reconcile.start,CUSTOMER_ADMIN", "channel.reconcile.review,CUSTOMER_ADMIN",
            "policy.create,CUSTOMER_ADMIN", "policy.update,CUSTOMER_ADMIN", "policy.delete,CUSTOMER_ADMIN",
            "violation.acknowledge,CUSTOMER_ADMIN", "channel.repair,TENANT_ADMIN", "channel.repair,SUPER_ADMIN"
    })
    void newActionsUseCurrentRoleAndCanonicalBinding(String action, String role) {
        role(role);
        binding(Mono.just("Serial-A"));
        Map<String, Object> params = switch (action) {
            case "channel.reconcile.review" -> Map.of("human_confirmed", true);
            case "cmd.send" -> Map.of("cmd", "diag", "params", Map.of("detail", true));
            default -> Map.of();
        };
        when(control.submit("customer-a", "Serial-A", 18, action, "key", params)).thenReturn(Mono.just(Map.of("action", action)));
        StepVerifier.create(controller.submit("customer-a", "serial-a", "Bearer token",
                        new DeviceControlController.Operation(action, "key", params)))
                .expectNext(Map.of("action", action)).verifyComplete();
        verify(control).submit("customer-a", "Serial-A", 18, action, "key", params);
    }

    @Test void bindingRequiredEvenForRootAndCaseInsensitive() {
        role("SUPER_ADMIN");
        binding(Mono.empty());
        StepVerifier.create(controller.authorize("Bearer token", "customer-a", "serial-a", "channel.repair"))
                .expectErrorMatches(error -> error instanceof ResponseStatusException status
                        && status.getStatusCode() == HttpStatus.FORBIDDEN).verify();
        verifyNoInteractions(control);
    }

    @Test void eventHistoryIsScopedAndCursorIsValidated() {
        binding(Mono.just("Serial-A"));
        var page = new ControlEventStore.Page(List.of(), "5", false);
        when(control.events("customer-a", "Serial-A", 50, 5L)).thenReturn(Mono.just(page));
        StepVerifier.create(controller.events("customer-a", "serial-a", "Bearer token", 50, 5L)).expectNext(page).verifyComplete();
        StepVerifier.create(controller.events("customer-a", "serial-a", "Bearer token", 101, 5L))
                .expectErrorMatches(error -> error instanceof ResponseStatusException status
                        && status.getStatusCode() == HttpStatus.BAD_REQUEST).verify();
    }

    @Test void streamRequiresAuthorizationAndRechecksItBeforeDelivery() {
        binding(Mono.just("Serial-A"));
        when(control.eventNotifications("customer-a", "Serial-A")).thenReturn(Flux.just("42"));
        var response = controller.stream("customer-a", "serial-a", "Bearer token").block();
        assertNotNull(response);
        assertEquals("no", response.getHeaders().getFirst("X-Accel-Buffering"));
        StepVerifier.create(response.getBody().take(1)).assertNext(event -> {
            assertEquals("control-event", event.event());
            assertEquals("42", event.id());
        }).verifyComplete();
        when(lifecycle.isOperational(user)).thenReturn(Mono.just(false));
        StepVerifier.create(response.getBody()).verifyComplete();
    }

    @Test void eventApisDoNotAcceptTokensInQueryOrCallerIdentityHeaders() {
        var http = WebTestClient.bindToController(controller).build();
        for (var path : List.of("events", "events/stream")) {
            http.get().uri("/api/v1/device-control/customer-a/serial-a/" + path + "?token=token")
                    .header("X-Auth-User-Id", "18").exchange().expectStatus().isUnauthorized();
        }
        verifyNoInteractions(control, db);
    }

    @Test void viewerCannotUseNewWriteAction() {
        denied(controller.authorize("Bearer token", "customer-a", "serial-a", "violation.acknowledge"), HttpStatus.FORBIDDEN);
    }

    private void role(String name) {
        when(roles.findRolesByUserId(18L)).thenReturn(Flux.just(AuthRole.builder().name(name).build()));
    }

    @SuppressWarnings("unchecked")
    private void binding(Mono<String> serial) {
        var query = mock(DatabaseClient.GenericExecuteSpec.class);
        RowsFetchSpec<String> rows = mock(RowsFetchSpec.class);
        when(db.sql(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            assertTrue(sql.contains("UPPER(d.dev_serial)=UPPER(:serial)"));
            assertTrue(sql.contains("c.status='ACTIVE'") && sql.contains("t.status='ACTIVE'"));
            assertTrue(sql.contains("d.is_active=true AND d.unbind_time IS NULL"));
            return query;
        });
        when(query.bind(anyString(), any())).thenReturn(query);
        when(query.map(any(java.util.function.BiFunction.class))).thenReturn(rows);
        when(rows.one()).thenReturn(serial);
    }
}
