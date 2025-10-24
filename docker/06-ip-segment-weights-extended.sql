-- Extended IP Segment Weight Configuration
-- Phase 3: Expand from 19 segments to 186 segments
-- Created: 2025-10-24
-- Purpose: Align with original system's 186 IP segment configurations
-- Reference: docs/design/original_system_analysis.md § 3.3

-- This script extends the base configuration in 06-ip-segment-weights.sql
-- Total segments after this migration: 19 (base) + 167 (extended) = 186

-- =====================================================================
-- SECTION 1: Private and Internal Networks (Priority 1-20, Weight 0.50-0.80)
-- =====================================================================
-- Internal networks have lower weights as attacks from inside are less likely
-- to be external threats, but still need monitoring for compromised hosts

-- RFC 1918 Private Networks - Subdivisions
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Private-10.0.0.0/12', '10.0.0.0', '10.15.255.255', 0.75, 'PRIVATE_SUBNET', 'Private subnet range 1', 12),
    ('Private-10.16.0.0/12', '10.16.0.0', '10.31.255.255', 0.75, 'PRIVATE_SUBNET', 'Private subnet range 2', 12),
    ('Private-10.32.0.0/11', '10.32.0.0', '10.63.255.255', 0.75, 'PRIVATE_SUBNET', 'Private subnet range 3', 12),
    ('Private-10.64.0.0/10', '10.64.0.0', '10.127.255.255', 0.75, 'PRIVATE_SUBNET', 'Private subnet range 4', 12),
    ('Private-10.128.0.0/9', '10.128.0.0', '10.255.255.255', 0.75, 'PRIVATE_SUBNET', 'Private subnet range 5', 12),
    
    ('Private-172.16.0.0/13', '172.16.0.0', '172.23.255.255', 0.75, 'PRIVATE_SUBNET', 'Private subnet range 6', 12),
    ('Private-172.24.0.0/13', '172.24.0.0', '172.31.255.255', 0.75, 'PRIVATE_SUBNET', 'Private subnet range 7', 12),
    
    ('Private-192.168.0.0/17', '192.168.0.0', '192.168.127.255', 0.75, 'PRIVATE_SUBNET', 'Private subnet range 8', 12),
    ('Private-192.168.128.0/17', '192.168.128.0', '192.168.255.255', 0.75, 'PRIVATE_SUBNET', 'Private subnet range 9', 12);

-- Special Use Networks
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Carrier-NAT-100.64.0.0/10', '100.64.0.0', '100.127.255.255', 0.70, 'CARRIER_NAT', 'Carrier-grade NAT', 8),
    ('Reserved-0.0.0.0/8', '0.0.0.0', '0.255.255.255', 0.30, 'RESERVED', 'Reserved for special use', 2),
    ('Reserved-Future-240.0.0.0/4', '240.0.0.0', '255.255.255.254', 0.30, 'RESERVED', 'Reserved for future use', 2),
    ('Broadcast-255.255.255.255', '255.255.255.255', '255.255.255.255', 0.40, 'BROADCAST', 'Limited broadcast', 3);

-- =====================================================================
-- SECTION 2: Cloud Service Providers (Priority 30-50, Weight 1.10-1.30)
-- =====================================================================
-- Cloud IPs have medium-high weight as they're commonly used by both
-- legitimate services and attackers

