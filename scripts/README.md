# Scripts Directory

This directory contains all scripts for the threat detection system, organized by their primary function into subdirectories for better maintainability.

## 📁 Directory Structure

```
scripts/
├── deploy/               # Deployment and initialization scripts
│   ├── init-kafka.sh
│   ├── rebuild_service.sh
│   ├── Dockerfile
│   ├── build-all-images.sh
│   ├── deploy_api_gateway.sh
│   ├── deploy_ip_segment_weights_v4.sh
│   ├── k8s-deploy.sh
│   └── validate-k8s-config.sh
├── maintenance/          # Maintenance, monitoring, and testing scripts
│   ├── diagnose_data_flow.sh
│   ├── fetch_alerts_api.py
│   ├── bulk_log_import.py
│   ├── fetch_recent_alerts.py
│   ├── test/             # Testing scripts
│   ├── ALERT_MONITORING_GUIDE.md
│   ├── BULK_LOG_IMPORT_README.md
│   ├── create_historical_customers.py
│   ├── fix_field_naming.sh
│   ├── full_api_gateway_test.sh
│   ├── insert_test_alerts.py
│   ├── integration_test_responsibility_separation.sh
│   ├── migrate_device_mappings.py
│   ├── phase1_completion_verify.sh
│   ├── quick_test_phase2.sh
│   ├── rebuild.sh
│   └── send_burst_test.sh
├── archive/              # Deprecated scripts and tools
│   ├── tools/           # Old utility scripts
│   └── utils/           # Old development utilities
├── create_temp_customers.py
├── bulk_log_import.py
├── fetch_alerts_api.py
├── fetch_recent_alerts.py
├── integration_test_notifications.py
├── requirements.txt
└── README.md
```

## 🚀 Deployment Scripts (deploy/)

Scripts for deploying and initializing system components.

### init-kafka.sh
**Purpose**: Initialize Kafka cluster with required topics and configurations
**Usage**:
```bash
./scripts/deploy/init-kafka.sh
```

### rebuild_service.sh
**Purpose**: Rebuild specific service containers
**Usage**:
```bash
./scripts/deploy/rebuild_service.sh <service-name>
```

### Other deployment scripts
- `Dockerfile` - Docker image for scripts
- `build-all-images.sh` - Build all Docker images
- `deploy_api_gateway.sh` - Deploy API gateway
- `deploy_ip_segment_weights_v4.sh` - Deploy IP segment weights
- `k8s-deploy.sh` - Kubernetes deployment
- `validate-k8s-config.sh` - Validate Kubernetes config

## 🛠️ Maintenance Scripts (maintenance/)

Scripts for system maintenance, diagnostics, and testing.

### Core maintenance scripts
- `diagnose_data_flow.sh` - Diagnose data flow issues
- `fetch_alerts_api.py` - Fetch alerts via API
- `bulk_log_import.py` - Bulk log import utility
- `fetch_recent_alerts.py` - Fetch recent alerts

### Testing scripts (test/)
All testing scripts have been moved to the test/ subdirectory within maintenance/

### Additional maintenance scripts
- `ALERT_MONITORING_GUIDE.md` - Alert monitoring guide
- `BULK_LOG_IMPORT_README.md` - Bulk import documentation
- `create_historical_customers.py` - Create historical customers
- `fix_field_naming.sh` - Fix field naming issues
- `full_api_gateway_test.sh` - Full API gateway test
- `insert_test_alerts.py` - Insert test alerts
- `integration_test_responsibility_separation.sh` - Integration test
- `migrate_device_mappings.py` - Migrate device mappings
- `phase1_completion_verify.sh` - Phase 1 verification
- `quick_test_phase2.sh` - Quick phase 2 test
- `rebuild.sh` - Rebuild services
- `send_burst_test.sh` - Send burst test data

## 🗂️ Core Utility Tools (Root Level)

Essential tools kept at root level for easy access.

- `create_temp_customers.py` - Create temporary customers
- `bulk_log_import.py` - Bulk log import (duplicate in maintenance/)
- `fetch_alerts_api.py` - Fetch alerts (duplicate in maintenance/)
- `fetch_recent_alerts.py` - Fetch recent alerts (duplicate in maintenance/)
- `integration_test_notifications.py` - Notification integration test
- `requirements.txt` - Python dependencies

## 📦 Archived Scripts (archive/)

Deprecated scripts moved to archive/ for reference.

- `tools/` - Old utility scripts
- `utils/` - Old development utilities

## 📋 Usage Guidelines

### Script Categories

1. **🔴 Deployment Scripts**: Use deploy/ scripts for system setup and deployment
2. **🟡 Maintenance Scripts**: Use maintenance/ scripts for diagnostics and testing
3. **🟢 Core Tools**: Use root-level scripts for common operations
4. **⚫ Archived Scripts**: Avoid using archive/ scripts in new development

### Best Practices

- Always run from project root directory
- Check script permissions before execution
- Use absolute paths when calling from other scripts
- Test in development before production use

## 🔄 Recent Updates

- **Directory Restructuring**: Reorganized scripts into deploy/, maintenance/, and archive/ subdirectories for better organization
- **Core Tools**: Kept essential utilities at root level for easy access
- **Path Updates**: Updated all documentation to reflect new directory structure

## Current Implementation Status

- ✅ **Directory Structure**: New organization implemented
- ✅ **Deployment Scripts**: All deployment tools organized in deploy/
- ✅ **Maintenance Scripts**: All maintenance tools in maintenance/
- ✅ **Core Utilities**: Essential tools at root level
- ✅ **Documentation**: README updated with new structure</content>
<parameter name="filePath">/home/kylecui/threat-detection-system/scripts/README.md