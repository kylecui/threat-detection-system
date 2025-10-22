# Immediate Action Plan: Code Cleanup Phase 1
## Quick Fixes (Estimated: 1-2 hours)

**Objective:** Remove anti-patterns and improve code quality without changing functionality  
**Risk Level:** LOW (existing tests should still pass)  
**Start:** Now | **Complete by:** Today

---

## Action 1: Clean Alert.java (15 minutes)

### Current Issues
- ❌ @Data + @Getter(NONE) contradiction
- ❌ Unnecessary try-catch blocks
- ❌ Redundant @JsonIgnore and @JsonIgnoreProperties

### Fix Steps

**Step 1: Backup current file**
```bash
cp services/alert-management/src/main/java/com/threatdetection/alert/model/Alert.java \
   services/alert-management/src/main/java/com/threatdetection/alert/model/Alert.java.backup
```

**Step 2: Remove contradictions from Alert.java**
- Remove line: `@Getter(AccessLevel.NONE)` from both collection fields
- Remove line: `@JsonIgnoreProperties({"affectedAssets", "recommendations"})` from class annotation
- Keep: `@JsonIgnore` on collection fields (sufficient)
- Remove: All try-catch blocks from custom getters

**After:** Alert.java should look like:
```java
@Entity
@Table(name = "alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert implements Serializable {
    // ... other fields ...
    
    @ElementCollection(fetch = FetchType.EAGER)
    @JsonIgnore
    private List<String> affectedAssets = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @JsonIgnore
    private List<String> recommendations = new ArrayList<>();
    
    // ✅ NO custom getters needed - Lombok @Data generates them
}
```

**Step 3: Verify compilation**
```bash
cd services/alert-management
mvn clean compile
# Should see [INFO] BUILD SUCCESS
```

**Step 4: Run tests**
```bash
mvn test
# All existing tests should pass
```

---

## Action 2: Clean AlertService.java (15 minutes)

### Current Issues
- ❌ Unnecessary Hibernate.initialize() calls
- ⚠️ Class-level @Transactional
- ❌ Incomplete caching strategy (@CacheEvict without @Cacheable)

### Fix Steps

**Step 1: Remove unnecessary initialize() calls**

**Before:**
```java
public Optional<Alert> findById(Long id) {
    logger.info("Finding alert: {}", id);
    Optional<Alert> alert = alertRepository.findById(id);
    alert.ifPresent(a -> {
        Hibernate.initialize(a.getAffectedAssets());
        Hibernate.initialize(a.getRecommendations());
    });
    return alert;
}
```

**After:**
```java
public Optional<Alert> findById(Long id) {
    logger.info("Finding alert: {}", id);
    // Collections are EAGER loaded, no initialize needed
    return alertRepository.findById(id);
}
```

**Step 2: Remove class-level @Transactional, add method-level for writes**

**Before:**
```java
@Service
@Transactional  // ❌ Applies to ALL methods
public class AlertService {
    // ...
}
```

**After:**
```java
@Service
public class AlertService {
    
    // Read method - no @Transactional needed
    public Optional<Alert> findById(Long id) { ... }
    
    // Write method - needs @Transactional
    @Transactional
    public Alert updateStatus(Long id, AlertStatus status) { ... }
    
    @Transactional
    public Alert resolveAlert(Long id, String resolutionNotes) { ... }
}
```

**Step 3: Remove @CacheEvict annotations (partial caching is problematic)**

**Before:**
```java
@CacheEvict(cacheNames = "alerts", key = "#id")
public Alert updateStatus(Long id, AlertStatus status) { ... }
```

**After:**
```java
// ✅ Remove @CacheEvict - either implement full caching or remove entirely
public Alert updateStatus(Long id, AlertStatus status) { ... }
```

**Step 4: Verify and test**
```bash
mvn clean test
# Should see all tests passing
```

---

## Action 3: Clean AlertController.java (15 minutes)

### Current Issues
- ❌ @Transactional on controller (anti-pattern)
- ⚠️ Catching generic Exception
- ⚠️ Returning raw Entity instead of DTO (skip for now)

### Fix Steps

**Step 1: Remove @Transactional from controller method**

**Before:**
```java
@GetMapping("/{id}")
@Transactional(readOnly = true)  // ❌ Remove this
public ResponseEntity<?> getAlert(@PathVariable Long id) {
    // ...
}
```

**After:**
```java
@GetMapping("/{id}")
// ✅ No @Transactional - service layer handles transactions
public ResponseEntity<?> getAlert(@PathVariable Long id) {
    // ...
}
```

**Step 2: Improve exception handling (optional for this phase)**

Current code catches all Exception - acceptable for now, but could be more specific:

