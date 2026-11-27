-- Track installer-managed database upgrades.
-- Fresh databases execute this file before all numbered schema scripts.

CREATE TABLE tds_schema_migrations (
    migration_name VARCHAR(255) PRIMARY KEY,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

