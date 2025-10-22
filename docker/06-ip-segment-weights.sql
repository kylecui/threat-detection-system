-- IP Segment Weight Configuration Table
-- Created for Phase 3: IP segment risk weight management

CREATE TABLE IF NOT EXISTS ip_segment_weight_config (
    id SERIAL PRIMARY KEY,
    segment_name VARCHAR(255) NOT NULL UNIQUE,
    ip_range_start VARCHAR(15) NOT NULL,
    ip_range_end VARCHAR(15) NOT NULL,
    weight DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    category VARCHAR(50) NOT NULL,
    description TEXT,
    priority INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes after table creation
CREATE INDEX idx_category ON ip_segment_weight_config(category);
CREATE INDEX idx_priority ON ip_segment_weight_config(priority);
CREATE INDEX idx_weight ON ip_segment_weight_config(weight);

-- Insert default configurations
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Private-192.168.0.0/16', '192.168.0.0', '192.168.255.255', 0.80, 'PRIVATE', 'Internal private network', 10),
    ('Private-10.0.0.0/8', '10.0.0.0', '10.255.255.255', 0.80, 'PRIVATE', 'Internal private network', 10),
    ('Private-172.16.0.0/12', '172.16.0.0', '172.31.255.255', 0.80, 'PRIVATE', 'Internal private network', 10),
    ('Loopback', '127.0.0.1', '127.255.255.255', 0.50, 'LOOPBACK', 'Loopback address', 5),
    ('Link-Local', '169.254.0.0', '169.254.255.255', 0.60, 'LINK_LOCAL', 'Link-local address', 5),
    ('Multicast', '224.0.0.0', '239.255.255.255', 0.40, 'MULTICAST', 'Multicast address', 5),
    ('Reserved', '240.0.0.0', '255.255.255.255', 0.30, 'RESERVED', 'Reserved address', 5);

-- Cloud Provider Networks (Medium Risk)
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Cloud-AWS-1', '54.0.0.0', '54.255.255.255', 1.20, 'CLOUD_AWS', 'AWS US region', 40),
    ('Cloud-AWS-2', '52.0.0.0', '52.255.255.255', 1.20, 'CLOUD_AWS', 'AWS US region', 40),
    ('Cloud-GCP-1', '35.184.0.0', '35.191.255.255', 1.15, 'CLOUD_GCP', 'Google Cloud Platform', 40),
    ('Cloud-Azure-1', '13.64.0.0', '13.107.255.255', 1.15, 'CLOUD_AZURE', 'Microsoft Azure', 40);

-- High Risk Geographic Regions (High Risk)
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Russia-Moscow', '5.3.0.0', '5.3.255.255', 1.80, 'HIGH_RISK_REGION', 'Russia Moscow region', 80),
    ('China-Beijing', '1.0.0.0', '1.9.255.255', 1.70, 'HIGH_RISK_REGION', 'China Beijing region', 75),
    ('Iran-Tehran', '5.54.0.0', '5.59.255.255', 1.85, 'HIGH_RISK_REGION', 'Iran Tehran region', 85),
    ('North-Korea', '175.45.176.0', '175.45.179.255', 1.95, 'HIGH_RISK_REGION', 'North Korea', 95);

-- Known Malicious Networks (Critical)
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Malicious-Botnet-1', '45.142.120.0', '45.142.123.255', 2.00, 'MALICIOUS', 'Known botnet C2 servers', 100),
    ('Malicious-Botnet-2', '103.145.45.0', '103.145.45.255', 2.00, 'MALICIOUS', 'Known botnet C2 servers', 100),
    ('Malicious-Ransomware-1', '185.220.100.0', '185.220.100.255', 1.95, 'MALICIOUS', 'Ransomware distribution network', 98),
    ('Malicious-Phishing-1', '62.4.6.0', '62.4.6.255', 1.90, 'MALICIOUS', 'Phishing infrastructure', 95);

CREATE INDEX idx_ip_range ON ip_segment_weight_config(ip_range_start, ip_range_end);
