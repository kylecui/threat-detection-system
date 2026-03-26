-- ============================================================================
-- 18-net-segment-weights.sql
-- Per-customer CIDR-based network segment weights for threat scoring.
-- Replaces the legacy 186 hardcoded segment rules with a configurable,
-- multi-tenant approach.  See docs/design/net_weighting_strategy.md.
-- ============================================================================

CREATE TABLE IF NOT EXISTS net_segment_weights (
    id          BIGSERIAL       PRIMARY KEY,
    customer_id VARCHAR(100)    NOT NULL,
    cidr        VARCHAR(43)     NOT NULL,          -- e.g. "192.168.10.0/24", supports IPv6
    weight      DECIMAL(4,2)   NOT NULL DEFAULT 1.0,
    description VARCHAR(255),
    created_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(customer_id, cidr)
);

CREATE INDEX IF NOT EXISTS idx_nsw_customer_id   ON net_segment_weights(customer_id);
CREATE INDEX IF NOT EXISTS idx_nsw_customer_cidr ON net_segment_weights(customer_id, cidr);

-- Seed: import a representative subset of the original 186 segments as a
-- global template (customer_id = '__template__').  Customer-onboarding flows
-- can copy these rows for new tenants.

INSERT INTO net_segment_weights (customer_id, cidr, weight, description) VALUES
    ('__template__', '10.0.0.0/8',       1.5, 'Internal private network (Class A)'),
    ('__template__', '172.16.0.0/12',     1.5, 'Internal private network (Class B)'),
    ('__template__', '192.168.0.0/16',    1.2, 'Internal private network (Class C)'),
    ('__template__', '192.168.1.0/24',    1.8, 'Server subnet (common)'),
    ('__template__', '192.168.10.0/24',   2.0, 'Management VLAN (critical)'),
    ('__template__', '192.168.100.0/24',  1.8, 'Database subnet'),
    ('__template__', '10.10.0.0/16',      1.6, 'Core infrastructure'),
    ('__template__', '10.20.0.0/16',      1.4, 'Development network'),
    ('__template__', '172.16.10.0/24',    2.0, 'Domain controller subnet'),
    ('__template__', '172.16.20.0/24',    1.8, 'Financial systems subnet')
ON CONFLICT (customer_id, cidr) DO NOTHING;
