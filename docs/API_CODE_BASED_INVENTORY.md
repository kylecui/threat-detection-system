# Threat Detection System - Complete API Inventory (Code-Based)

## Overview

This document provides a comprehensive inventory of all REST APIs in the Threat Detection System, based on actual code implementation rather than documentation. The system consists of 5 microservices with a total of 21 controllers providing REST endpoints.

**Generated from code analysis on:** 2025-01-XX
**Total Controllers:** 21
**Total Endpoints:** 125+

## Service Architecture

| Service | Port | Controllers | Purpose |
|---------|------|-------------|---------|
| **threat-assessment** | 8083 | 5 | Threat scoring and assessment |
| **data-ingestion** | 8081 | 2 | Log ingestion and parsing |
| **alert-management** | 8082 | 3 | Alert management and notifications |
| **customer-management** | 8084 | 5 | Customer and device management |
| **api-gateway** | 8080 | 1 | API gateway and routing |

---

## 1. Threat Assessment Service (Port 8083)

### AssessmentController
**Base Path:** `/api/v1/assessment`

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| POST | `/evaluate` | Evaluate threat score for aggregated data | RequestBody: AggregatedAttackData |
| GET | `/{assessmentId}` | Get assessment by ID | Path: assessmentId (Long) |
| GET | `/assessments` | Get paginated assessments | Query: customerId, page, size, sort |
| GET | `/statistics` | Get assessment statistics | Query: customerId, startTime, endTime |
| GET | `/trend` | Get threat score trends | Query: customerId, hours |
| GET | `/port-distribution` | Get port attack distribution | Query: customerId, hours |
| GET | `/health` | Health check | - |

### WeightManagementController
**Base Path:** `/api/v1/weights`

#### Attack Source Weights
| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `/attack-source/{customerId}` | Get attack source weights | Path: customerId |
| GET | `/attack-source/{customerId}/active` | Get active attack source weights | Path: customerId |
| POST | `/attack-source` | Create/update attack source weight | RequestBody: AttackSourceWeightDto |
| DELETE | `/attack-source/{customerId}` | Delete attack source weight | Path: customerId, Query: ipSegment |
| PATCH | `/attack-source/{customerId}/enable` | Enable attack source weight | Path: customerId, Query: ipSegment |
| PATCH | `/attack-source/{customerId}/disable` | Disable attack source weight | Path: customerId, Query: ipSegment |

#### Honeypot Sensitivity Weights
| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `/honeypot-sensitivity/{customerId}` | Get honeypot sensitivity weights | Path: customerId |
| POST | `/honeypot-sensitivity` | Create/update honeypot sensitivity weight | RequestBody: HoneypotSensitivityWeightDto |
| DELETE | `/honeypot-sensitivity/{customerId}` | Delete honeypot sensitivity weight | Path: customerId, Query: ipSegment |

#### Attack Phase Port Configs
| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `/attack-phase/{customerId}` | Get attack phase port configs | Path: customerId |
| GET | `/attack-phase/{customerId}/{phase}` | Get configs by phase | Path: customerId, phase |
| GET | `/attack-phase/{customerId}/{phase}/effective` | Get effective configs | Path: customerId, phase |
| POST | `/attack-phase` | Create/update attack phase port config | RequestBody: AttackPhasePortConfigDto |
| DELETE | `/attack-phase/{customerId}/{phase}/{port}` | Delete attack phase port config | Path: customerId, phase, port |

#### APT Temporal Accumulations
| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `/apt-temporal/{customerId}` | Get APT temporal accumulations | Path: customerId |
| GET | `/apt-temporal/{customerId}/{attackMac}` | Get accumulations by MAC | Path: customerId, attackMac |
| GET | `/apt-temporal/{customerId}/range` | Get accumulations by time range | Path: customerId, Query: startTime, endTime |
| POST | `/apt-temporal` | Create/update APT temporal accumulation | RequestBody: AptTemporalAccumulationDto |
| PUT | `/apt-temporal/{customerId}/{attackMac}` | Update accumulation scores | Path: customerId, attackMac, Query: windowStart, accumulatedScore, decayAccumulatedScore |
| DELETE | `/apt-temporal/{customerId}/{attackMac}` | Delete APT temporal accumulation | Path: customerId, attackMac, Query: windowStart |
| GET | `/apt-temporal/{customerId}/{attackMac}/threat-score` | Get current threat score | Path: customerId, attackMac |

