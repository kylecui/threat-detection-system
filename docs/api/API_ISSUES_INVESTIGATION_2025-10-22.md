# API Issues Investigation Report
## 2025-10-22 10:56 - 11:03

---

## Issue #1: Alert Creation Failure ✅ RESOLVED

### Problem
```
POST /api/v1/alerts returned 400 Bad Request
JSON parse error: Cannot deserialize value of type `AlertStatus` from String "OPEN"
```

### Root Cause
**Enum Mismatch**: Test was using `"status": "OPEN"` but the AlertStatus enum only accepts:
- NEW
- ARCHIVED
- ENRICHED
- RESOLVED
- DEDUPLICATED
- ESCALATED
- NOTIFIED

### Solution
**Fix**: Change test payload to use valid status: `"status": "NEW"`

### Verification
```bash
# CORRECT REQUEST (now works):
curl -X POST http://localhost:8082/api/v1/alerts \
  -H "Content-Type: application/json" \
  -d '{
    "title":"Test",
    "description":"Test",
    "severity":"MEDIUM",
    "status":"NEW"  # ← Changed from "OPEN" to "NEW"
  }'

# RESPONSE (HTTP 201):
{
  "id": 1,
  "title": "Test",
  "description": "Test",
  "status": "NEW",
  "severity": "MEDIUM",
  ...
}
```

### Status
✅ **FIXED - Alert creation now works**

---

## Issue #2: Log Ingestion Failure 🔄 INVESTIGATING

### Problem
```
POST /api/v1/logs/ingest returned 400 Bad Request
Response: "Failed to process log"
```

### Root Cause Analysis

#### Discovery 1: Wrong Content-Type
- **What We Tried**: `Content-Type: application/json` with JSON body
- **What It Expects**: `Content-Type: text/plain` with syslog format string
- **Source**: LogIngestionController.java line 46-63

#### Discovery 2: Wrong Format
- **What We Tried**: 
  ```json
  {
    "dev_serial": "TEST-DEV-001",
    "attack_mac": "00:11:22:33:44:55",
    ...
  }
  ```
- **What It Expects**: Syslog key=value format
  ```
  syslog_version=1.10.0,dev_serial=ABC123,log_type=1,sub_type=1,attack_mac=00:11:22:33:44:55,...
  ```

#### Discovery 3: Parsing Failure
- Even with correct format (text/plain + syslog), parsing still fails
- Logs show: `"Failed to parse or process log"`
- Issue is in `LogParserService.parseLog()` method
- Possible causes:
  1. Missing required fields in syslog format
  2. Field format validation failing
  3. DevSerial not mapped to a customer
  4. Kafka producer failing silently

### Testing
```bash
# INCORRECT (JSON format - FAILS):
curl -X POST http://localhost:8080/api/v1/logs/ingest \
  -H "Content-Type: application/json" \
  -d '{"dev_serial":"TEST-DEV-001",...}'
# Response: 400 "Failed to process log"

# PARTIALLY CORRECT (text/plain but still fails):
curl -X POST http://localhost:8080/api/v1/logs/ingest \
  -H "Content-Type: text/plain" \
  -d "syslog_version=1.10.0,dev_serial=TEST-DEV-001,log_type=1,...,log_time=1729598903,..."
# Response: 400 "Failed to process log"
```

### Diagnosis Steps Needed
1. ✅ Verify Content-Type header is `text/plain`
2. ✅ Verify syslog format with required fields:
   - syslog_version=1.10.0
   - dev_serial=TEST-DEV-001  
   - log_type=1 (attack) or 2 (status)
   - attack_mac, attack_ip, response_ip, response_port (for type 1)
3. ⏳ Check if devSerial "TEST-DEV-001" is registered in database
4. ⏳ Check LogParserService.parseLog() implementation
5. ⏳ Check Kafka producer error handling

### Next Steps
1. Verify dev_serial is registered as a device in customer-management
2. Check database for device mappings
3. Enable debug logging for LogParserService
4. Test with a known valid dev_serial from database

### Status
🔄 **IN PROGRESS - Requires data verification**

---

## Summary of Findings

### Issues Fixed
- ✅ Alert-Management database tables (alert_affected_assets) - FIXED
- ✅ Threat-Assessment port mapping (8083→8081) - FIXED
- ✅ Alert creation status enum (OPEN→NEW) - FIXED

### Issues Remaining  
- ⏳ Log Ingestion parsing failure - REQUIRES DATA VERIFICATION

### Action Items

**Priority 1 (Do Now)**:
1. Check if TEST-DEV-001 exists in database
2. If not, use a valid dev_serial or create one
3. Re-test with valid dev_serial

**Priority 2 (If still failing)**:
1. Check LogParserService logs for parsing errors
2. Verify all required syslog fields are present
3. Check Kafka producer logs

---

## Required Fixes for Test Script

The `test_backend_api_happy_path.sh` needs these corrections:

### Fix #1: Alert Status
```bash
# OLD (WRONG):
"status": "OPEN"

# NEW (CORRECT):
"status": "NEW"
```

### Fix #2: Log Ingestion Format
```bash
# OLD (WRONG):
curl -X POST http://localhost:8080/api/v1/logs/ingest \
  -H "Content-Type: application/json" \
  -d '{"dev_serial":"TEST-DEV-001",...}'

# NEW (CORRECT):
curl -X POST http://localhost:8080/api/v1/logs/ingest \
  -H "Content-Type: text/plain" \
  -d "syslog_version=1.10.0,dev_serial=VALID_DEV_SERIAL,log_type=1,..."
```

---

Generated: 2025-10-22 11:03  
Investigation Time: ~7 minutes  
Issues Resolved: 2/3  
Issues Under Investigation: 1/3
