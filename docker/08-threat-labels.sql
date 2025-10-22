-- Threat Labels Configuration Table
-- Created for Phase 4: Threat labeling and categorization system

CREATE TABLE IF NOT EXISTS threat_labels (
    id SERIAL PRIMARY KEY,
    label_code VARCHAR(50) NOT NULL UNIQUE,
    label_name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    description TEXT,
    auto_tag_rules TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_label_code ON threat_labels(label_code);
CREATE INDEX idx_label_category ON threat_labels(category);
CREATE INDEX idx_label_severity ON threat_labels(severity);

-- Insert default threat labels
-- APT Attacks
INSERT INTO threat_labels (label_code, label_name, category, severity, description) VALUES
    ('APT_LATERAL_MOVE', 'APT Lateral Movement', 'APT', 'CRITICAL', 'APT attacker attempting lateral movement within network'),
    ('APT_RECONNAISSANCE', 'APT Reconnaissance Scanning', 'APT', 'HIGH', 'APT attacker conducting network reconnaissance'),
    ('APT_C2_COMMUNICATION', 'APT C2 Communication', 'APT', 'CRITICAL', 'Communication with command and control server'),
    ('APT_DATA_EXFIL', 'APT Data Exfiltration', 'APT', 'CRITICAL', 'APT attacker exfiltrating sensitive data');

-- Ransomware
INSERT INTO threat_labels (label_code, label_name, category, severity, description) VALUES
    ('RANSOMWARE_SMB_SPREAD', 'Ransomware SMB Propagation', 'RANSOMWARE', 'CRITICAL', 'Ransomware spreading via SMB protocol'),
    ('RANSOMWARE_RDP_EXPLOIT', 'Ransomware RDP Exploitation', 'RANSOMWARE', 'CRITICAL', 'Ransomware exploiting RDP for propagation'),
    ('RANSOMWARE_ENCRYPTION', 'Ransomware File Encryption', 'RANSOMWARE', 'CRITICAL', 'Ransomware encrypting files on system'),
    ('RANSOMWARE_PROPAGATION', 'Ransomware Propagation', 'RANSOMWARE', 'HIGH', 'Ransomware propagating to multiple systems');

-- Scanning and Reconnaissance
INSERT INTO threat_labels (label_code, label_name, category, severity, description) VALUES
    ('SCAN_PORT_FULL', 'Full Port Scan', 'SCANNING', 'MEDIUM', 'Attacker scanning all ports on target'),
    ('SCAN_SERVICE_ENUM', 'Service Enumeration', 'SCANNING', 'MEDIUM', 'Attacker enumerating services and versions'),
    ('SCAN_VULNERABILITY', 'Vulnerability Scanning', 'SCANNING', 'HIGH', 'Attacker scanning for known vulnerabilities'),
    ('SCAN_NETWORK_SWEEP', 'Network Sweep Scan', 'SCANNING', 'LOW', 'Attacker performing network sweep scanning');

-- Lateral Movement
INSERT INTO threat_labels (label_code, label_name, category, severity, description) VALUES
    ('MOVE_RDP_ATTEMPT', 'RDP Lateral Movement', 'LATERAL_MOVEMENT', 'HIGH', 'Attacker attempting RDP lateral movement'),
    ('MOVE_SMB_ATTEMPT', 'SMB Lateral Movement', 'LATERAL_MOVEMENT', 'HIGH', 'Attacker attempting SMB lateral movement'),
    ('MOVE_SSH_ATTEMPT', 'SSH Lateral Movement', 'LATERAL_MOVEMENT', 'HIGH', 'Attacker attempting SSH lateral movement'),
    ('MOVE_WMI_ATTEMPT', 'WMI Lateral Movement', 'LATERAL_MOVEMENT', 'HIGH', 'Attacker attempting WMI lateral movement');

-- Brute Force Attacks
INSERT INTO threat_labels (label_code, label_name, category, severity, description) VALUES
    ('BRUTE_RDP', 'RDP Brute Force', 'BRUTE_FORCE', 'HIGH', 'Brute force attack on RDP service'),
    ('BRUTE_SSH', 'SSH Brute Force', 'BRUTE_FORCE', 'HIGH', 'Brute force attack on SSH service'),
    ('BRUTE_DB', 'Database Brute Force', 'BRUTE_FORCE', 'HIGH', 'Brute force attack on database credentials'),
    ('BRUTE_HTTP', 'HTTP Authentication Brute Force', 'BRUTE_FORCE', 'MEDIUM', 'Brute force attack on HTTP authentication');

-- Data Exfiltration
INSERT INTO threat_labels (label_code, label_name, category, severity, description) VALUES
    ('EXFIL_DATABASE', 'Database Exfiltration', 'DATA_EXFILTRATION', 'CRITICAL', 'Database content being exfiltrated'),
    ('EXFIL_FILE_TRANSFER', 'File Transfer Exfiltration', 'DATA_EXFILTRATION', 'HIGH', 'Large file transfer indicating data exfiltration'),
    ('EXFIL_EMAIL', 'Email Exfiltration', 'DATA_EXFILTRATION', 'HIGH', 'Email messages being exfiltrated'),
    ('EXFIL_CLOUD', 'Cloud Storage Exfiltration', 'DATA_EXFILTRATION', 'MEDIUM', 'Data being uploaded to cloud storage');

-- Malware
INSERT INTO threat_labels (label_code, label_name, category, severity, description) VALUES
    ('MALWARE_BOTNET', 'Botnet Infection', 'MALWARE', 'CRITICAL', 'System infected with botnet malware'),
    ('MALWARE_TROJAN', 'Trojan Infection', 'MALWARE', 'HIGH', 'System infected with trojan malware'),
    ('MALWARE_WORM', 'Worm Infection', 'MALWARE', 'HIGH', 'System infected with worm malware'),
    ('MALWARE_DROPPER', 'Malware Dropper', 'MALWARE', 'HIGH', 'Malware dropper downloading additional malware');

-- Network Anomalies
INSERT INTO threat_labels (label_code, label_name, category, severity, description) VALUES
    ('ANOMALY_TRAFFIC', 'Abnormal Network Traffic', 'NETWORK_ANOMALY', 'MEDIUM', 'Unusual network traffic patterns detected'),
    ('ANOMALY_CONN_SPIKE', 'Connection Count Spike', 'NETWORK_ANOMALY', 'MEDIUM', 'Abnormal spike in network connections'),
    ('ANOMALY_BANDWIDTH', 'Abnormal Bandwidth Usage', 'NETWORK_ANOMALY', 'HIGH', 'Abnormal bandwidth consumption detected'),
    ('ANOMALY_TIMING', 'Abnormal Timing Pattern', 'NETWORK_ANOMALY', 'LOW', 'Activity occurring at unusual times');

-- Insider Threats
INSERT INTO threat_labels (label_code, label_name, category, severity, description) VALUES
    ('INSIDER_DATA_COPY', 'Insider Data Copy', 'INSIDER_THREAT', 'HIGH', 'Employee copying large amounts of data'),
    ('INSIDER_PRIV_ESCALATE', 'Insider Privilege Escalation', 'INSIDER_THREAT', 'HIGH', 'Employee escalating privileges'),
    ('INSIDER_CONFIG_CHANGE', 'Insider Configuration Change', 'INSIDER_THREAT', 'MEDIUM', 'Employee making unauthorized configuration changes'),
    ('INSIDER_BACKUP_ATTEMPT', 'Insider Backup Attempt', 'INSIDER_THREAT', 'MEDIUM', 'Employee attempting to access/backup sensitive systems');

-- Exploitation
INSERT INTO threat_labels (label_code, label_name, category, severity, description) VALUES
    ('EXPLOIT_CVE_ATTACK', 'CVE Exploitation', 'EXPLOITATION', 'CRITICAL', 'Exploitation of known CVE vulnerability'),
    ('EXPLOIT_ZERO_DAY', 'Zero-Day Exploitation', 'EXPLOITATION', 'CRITICAL', 'Potential zero-day vulnerability exploitation'),
    ('EXPLOIT_PRIVILEGE', 'Privilege Escalation', 'EXPLOITATION', 'HIGH', 'Privilege escalation attack'),
    ('EXPLOIT_SQL_INJECT', 'SQL Injection Attack', 'EXPLOITATION', 'HIGH', 'SQL injection attack detected');

-- Denial of Service
INSERT INTO threat_labels (label_code, label_name, category, severity, description) VALUES
    ('DOS_DDOS_ATTACK', 'DDoS Attack', 'DOS', 'HIGH', 'Distributed denial of service attack'),
    ('DOS_RESOURCE_EXHAUST', 'Resource Exhaustion', 'DOS', 'MEDIUM', 'Resource exhaustion attack'),
    ('DOS_SLOWLORIS', 'Slowloris Attack', 'DOS', 'MEDIUM', 'Slowloris DoS attack'),
    ('DOS_BANDWIDTH_FLOOD', 'Bandwidth Flood', 'DOS', 'HIGH', 'Bandwidth flooding attack');

-- Web Attacks
INSERT INTO threat_labels (label_code, label_name, category, severity, description) VALUES
    ('WEB_XSS_ATTACK', 'Cross-Site Scripting (XSS)', 'WEB', 'MEDIUM', 'Cross-site scripting attack'),
    ('WEB_CSRF_ATTACK', 'Cross-Site Request Forgery', 'WEB', 'MEDIUM', 'CSRF attack detected'),
    ('WEB_LOGIC_BYPASS', 'Application Logic Bypass', 'WEB', 'MEDIUM', 'Application business logic bypass attempt'),
    ('WEB_PATH_TRAVERSAL', 'Path Traversal Attack', 'WEB', 'HIGH', 'Directory traversal / path traversal attack');