#### Statistics
| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `/attack-source/{customerId}/statistics` | Get attack source weight statistics | Path: customerId |
| GET | `/honeypot-sensitivity/{customerId}/statistics` | Get honeypot sensitivity weight statistics | Path: customerId |
| GET | `/attack-phase/{customerId}/statistics` | Get attack phase port config statistics | Path: customerId |
| GET | `/apt-temporal/{customerId}/statistics` | Get APT temporal accumulation statistics | Path: customerId |

### CustomerPortWeightController
**Base Path:** `/api/port-weights`

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `/{customerId}` | Get customer port weights | Path: customerId |
| GET | `/{customerId}/all` | Get all customer port weights | Path: customerId |
| GET | `/{customerId}/port/{portNumber}` | Get port weight | Path: customerId, portNumber |
| POST | `/{customerId}/batch` | Batch get port weights | Path: customerId, RequestBody: List<Integer> |
| POST | `/{customerId}` | Create port weight config | Path: customerId, RequestBody: CustomerPortWeight |
| POST | `/{customerId}/import` | Batch import port weights | Path: customerId, RequestBody: List<CustomerPortWeight> |
| PUT | `/{customerId}/port/{portNumber}` | Update port weight | Path: customerId, portNumber, Query: weight, updatedBy |
| DELETE | `/{customerId}/port/{portNumber}` | Delete port weight config | Path: customerId, portNumber |
| DELETE | `/{customerId}` | Delete all customer configs | Path: customerId |
| PATCH | `/{customerId}/port/{portNumber}/enabled` | Toggle port config enabled | Path: customerId, portNumber, Query: enabled |
| GET | `/{customerId}/statistics` | Get statistics | Path: customerId |
| GET | `/{customerId}/high-priority` | Get high priority configs | Path: customerId, Query: minPriority |
| GET | `/{customerId}/high-weight` | Get high weight ports | Path: customerId, Query: minWeight |
| GET | `/{customerId}/risk-level/{riskLevel}` | Get by risk level | Path: customerId, riskLevel |
| GET | `/{customerId}/port/{portNumber}/exists` | Check config exists | Path: customerId, portNumber |

### CustomerTimeWeightController
**Base Path:** `/api/time-weights`

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `/customer/{customerId}` | Get customer time weights | Path: customerId |
| GET | `/customer/{customerId}/enabled` | Get enabled time weights | Path: customerId |
| POST | `` | Create time weight config | RequestBody: CustomerTimeWeight |
| POST | `/batch` | Batch create time weights | RequestBody: List<CustomerTimeWeight> |
| PUT | `/{id}` | Update time weight config | Path: id, RequestBody: CustomerTimeWeight |
| DELETE | `/{id}` | Delete time weight config | Path: id |
| DELETE | `/customer/{customerId}` | Delete all customer configs | Path: customerId |
| PATCH | `/{id}/enabled` | Toggle enabled status | Path: id, Query: enabled |
| POST | `/customer/{customerId}/initialize` | Initialize default weights | Path: customerId |
| GET | `/customer/{customerId}/statistics` | Get statistics | Path: customerId |
| PATCH | `/batch/weights` | Batch update weights | RequestBody: List<WeightUpdateRequest> |
| PATCH | `/batch/enabled` | Batch toggle enabled | RequestBody: List<EnabledUpdateRequest> |

### DeviceManagementController
**Base Path:** `/api/v1/devices`

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| POST | `/bind` | Bind device to customer | Query: deviceSerial, customerId, bindReason, bindTime |
| POST | `/unbind` | Unbind device | Query: deviceSerial, unbindReason, unbindTime |
| GET | `/customer` | Get device customer mapping | Query: deviceSerial, timestamp |
| GET | `/history/{deviceSerial}` | Get device mapping history | Path: deviceSerial |
| GET | `/active` | Get active mappings | - |
| POST | `/transfer` | Transfer device to new customer | Query: deviceSerial, newCustomerId, transferReason, transferTime |

---

## 2. Data Ingestion Service (Port 8081)