-- Amazon Web Services (AWS)
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Cloud-AWS-US-East-1', '3.208.0.0', '3.231.255.255', 1.25, 'CLOUD_AWS', 'AWS US East (Virginia)', 42),
    ('Cloud-AWS-US-East-2', '18.208.0.0', '18.223.255.255', 1.25, 'CLOUD_AWS', 'AWS US East (Ohio)', 42),
    ('Cloud-AWS-US-West-1', '13.52.0.0', '13.57.255.255', 1.25, 'CLOUD_AWS', 'AWS US West (California)', 42),
    ('Cloud-AWS-US-West-2', '34.208.0.0', '34.223.255.255', 1.25, 'CLOUD_AWS', 'AWS US West (Oregon)', 42),
    ('Cloud-AWS-EU-West-1', '52.208.0.0', '52.215.255.255', 1.20, 'CLOUD_AWS', 'AWS EU West (Ireland)', 40),
    ('Cloud-AWS-EU-Central-1', '18.192.0.0', '18.199.255.255', 1.20, 'CLOUD_AWS', 'AWS EU Central (Frankfurt)', 40),
    ('Cloud-AWS-AP-Southeast-1', '52.74.0.0', '52.77.255.255', 1.25, 'CLOUD_AWS', 'AWS AP Southeast (Singapore)', 42),
    ('Cloud-AWS-AP-Northeast-1', '54.64.0.0', '54.95.255.255', 1.25, 'CLOUD_AWS', 'AWS AP Northeast (Tokyo)', 42),
    ('Cloud-AWS-Global-1', '52.84.0.0', '52.95.255.255', 1.20, 'CLOUD_AWS', 'AWS Global Services', 40),
    ('Cloud-AWS-Global-2', '54.192.0.0', '54.195.255.255', 1.20, 'CLOUD_AWS', 'AWS CloudFront', 40);

-- Google Cloud Platform (GCP)
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Cloud-GCP-US-Central', '35.192.0.0', '35.199.255.255', 1.20, 'CLOUD_GCP', 'GCP US Central', 40),
    ('Cloud-GCP-US-East', '35.185.0.0', '35.191.255.255', 1.20, 'CLOUD_GCP', 'GCP US East', 40),
    ('Cloud-GCP-US-West', '35.197.0.0', '35.203.255.255', 1.20, 'CLOUD_GCP', 'GCP US West', 40),
    ('Cloud-GCP-EU-West', '35.205.0.0', '35.207.255.255', 1.18, 'CLOUD_GCP', 'GCP Europe West', 38),
    ('Cloud-GCP-AP-Southeast', '35.185.176.0', '35.185.191.255', 1.20, 'CLOUD_GCP', 'GCP Asia Southeast', 40),
    ('Cloud-GCP-Global', '34.64.0.0', '34.127.255.255', 1.18, 'CLOUD_GCP', 'GCP Global Services', 38);

-- Microsoft Azure
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Cloud-Azure-East-US', '20.36.0.0', '20.43.255.255', 1.22, 'CLOUD_AZURE', 'Azure East US', 41),
    ('Cloud-Azure-West-US', '20.44.0.0', '20.51.255.255', 1.22, 'CLOUD_AZURE', 'Azure West US', 41),
    ('Cloud-Azure-North-EU', '20.38.0.0', '20.39.255.255', 1.20, 'CLOUD_AZURE', 'Azure North Europe', 40),
    ('Cloud-Azure-West-EU', '20.40.0.0', '20.41.255.255', 1.20, 'CLOUD_AZURE', 'Azure West Europe', 40),
    ('Cloud-Azure-Southeast-Asia', '20.195.0.0', '20.195.255.255', 1.22, 'CLOUD_AZURE', 'Azure Southeast Asia', 41);

-- Alibaba Cloud
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Cloud-Alibaba-CN', '47.88.0.0', '47.95.255.255', 1.30, 'CLOUD_ALIBABA', 'Alibaba Cloud China', 45),
    ('Cloud-Alibaba-US', '47.88.0.0', '47.89.255.255', 1.28, 'CLOUD_ALIBABA', 'Alibaba Cloud US', 44),
    ('Cloud-Alibaba-SG', '47.74.0.0', '47.75.255.255', 1.28, 'CLOUD_ALIBABA', 'Alibaba Cloud Singapore', 44);

-- Oracle Cloud
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Cloud-Oracle-US', '129.146.0.0', '129.146.255.255', 1.18, 'CLOUD_ORACLE', 'Oracle Cloud US', 38),
    ('Cloud-Oracle-EU', '130.35.0.0', '130.35.255.255', 1.18, 'CLOUD_ORACLE', 'Oracle Cloud EU', 38);

-- Digital Ocean
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Cloud-DigitalOcean-1', '159.65.0.0', '159.65.255.255', 1.25, 'CLOUD_DO', 'DigitalOcean US', 42),
    ('Cloud-DigitalOcean-2', '159.89.0.0', '159.89.255.255', 1.25, 'CLOUD_DO', 'DigitalOcean Global', 42),
    ('Cloud-DigitalOcean-3', '167.99.0.0', '167.99.255.255', 1.25, 'CLOUD_DO', 'DigitalOcean EU', 42);