```java
try {
    Optional<Alert> alert = alertService.findById(id);
    if (alert.isPresent()) {
        return ResponseEntity.ok(alert.get());
    } else {
        return ResponseEntity.notFound().build();
    }
} catch (AlertException e) {  // More specific exception type
    logger.error("Error fetching alert: {}", id, e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body("Error fetching alert: " + e.getMessage());
}
```

**Step 3: Verify and test**
```bash
mvn clean test
# All tests should still pass
```

---

## Action 4: Fix application-docker.properties (10 minutes)

### Current Issues
- ❌ DEBUG level logging in production profile
- ❌ Expensive SQL formatting enabled
- ❌ Missing profile separation

### Fix Steps

**Step 1: Update application-docker.properties**

**Remove these lines (too verbose for production):**
```properties
# ❌ Remove - too expensive
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
logging.level.org.springframework.transaction=DEBUG
logging.level.com.zaxxer.hikari=DEBUG
```

**Replace with production-appropriate logging:**
```properties
# ✅ Production logging levels
spring.jpa.properties.hibernate.format_sql=false
logging.level.root=WARN
logging.level.com.threatdetection=INFO
logging.level.org.springframework.web=INFO
logging.level.org.hibernate.SQL=WARN
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=WARN

# ✅ Keep - necessary for lazy loading fix
spring.jpa.open-in-view=true
```

**Step 2: Restart alert-management service to apply config**

```bash
cd docker
docker-compose stop alert-management
docker-compose rm -f alert-management
docker-compose up -d alert-management

# Wait for startup
sleep 10

# Verify health
curl http://localhost:8082/actuator/health
```

---

## Verification Steps

### 1. Build & Compile Check
```bash
cd services/alert-management
mvn clean package
# Should see [INFO] BUILD SUCCESS
```

### 2. Run Unit Tests
```bash
mvn test
# All tests should pass
```

### 3. Run Happy Path Test
```bash
cd /home/kylecui/threat-detection-system
bash scripts/test_backend_api_happy_path.sh

# Expected output: 16/17 PASS (or better)
# Alert Management section: 4/4 PASS
```

### 4. Manual Verification
```bash
# Test GET /alerts endpoint
curl -X GET http://localhost:8082/api/v1/alerts/1

# Should return 200 with alert JSON (not 500)
```

### 5. Check Logs
```bash
# View alert-management logs
docker logs alert-management-service

# Should see:
# ✅ INFO level logs (not DEBUG)
# ❌ NO DEBUG/TRACE SQL logging
# ❌ NO stack traces from initialization
```

---

## Rollback Plan

If tests fail after changes:

```bash
# Restore from backup
cp services/alert-management/src/main/java/com/threatdetection/alert/model/Alert.java.backup \
   services/alert-management/src/main/java/com/threatdetection/alert/model/Alert.java

# Rebuild
cd docker
docker system prune -f
docker-compose build --no-cache alert-management
docker-compose up -d alert-management

# Re-verify
bash scripts/test_backend_api_happy_path.sh
```

---

## Files to Modify

1. ✏️ `services/alert-management/src/main/java/com/threatdetection/alert/model/Alert.java`
   - Remove @Getter(NONE), try-catch, redundant @JsonIgnore...

2. ✏️ `services/alert-management/src/main/java/com/threatdetection/alert/service/alert/AlertService.java`
   - Remove initialize(), move @Transactional, remove @CacheEvict

3. ✏️ `services/alert-management/src/main/java/com/threatdetection/alert/controller/AlertController.java`
   - Remove @Transactional from method

4. ✏️ `services/alert-management/src/main/resources/application-docker.properties`
   - Update logging levels, remove DEBUG

---

## Estimated Timeline

| Step | Task | Time | Cumulative |
|------|------|------|-----------|
| 1 | Alert.java cleanup | 10 min | 10 min |
| 2 | AlertService.java cleanup | 10 min | 20 min |
| 3 | AlertController.java cleanup | 10 min | 30 min |
| 4 | Config update & restart | 10 min | 40 min |
| 5 | Testing & verification | 15 min | 55 min |
| 6 | Troubleshooting (if needed) | 15-30 min | 70-85 min |
| **Total** | | | **~1.5 hours** |

---

## Next Steps After Phase 1

✅ **Phase 1:** Code cleanup (THIS PLAN)  
⏳ **Phase 2:** DTO pattern introduction (~4-5 hours, next sprint)  
⏳ **Phase 3:** Build script automation (~2-3 hours)  
⏳ **Phase 4:** Full test suite expansion

---

## Go/No-Go Checklist

**READY TO START PHASE 1?** ✅ YES, proceed

**Prerequisites:**
- [ ] All services running healthy
- [ ] Happy path tests passing (16/17)
- [ ] Code backup created
- [ ] Team notified (if applicable)

**Proceed with:** Above action items