### ImportController
**Base Path:** `/api/v1/import`

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| POST | `/scenario` | Import with scenario mode | RequestBody: ImportRequest |
| POST | `/migration` | Migration import | RequestBody: List<String> |
| POST | `/completion/{customerId}` | Completion import | Path: customerId, RequestBody: List<String> |
| POST | `/offline` | Offline analysis import | RequestBody: List<String> |
| GET | `/modes` | Get supported import modes | - |
| GET | `/health` | Health check | - |

### LogIngestionController
**Base Path:** `/api/v1/logs`

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| POST | `/ingest` | Ingest single log | RequestBody: String |
| POST | `/batch` | Batch log ingestion | RequestBody: BatchLogRequest |
| GET | `/stats` | Get parse statistics | - |
| POST | `/stats/reset` | Reset parse statistics | - |
| GET | `/health` | Health check | - |
| GET | `/customer-mapping/{devSerial}` | Get customer mapping | Path: devSerial |

---

## 3. Alert Management Service (Port 8082)

### AlertController
**Base Path:** `/api/v1/alerts`

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| POST | `` | Create alert | RequestBody: Alert |
| GET | `/{id}` | Get alert by ID | Path: id |
| GET | `` | Query alerts | Query: customerId, status, severity, startTime, endTime, page, size, sortBy, sortDir |
| PUT | `/{id}/status` | Update alert status | Path: id, Query: status |
| POST | `/{id}/resolve` | Resolve alert | Path: id, RequestBody: ResolveAlertRequest |
| POST | `/{id}/assign` | Assign alert | Path: id, RequestBody: AssignAlertRequest |
| POST | `/{id}/escalate` | Escalate alert | Path: id, RequestBody: EscalateAlertRequest |
| POST | `/{id}/cancel-escalation` | Cancel escalation | Path: id |
| GET | `/analytics` | Get alert analytics | - |
| GET | `/notifications/analytics` | Get notification analytics | - |
| GET | `/escalations/analytics` | Get escalation analytics | - |
| POST | `/notify/email` | Send manual email | RequestBody: SendEmailRequest |
| POST | `/archive` | Archive old alerts | Query: daysOld |

### NotificationConfigController
**Base Path:** `/api/notification-config`

#### SMTP Configuration (Full CRUD)
| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `/smtp` | Get all SMTP configs | - |
| GET | `/smtp/default` | Get default SMTP config | - |
| GET | `/smtp/{id}` | Get SMTP config by ID | Path: id |
| POST | `/smtp` | Create SMTP config | RequestBody: SmtpConfig |
| PUT | `/smtp/{id}` | Update SMTP config | Path: id, RequestBody: SmtpConfig |
| POST | `/smtp/{id}/test` | Test SMTP connection | Path: id |
| POST | `/smtp/refresh-cache` | Refresh SMTP cache | - |

#### Customer Notification Config (Read-Only - DEPRECATED)
| Method | Endpoint | Description | Status |
|--------|----------|-------------|--------|
| GET | `/customer` | Get all customer configs | ✅ Active |
| GET | `/customer/{customerId}` | Get customer config | ✅ Active |
| POST | `/customer` | Create customer config | ❌ DEPRECATED (403) |
| PUT | `/customer/{customerId}` | Update customer config | ❌ DEPRECATED (403) |
| DELETE | `/customer/{customerId}` | Delete customer config | ❌ DEPRECATED (403) |

### IntegrationTestController
**Base Path:** `/api/v1/integration-test`

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `/stats` | Get notification stats | - |
| GET | `/status` | Get integration test status | - |

---

## 4. Customer Management Service (Port 8084)

### CustomerController
**Base Path:** `/api/v1/customers`

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| POST | `` | Create customer | RequestBody: CreateCustomerRequest |
| GET | `/{customerId}/exists` | Check customer exists | Path: customerId |
| GET | `/{customerId}/stats` | Get customer stats | Path: customerId |
| GET | `/{customerId}` | Get customer | Path: customerId |
| GET | `` | Get all customers (paginated) | Query: page, size, sort |
| GET | `/search` | Search customers | Query: keyword, page, size |
| GET | `/status/{status}` | Get customers by status | Path: status, Query: page, size |
| PUT | `/{customerId}` | Update customer | Path: customerId, RequestBody: UpdateCustomerRequest |
| DELETE | `/{customerId}` | Delete customer (soft) | Path: customerId, Query: permanent |
| DELETE | `/{customerId}/hard` | Hard delete customer | Path: customerId |
| PATCH | `/{customerId}` | Patch customer | Path: customerId, RequestBody: Map<String, Object> |