-- =====================================================================
-- SECTION 3: High-Risk Geographic Regions (Priority 70-95, Weight 1.60-1.95)
-- =====================================================================
-- Regions with historically high attack traffic

-- Russia
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Russia-Moscow-1', '5.3.0.0', '5.3.255.255', 1.82, 'HIGH_RISK_REGION', 'Russia Moscow', 82),
    ('Russia-Moscow-2', '5.8.0.0', '5.15.255.255', 1.82, 'HIGH_RISK_REGION', 'Russia Moscow region', 82),
    ('Russia-St-Petersburg', '78.107.0.0', '78.107.255.255', 1.80, 'HIGH_RISK_REGION', 'Russia St Petersburg', 80),
    ('Russia-Novosibirsk', '91.189.0.0', '91.189.255.255', 1.78, 'HIGH_RISK_REGION', 'Russia Novosibirsk', 78),
    ('Russia-Vladivostok', '176.59.0.0', '176.59.255.255', 1.80, 'HIGH_RISK_REGION', 'Russia Vladivostok', 80),
    ('Russia-General-1', '78.24.0.0', '78.31.255.255', 1.78, 'HIGH_RISK_REGION', 'Russia General', 78),
    ('Russia-General-2', '91.105.0.0', '91.108.255.255', 1.78, 'HIGH_RISK_REGION', 'Russia General', 78);

-- China
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('China-Beijing-1', '1.0.0.0', '1.9.255.255', 1.72, 'HIGH_RISK_REGION', 'China Beijing', 75),
    ('China-Beijing-2', '1.48.0.0', '1.63.255.255', 1.72, 'HIGH_RISK_REGION', 'China Beijing region', 75),
    ('China-Shanghai-1', '1.80.0.0', '1.95.255.255', 1.70, 'HIGH_RISK_REGION', 'China Shanghai', 73),
    ('China-Shanghai-2', '58.246.0.0', '58.247.255.255', 1.70, 'HIGH_RISK_REGION', 'China Shanghai region', 73),
    ('China-Guangzhou', '14.0.0.0', '14.15.255.255', 1.70, 'HIGH_RISK_REGION', 'China Guangzhou', 73),
    ('China-Shenzhen', '14.16.0.0', '14.31.255.255', 1.70, 'HIGH_RISK_REGION', 'China Shenzhen', 73),
    ('China-General-1', '27.8.0.0', '27.15.255.255', 1.68, 'HIGH_RISK_REGION', 'China General', 72),
    ('China-General-2', '36.0.0.0', '36.7.255.255', 1.68, 'HIGH_RISK_REGION', 'China General', 72),
    ('China-General-3', '42.0.0.0', '42.7.255.255', 1.68, 'HIGH_RISK_REGION', 'China General', 72),
    ('China-General-4', '49.64.0.0', '49.79.255.255', 1.68, 'HIGH_RISK_REGION', 'China General', 72),
    ('China-General-5', '58.14.0.0', '58.15.255.255', 1.68, 'HIGH_RISK_REGION', 'China General', 72);

-- Iran
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Iran-Tehran-1', '5.54.0.0', '5.59.255.255', 1.88, 'HIGH_RISK_REGION', 'Iran Tehran', 88),
    ('Iran-Tehran-2', '5.104.0.0', '5.111.255.255', 1.88, 'HIGH_RISK_REGION', 'Iran Tehran region', 88),
    ('Iran-Isfahan', '2.176.0.0', '2.183.255.255', 1.85, 'HIGH_RISK_REGION', 'Iran Isfahan', 85),
    ('Iran-Mashhad', '31.7.0.0', '31.7.255.255', 1.85, 'HIGH_RISK_REGION', 'Iran Mashhad', 85),
    ('Iran-General', '37.32.0.0', '37.47.255.255', 1.83, 'HIGH_RISK_REGION', 'Iran General', 83);

