package com.threatdetection.gateway.auth.service;

import com.threatdetection.gateway.auth.entity.AuthUser;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Resolves the current operational state from the database rather than from
 * stale JWT claims. Tenant and customer suspension therefore takes effect on
 * the next authenticated request.
 */
@Service
public class AccountLifecycleService {

    private final DatabaseClient databaseClient;

    public AccountLifecycleService(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public Mono<Boolean> isOperational(AuthUser user) {
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            return Mono.just(false);
        }

        Mono<Boolean> tenantOperational = user.getTenantId() == null
                ? Mono.just(true)
                : databaseClient.sql(
                                "SELECT EXISTS (SELECT 1 FROM tenants " +
                                "WHERE id = :tenantId AND status = 'ACTIVE') AS active")
                        .bind("tenantId", user.getTenantId())
                        .map((row, metadata) -> Boolean.TRUE.equals(row.get("active", Boolean.class)))
                        .one()
                        .defaultIfEmpty(false);

        Mono<Boolean> customerOperational;
        if (user.getCustomerId() == null || user.getCustomerId().isBlank()) {
            customerOperational = Mono.just(true);
        } else if (user.getTenantId() == null) {
            customerOperational = databaseClient.sql(
                            "SELECT EXISTS (SELECT 1 FROM customers " +
                            "WHERE customer_id = :customerId AND status = 'ACTIVE') AS active")
                    .bind("customerId", user.getCustomerId())
                    .map((row, metadata) -> Boolean.TRUE.equals(row.get("active", Boolean.class)))
                    .one()
                    .defaultIfEmpty(false);
        } else {
            customerOperational = databaseClient.sql(
                            "SELECT EXISTS (SELECT 1 FROM customers " +
                            "WHERE customer_id = :customerId AND tenant_id = :tenantId " +
                            "AND status = 'ACTIVE') AS active")
                    .bind("customerId", user.getCustomerId())
                    .bind("tenantId", user.getTenantId())
                    .map((row, metadata) -> Boolean.TRUE.equals(row.get("active", Boolean.class)))
                    .one()
                    .defaultIfEmpty(false);
        }

        return Mono.zip(tenantOperational, customerOperational)
                .map(state -> state.getT1() && state.getT2());
    }
}
