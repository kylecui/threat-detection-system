# Net Segment Weighting Strategy

## Overview
This document outlines the strategy for incorporating network segment weighting into the threat detection platform's scoring engine. It bridges the gap between the legacy hardcoded approach and the current diversity-based model.

## Original System
The original C#/MySQL-based system utilized a static weighting approach for network segments. It contained **186** hardcoded network segment configurations with associated threat weights. These were CIDR-based rules stored in a MySQL table. During threat score calculation, the system would check if a target IP fell within these segments (e.g., server subnets, management VLANs) and apply a higher weight to the score, as attacks on these critical segments are more concerning.

## Current Simplified Approach
The new cloud-native platform (Java/Spring Boot/PostgreSQL) currently employs a simplified diversity-based algorithm (`uniqueIps` weighting) instead of per-segment weights. This design choice was made for several reasons:
1. **Customer-Specific Topology**: Network segment weights are highly dependent on the specific network topology of each deployment.
2. **Scalability**: Hardcoding 186 segments does not scale across a diverse set of customers with varying infrastructure.
3. **Signal Correlation**: The diversity metric (number of unique IPs targeted) captures a similar signal, as a broader attack across multiple IPs typically indicates a higher threat level.

## Hybrid Strategy
To provide both the flexibility of the new platform and the precision of the original system, we are moving to a hybrid strategy. This approach allows for configurable net segment weights per **customer** via the customer-management service. This ensures that critical infrastructure can be prioritized while maintaining the benefits of the diversity-based scoring.

## Data Model
The following table design will be implemented in the PostgreSQL database to store per-customer segment weights:

```sql
CREATE TABLE net_segment_weights (
  id BIGSERIAL PRIMARY KEY,
  customer_id VARCHAR(100) NOT NULL,
  cidr VARCHAR(43) NOT NULL,
  weight DECIMAL(4,2) NOT NULL DEFAULT 1.0,
  description VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(customer_id, cidr)
);

CREATE INDEX idx_nsw_customer_id ON net_segment_weights(customer_id);
CREATE INDEX idx_nsw_customer_cidr ON net_segment_weights(customer_id, cidr);
```

## Scoring Integration
The `net_segment_weights` table will integrate with the existing threat scoring formula by introducing a `netWeight` factor. When a threat is detected, the system will identify which CIDR the `response_ip` (the decoy/honeypot IP) belongs to for that specific customer. The corresponding `weight` will be retrieved and multiplied into the final threat score. If no matching CIDR is found, a default weight of 1.0 is applied.

## Migration Path
To facilitate the transition for existing users and provide a baseline for new deployments, the **186** original segment configurations will be maintained as a global template. During the onboarding of a new customer, these 186 segments can be imported into the `net_segment_weights` table as a starting point, which the customer can then customize to match their specific network environment.