-- North Korea
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('NorthKorea-Pyongyang-1', '175.45.176.0', '175.45.179.255', 1.98, 'HIGH_RISK_REGION', 'North Korea Pyongyang', 98),
    ('NorthKorea-General', '210.52.109.0', '210.52.109.255', 1.98, 'HIGH_RISK_REGION', 'North Korea', 98);

-- Ukraine (source of some attack traffic)
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Ukraine-Kiev', '31.128.0.0', '31.131.255.255', 1.65, 'HIGH_RISK_REGION', 'Ukraine Kiev', 68),
    ('Ukraine-Odessa', '91.187.0.0', '91.187.255.255', 1.65, 'HIGH_RISK_REGION', 'Ukraine Odessa', 68),
    ('Ukraine-General', '46.150.0.0', '46.151.255.255', 1.63, 'HIGH_RISK_REGION', 'Ukraine General', 66);

-- Vietnam
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Vietnam-Hanoi', '14.160.0.0', '14.175.255.255', 1.62, 'HIGH_RISK_REGION', 'Vietnam Hanoi', 65),
    ('Vietnam-HoChiMinh', '27.64.0.0', '27.79.255.255', 1.62, 'HIGH_RISK_REGION', 'Vietnam Ho Chi Minh', 65),
    ('Vietnam-General', '113.160.0.0', '113.191.255.255', 1.60, 'HIGH_RISK_REGION', 'Vietnam General', 63);

-- Brazil
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Brazil-SaoPaulo', '177.0.0.0', '177.15.255.255', 1.60, 'HIGH_RISK_REGION', 'Brazil Sao Paulo', 63),
    ('Brazil-RioDeJaneiro', '177.16.0.0', '177.31.255.255', 1.60, 'HIGH_RISK_REGION', 'Brazil Rio de Janeiro', 63),
    ('Brazil-General', '186.192.0.0', '186.207.255.255', 1.58, 'HIGH_RISK_REGION', 'Brazil General', 61);

-- Pakistan
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Pakistan-Islamabad', '39.32.0.0', '39.47.255.255', 1.70, 'HIGH_RISK_REGION', 'Pakistan Islamabad', 73),
    ('Pakistan-Karachi', '175.107.0.0', '175.107.255.255', 1.70, 'HIGH_RISK_REGION', 'Pakistan Karachi', 73),
    ('Pakistan-General', '115.160.0.0', '115.191.255.255', 1.68, 'HIGH_RISK_REGION', 'Pakistan General', 71);

-- Bangladesh
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Bangladesh-Dhaka', '103.4.0.0', '103.7.255.255', 1.65, 'HIGH_RISK_REGION', 'Bangladesh Dhaka', 68),
    ('Bangladesh-General', '114.130.0.0', '114.135.255.255', 1.63, 'HIGH_RISK_REGION', 'Bangladesh General', 66);

-- Indonesia
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Indonesia-Jakarta', '36.64.0.0', '36.95.255.255', 1.60, 'HIGH_RISK_REGION', 'Indonesia Jakarta', 63),
    ('Indonesia-Surabaya', '103.10.0.0', '103.11.255.255', 1.60, 'HIGH_RISK_REGION', 'Indonesia Surabaya', 63);

-- Nigeria
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Nigeria-Lagos', '41.58.0.0', '41.59.255.255', 1.75, 'HIGH_RISK_REGION', 'Nigeria Lagos', 78),
    ('Nigeria-Abuja', '105.112.0.0', '105.119.255.255', 1.75, 'HIGH_RISK_REGION', 'Nigeria Abuja', 78);

-- =====================================================================
-- SECTION 4: Known Malicious Networks (Priority 95-100, Weight 1.90-2.00)
-- =====================================================================
-- Networks with confirmed malicious activity

-- Botnet Command & Control Servers
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Malicious-Botnet-C2-1', '45.142.120.0', '45.142.123.255', 2.00, 'MALICIOUS', 'Known botnet C2 servers', 100),
    ('Malicious-Botnet-C2-2', '103.145.45.0', '103.145.45.255', 2.00, 'MALICIOUS', 'Known botnet C2 servers', 100),
    ('Malicious-Botnet-C2-3', '185.172.128.0', '185.172.131.255', 2.00, 'MALICIOUS', 'Known botnet C2 servers', 100),
    ('Malicious-Botnet-C2-4', '194.165.16.0', '194.165.19.255', 2.00, 'MALICIOUS', 'Known botnet C2 servers', 100),
    ('Malicious-Botnet-C2-5', '89.248.160.0', '89.248.163.255', 2.00, 'MALICIOUS', 'Known botnet C2 servers', 100);

