-- Device Status History table
-- Stores periodic device status reports (heartbeat data)
-- Referenced by: data-ingestion, threat-assessment, customer-management

CREATE TABLE IF NOT EXISTS device_status_history (
    id BIGSERIAL PRIMARY KEY,
    dev_serial VARCHAR(64) NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    sentry_count INTEGER NOT NULL,
    real_host_count INTEGER NOT NULL,
    dev_start_time BIGINT,
    dev_end_time BIGINT,
    report_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dsh_dev_serial ON device_status_history(dev_serial);
CREATE INDEX IF NOT EXISTS idx_dsh_customer_id ON device_status_history(customer_id);
CREATE INDEX IF NOT EXISTS idx_dsh_report_time ON device_status_history(report_time DESC);

GRANT SELECT, INSERT, UPDATE, DELETE ON device_status_history TO threat_user;
GRANT USAGE, SELECT ON SEQUENCE device_status_history_id_seq TO threat_user;
