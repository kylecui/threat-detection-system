package com.threatdetection.gateway.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.FetchSpec;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceControlReconcileTest {
    record Statement(String sql, Map<String,Object> bindings) { }

    @Test void failedQueryKeepsBusinessPhaseAndReleasesOnlyItsOwnLease() {
        var statements = new ArrayList<Statement>();
        DatabaseClient db = mock(DatabaseClient.class);
        Map<String,Object> row = Map.of("request_id",UUID.randomUUID().toString(),
            "customer_id","customer-a","dev_serial","SERIAL-A",
            "reconcile_query_id",UUID.randomUUID().toString(),"reconcile_query_issued",Instant.now());
        when(db.sql(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            Map<String,Object> bindings = new HashMap<>();
            statements.add(new Statement(sql,bindings));
            DatabaseClient.GenericExecuteSpec spec = mock(DatabaseClient.GenericExecuteSpec.class);
            @SuppressWarnings("unchecked") FetchSpec<Map<String,Object>> fetch = mock(FetchSpec.class);
            when(spec.bind(anyString(),any())).thenAnswer(binding -> {
                bindings.put(binding.getArgument(0),binding.getArgument(1)); return spec;
            });
            when(spec.fetch()).thenReturn(fetch);
            when(fetch.one()).thenReturn(sql.startsWith("WITH due") ? Mono.just(row) : Mono.just(Map.of("authorized",1)));
            when(fetch.rowsUpdated()).thenReturn(Mono.just(1L));
            return spec;
        });
        var published = new ArrayList<Map<String,Object>>();
        DeviceControlService service = new DeviceControlService(db,new ObjectMapper(), null, null) {
            @Override Mono<Void> publishReconcile(Map<String,Object> claimed) {
                published.add(claimed); return Mono.error(new IllegalStateException("test network failure"));
            }
        };
        service.reconcileOne().block();
        assertEquals(List.of(row),published);
        Statement claim = statements.get(0);
        assertTrue(claim.sql.contains("FOR UPDATE SKIP LOCKED"));
        assertTrue(claim.sql.contains("ORDER BY r.next_reconcile_at,r.created_at,r.request_id"));
        Statement failure = statements.stream().filter(s -> "query_publish_unconfirmed".equals(s.bindings.get("error"))).findFirst().orElseThrow();
        assertEquals(claim.bindings.get("claim"),failure.bindings.get("claim"));
        assertTrue(failure.sql.contains("reconcile_claim_token=:claim"));
        assertTrue(failure.sql.contains("make_interval"));
        assertTrue(statements.stream().noneMatch(s -> s.sql.contains("SET phase=")));
    }

    @Test void recoveredQueryReusesPersistedIdentityAndOnlyQueriesOriginalTask() {
        DeviceControlService service = new DeviceControlService(null,new ObjectMapper(), null, null);
        String original = UUID.randomUUID().toString(), query = UUID.randomUUID().toString();
        Instant issued = Instant.now();
        Map<String,Object> row = Map.of("request_id",original,"customer_id","customer-a","dev_serial","SERIAL-A",
            "reconcile_query_id",query,"reconcile_query_issued",issued);
        var first = service.reconcileEnvelope(row);
        var retry = service.reconcileEnvelope(row);
        assertEquals(first,retry);
        assertEquals("task.get",first.get("action"));
        assertEquals(query,first.get("request_id"));
        assertEquals(query,first.get("idempotency_key"));
        assertEquals(Map.of("request_id",original),first.get("params"));
        assertEquals(issued.plusSeconds(120),Instant.parse((String)first.get("expires_at")));
        assertEquals("customer-a",first.get("customer_id"));
        assertNotEquals(original,first.get("request_id"));
    }
}
