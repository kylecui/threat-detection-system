# 001. PostgreSQL over ClickHouse for Threat Detection Platform

## Status
Accepted

## Context
The original cloud design (in `/mnt/d/MyWorkSpaces/jzzn_Cloud/`) planned ClickHouse for time-series analytics of attack events. The current implementation uses PostgreSQL 15.

The platform is a honeypot/deception defense system processing syslog attack events from sentinel devices. It requires multi-tenant isolation via `customerId`.

## Decision
PostgreSQL was chosen for:
1. Operational simplicity — single database for all services.
2. Current scale is single-digit customers with <1M events/day.
3. PostgreSQL JSONB provides sufficient query flexibility.
4. Fewer infrastructure components to maintain in K8s.

## Consequences
ClickHouse becomes necessary when:
- >10M events/day.
- Need for sub-second aggregation over months of data.
- Multiple concurrent dashboard users.

The migration path involves adding ClickHouse as a read-replica fed from Kafka, while keeping PostgreSQL for OLTP.