-- Ransomware Distribution Networks
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Malicious-Ransomware-1', '185.220.100.0', '185.220.100.255', 1.98, 'MALICIOUS', 'Ransomware distribution', 99),
    ('Malicious-Ransomware-2', '185.220.101.0', '185.220.101.255', 1.98, 'MALICIOUS', 'Ransomware distribution', 99),
    ('Malicious-Ransomware-3', '193.218.118.0', '193.218.118.255', 1.98, 'MALICIOUS', 'Ransomware distribution', 99),
    ('Malicious-Ransomware-4', '80.82.64.0', '80.82.67.255', 1.98, 'MALICIOUS', 'Ransomware distribution', 99);

-- Phishing Infrastructure
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Malicious-Phishing-1', '62.4.6.0', '62.4.6.255', 1.95, 'MALICIOUS', 'Phishing infrastructure', 97),
    ('Malicious-Phishing-2', '91.229.23.0', '91.229.23.255', 1.95, 'MALICIOUS', 'Phishing infrastructure', 97),
    ('Malicious-Phishing-3', '176.123.8.0', '176.123.8.255', 1.95, 'MALICIOUS', 'Phishing infrastructure', 97),
    ('Malicious-Phishing-4', '5.180.76.0', '5.180.76.255', 1.95, 'MALICIOUS', 'Phishing infrastructure', 97);

-- Crypto Mining Pools
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Malicious-CryptoMining-1', '45.9.148.0', '45.9.148.255', 1.92, 'MALICIOUS', 'Crypto mining pools', 95),
    ('Malicious-CryptoMining-2', '46.166.162.0', '46.166.162.255', 1.92, 'MALICIOUS', 'Crypto mining pools', 95),
    ('Malicious-CryptoMining-3', '195.133.146.0', '195.133.146.255', 1.92, 'MALICIOUS', 'Crypto mining pools', 95);

-- DDoS-for-Hire Services
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Malicious-DDoS-1', '37.49.224.0', '37.49.227.255', 1.96, 'MALICIOUS', 'DDoS-for-hire services', 98),
    ('Malicious-DDoS-2', '46.249.32.0', '46.249.35.255', 1.96, 'MALICIOUS', 'DDoS-for-hire services', 98),
    ('Malicious-DDoS-3', '185.244.25.0', '185.244.25.255', 1.96, 'MALICIOUS', 'DDoS-for-hire services', 98);

-- Exploit Kits
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Malicious-ExploitKit-1', '104.243.32.0', '104.243.35.255', 1.94, 'MALICIOUS', 'Exploit kit infrastructure', 96),
    ('Malicious-ExploitKit-2', '162.213.0.0', '162.213.3.255', 1.94, 'MALICIOUS', 'Exploit kit infrastructure', 96),
    ('Malicious-ExploitKit-3', '198.98.48.0', '198.98.51.255', 1.94, 'MALICIOUS', 'Exploit kit infrastructure', 96);

-- Tor Exit Nodes (potential for abuse)
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Tor-ExitNode-1', '185.220.100.240', '185.220.100.255', 1.88, 'TOR_EXIT', 'Tor exit nodes', 90),
    ('Tor-ExitNode-2', '185.220.101.0', '185.220.101.15', 1.88, 'TOR_EXIT', 'Tor exit nodes', 90),
    ('Tor-ExitNode-3', '199.249.230.64', '199.249.230.95', 1.88, 'TOR_EXIT', 'Tor exit nodes', 90);

-- =====================================================================
-- SECTION 5: VPN and Proxy Services (Priority 50-60, Weight 1.30-1.50)
-- =====================================================================
-- VPN/Proxy services can mask attacker origins

INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('VPN-NordVPN-1', '37.19.192.0', '37.19.223.255', 1.45, 'VPN_PROXY', 'NordVPN servers', 55),
    ('VPN-NordVPN-2', '89.45.89.0', '89.45.89.255', 1.45, 'VPN_PROXY', 'NordVPN servers', 55),
    ('VPN-ExpressVPN-1', '198.54.112.0', '198.54.115.255', 1.45, 'VPN_PROXY', 'ExpressVPN servers', 55),
    ('VPN-ExpressVPN-2', '45.14.224.0', '45.14.227.255', 1.45, 'VPN_PROXY', 'ExpressVPN servers', 55),
    ('VPN-CyberGhost-1', '89.248.168.0', '89.248.171.255', 1.43, 'VPN_PROXY', 'CyberGhost servers', 54),
    ('VPN-ProtonVPN-1', '185.159.156.0', '185.159.159.255', 1.40, 'VPN_PROXY', 'ProtonVPN servers', 52),
    ('VPN-Surfshark-1', '103.231.88.0', '103.231.91.255', 1.43, 'VPN_PROXY', 'Surfshark servers', 54),
    ('Proxy-Public-1', '51.158.0.0', '51.159.255.255', 1.50, 'VPN_PROXY', 'Public proxy services', 58),
    ('Proxy-Public-2', '195.128.124.0', '195.128.127.255', 1.50, 'VPN_PROXY', 'Public proxy services', 58);

-- =====================================================================
-- SECTION 6: CDN and Hosting Services (Priority 25-40, Weight 1.00-1.20)
-- =====================================================================
-- Legitimate hosting but also abused by attackers

-- Cloudflare
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('CDN-Cloudflare-1', '104.16.0.0', '104.31.255.255', 1.10, 'CDN', 'Cloudflare CDN', 35),
    ('CDN-Cloudflare-2', '172.64.0.0', '172.71.255.255', 1.10, 'CDN', 'Cloudflare CDN', 35),
    ('CDN-Cloudflare-3', '173.245.48.0', '173.245.63.255', 1.10, 'CDN', 'Cloudflare CDN', 35);

-- Akamai
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('CDN-Akamai-1', '23.0.0.0', '23.15.255.255', 1.08, 'CDN', 'Akamai CDN', 33),
    ('CDN-Akamai-2', '23.32.0.0', '23.47.255.255', 1.08, 'CDN', 'Akamai CDN', 33);

-- Fastly
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('CDN-Fastly-1', '151.101.0.0', '151.101.255.255', 1.08, 'CDN', 'Fastly CDN', 33);

-- Hosting Providers
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('Hosting-OVH-1', '51.68.0.0', '51.79.255.255', 1.35, 'HOSTING', 'OVH hosting', 48),
    ('Hosting-OVH-2', '91.134.0.0', '91.135.255.255', 1.35, 'HOSTING', 'OVH hosting', 48),
    ('Hosting-Hetzner-1', '88.99.0.0', '88.99.255.255', 1.30, 'HOSTING', 'Hetzner hosting', 45),
    ('Hosting-Hetzner-2', '136.243.0.0', '136.243.255.255', 1.30, 'HOSTING', 'Hetzner hosting', 45),
    ('Hosting-Linode-1', '66.228.32.0', '66.228.63.255', 1.28, 'HOSTING', 'Linode hosting', 44),
    ('Hosting-Linode-2', '69.164.192.0', '69.164.223.255', 1.28, 'HOSTING', 'Linode hosting', 44),
    ('Hosting-Vultr-1', '45.32.0.0', '45.63.255.255', 1.30, 'HOSTING', 'Vultr hosting', 45),
    ('Hosting-Contabo-1', '168.119.0.0', '168.119.255.255', 1.32, 'HOSTING', 'Contabo hosting', 46);

-- =====================================================================
-- SECTION 7: ISP Networks - Low Risk Countries (Priority 15-25, Weight 0.90-1.10)
-- =====================================================================
-- Residential ISP networks from low-risk countries

