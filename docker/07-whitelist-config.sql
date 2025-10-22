-- Whitelist Configuration Table
-- Created for Phase 3: IP/MAC whitelist management

CREATE TABLE IF NOT EXISTS whitelist_config (
    id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    ip_address VARCHAR(15),
    mac_address VARCHAR(17),
    description TEXT,
    category VARCHAR(50) NOT NULL,
    priority INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT unique_ip_per_customer UNIQUE(customer_id, ip_address),
    CONSTRAINT unique_mac_per_customer UNIQUE(customer_id, mac_address)
);

-- Create indexes
CREATE INDEX idx_whitelist_customer ON whitelist_config(customer_id);
CREATE INDEX idx_whitelist_ip ON whitelist_config(ip_address);
CREATE INDEX idx_whitelist_mac ON whitelist_config(mac_address);
CREATE INDEX idx_whitelist_active ON whitelist_config(is_active);