### NotificationConfigController
**Base Path:** `/api/v1/customers/{customerId}/notification-config`

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `` | Get notification config | Path: customerId |
| PUT | `` | Create/update config | Path: customerId, RequestBody: NotificationConfigRequest |
| PATCH | `` | Patch config | Path: customerId, RequestBody: NotificationConfigRequest |
| DELETE | `` | Delete config | Path: customerId |
| POST | `/test` | Test config | Path: customerId |
| PATCH | `/email/toggle` | Toggle email | Path: customerId, Query: enabled |
| PATCH | `/slack/toggle` | Toggle Slack | Path: customerId, Query: enabled |
| PATCH | `/webhook/toggle` | Toggle webhook | Path: customerId, Query: enabled |
| PUT | `/email/enable` | Enable email | Path: customerId |
| PUT | `/email/disable` | Disable email | Path: customerId |
| PUT | `/sms/enable` | Enable SMS | Path: customerId |
| PUT | `/sms/disable` | Disable SMS | Path: customerId |
| GET | `/exists` | Check config exists | Path: customerId |

### DeviceQueryController
**Base Path:** `/api/v1/devices`

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `/{devSerial}/customer` | Find customer by device | Path: devSerial |

### DeviceManagementController
**Base Path:** `/api/v1/customers/{customerId}/devices`

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| POST | `` | Bind device | Path: customerId, RequestBody: DeviceMappingRequest |
| POST | `/batch` | Batch bind devices | Path: customerId, RequestBody: BatchDeviceMappingRequest |
| GET | `` | Get customer devices | Path: customerId, Query: isActive, pageable |
| GET | `/{devSerial}/bound` | Check device bound | Path: customerId, devSerial |
| GET | `/{devSerial}` | Get device | Path: customerId, devSerial |
| DELETE | `/{devSerial}` | Unbind device | Path: customerId, devSerial |
| DELETE | `/batch` | Batch unbind devices | Path: customerId, RequestBody: List<String> |
| POST | `/sync` | Sync device count | Path: customerId |
| GET | `/quota` | Get device quota | Path: customerId |
| PATCH | `/{devSerial}/status` | Toggle device status | Path: customerId, devSerial, Query: isActive |
| PUT | `/{devSerial}` | Update device | Path: customerId, devSerial, RequestBody: DeviceMappingRequest |

### GlobalExceptionHandler
**Global Exception Handling** (No specific endpoints, handles exceptions across all controllers)

---

## 5. API Gateway Service (Port 8080)

### FallbackController
**Base Path:** `/fallback`

| Method | Endpoint | Description | Parameters |
|--------|----------|-------------|------------|
| GET | `/customer-management` | Customer management fallback | - |
| GET | `/data-ingestion` | Data ingestion fallback | - |
| GET | `/threat-assessment` | Threat assessment fallback | - |
| GET | `/alert-management` | Alert management fallback | - |

---

## Summary

### Total Endpoints by Service
- **Threat Assessment**: ~50+ endpoints across 5 controllers
- **Data Ingestion**: 11 endpoints across 2 controllers
- **Alert Management**: 25+ endpoints across 3 controllers
- **Customer Management**: 35+ endpoints across 5 controllers
- **API Gateway**: 4 endpoints across 1 controller

### Total: 125+ REST API endpoints across 21 controllers

### Key Features
- **Multi-tenant**: All services support customer isolation
- **Comprehensive CRUD**: Full lifecycle management for all entities
- **Batch Operations**: Support for bulk operations where applicable
- **Statistics & Analytics**: Rich analytics endpoints for monitoring
- **Health Checks**: Health endpoints for service monitoring
- **Fallback Handling**: Circuit breaker patterns in API gateway
- **Validation**: Input validation with detailed error responses
- **Pagination**: Paginated responses for large datasets
- **Search & Filtering**: Advanced query capabilities

### Important Notes
1. **Code-First Documentation**: This inventory is based on actual controller implementations, not documentation
2. **Deprecated APIs**: Some endpoints in alert-management are deprecated and return 403
3. **Service Separation**: Customer notification configs moved from alert-management to customer-management
4. **Multi-tenant Isolation**: All operations require or support customerId for data isolation
5. **Error Handling**: Comprehensive exception handling with structured error responses