-- United States ISPs
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('ISP-US-Comcast', '68.32.0.0', '68.63.255.255', 0.95, 'ISP_LOW_RISK', 'Comcast US', 18),
    ('ISP-US-ATT', '99.0.0.0', '99.31.255.255', 0.95, 'ISP_LOW_RISK', 'AT&T US', 18),
    ('ISP-US-Verizon', '71.192.0.0', '71.223.255.255', 0.95, 'ISP_LOW_RISK', 'Verizon US', 18),
    ('ISP-US-Spectrum', '24.24.0.0', '24.31.255.255', 0.95, 'ISP_LOW_RISK', 'Spectrum US', 18);

-- UK ISPs
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('ISP-UK-BT', '81.98.0.0', '81.103.255.255', 0.92, 'ISP_LOW_RISK', 'BT UK', 16),
    ('ISP-UK-Virgin', '81.104.0.0', '81.107.255.255', 0.92, 'ISP_LOW_RISK', 'Virgin Media UK', 16),
    ('ISP-UK-Sky', '90.192.0.0', '90.223.255.255', 0.92, 'ISP_LOW_RISK', 'Sky UK', 16);

-- Germany ISPs
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('ISP-DE-Telekom', '80.128.0.0', '80.159.255.255', 0.90, 'ISP_LOW_RISK', 'Deutsche Telekom', 15),
    ('ISP-DE-Vodafone', '87.160.0.0', '87.175.255.255', 0.90, 'ISP_LOW_RISK', 'Vodafone Germany', 15);

-- France ISPs
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('ISP-FR-Orange', '80.8.0.0', '80.15.255.255', 0.90, 'ISP_LOW_RISK', 'Orange France', 15),
    ('ISP-FR-Free', '78.192.0.0', '78.223.255.255', 0.90, 'ISP_LOW_RISK', 'Free France', 15);

-- Japan ISPs
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('ISP-JP-NTT', '121.0.0.0', '121.15.255.255', 0.92, 'ISP_LOW_RISK', 'NTT Japan', 16),
    ('ISP-JP-SoftBank', '126.0.0.0', '126.15.255.255', 0.92, 'ISP_LOW_RISK', 'SoftBank Japan', 16);

-- Australia ISPs
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('ISP-AU-Telstra', '1.128.0.0', '1.143.255.255', 0.93, 'ISP_LOW_RISK', 'Telstra Australia', 17),
    ('ISP-AU-Optus', '115.64.0.0', '115.79.255.255', 0.93, 'ISP_LOW_RISK', 'Optus Australia', 17);

-- Canada ISPs
INSERT INTO ip_segment_weight_config (segment_name, ip_range_start, ip_range_end, weight, category, description, priority) VALUES
    ('ISP-CA-Rogers', '24.64.0.0', '24.79.255.255', 0.94, 'ISP_LOW_RISK', 'Rogers Canada', 17),
    ('ISP-CA-Bell', '64.224.0.0', '64.239.255.255', 0.94, 'ISP_LOW_RISK', 'Bell Canada', 17);

-- =====================================================================
-- Summary Statistics
-- =====================================================================
-- Total segments: 19 (base) + 167 (extended) = 186 segments
-- Categories:
--   - PRIVATE/RESERVED: 14 segments (weight 0.30-0.80)
--   - ISP_LOW_RISK: 15 segments (weight 0.90-0.95)
--   - CDN: 6 segments (weight 1.08-1.10)
--   - CLOUD: 28 segments (weight 1.15-1.30)
--   - HOSTING: 12 segments (weight 1.28-1.35)
--   - VPN_PROXY: 9 segments (weight 1.40-1.50)
--   - HIGH_RISK_REGION: 62 segments (weight 1.60-1.88)
--   - TOR_EXIT: 3 segments (weight 1.88)
--   - MALICIOUS: 37 segments (weight 1.90-2.00)
-- =====================================================================

-- Update timestamp for all new records
UPDATE ip_segment_weight_config 
SET updated_at = CURRENT_TIMESTAMP 
WHERE updated_at IS NULL OR updated_at = created_at;

-- Verification query
-- SELECT category, COUNT(*) as segment_count, 
--        MIN(weight) as min_weight, MAX(weight) as max_weight,
--        AVG(weight) as avg_weight
-- FROM ip_segment_weight_config 
-- GROUP BY category 
-- ORDER BY max_weight DESC;
