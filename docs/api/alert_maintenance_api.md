# 告警维护API文档

**服务**: Alert Management Service  
**端口**: 8082  
**基础路径**: `/api/v1/alerts/maintenance`  
**认证**: Bearer Token  
**版本**: v1

---

## 目录

1. [系统概述](#1-系统概述)
2. [核心功能](#2-核心功能)
3. [维护操作类型](#3-维护操作类型)
4. [数据模型](#4-数据模型)
5. [API端点列表](#5-api端点列表)
6. [API详细文档](#6-api详细文档)
7. [使用场景](#7-使用场景)
8. [Java客户端完整示例](#8-java客户端完整示例)
9. [最佳实践](#9-最佳实践)
10. [故障排查](#10-故障排查)
11. [相关文档](#11-相关文档)

---

## 1. 系统概述

### 1.1 核心功能

告警维护API提供企业级数据生命周期管理,支持:

- **归档管理**: 历史告警归档、冷热数据分离
- **批量清理**: 过期数据清理、存储空间优化
- **数据迁移**: 数据库迁移、跨环境同步
- **备份恢复**: 自动备份、灾难恢复
- **重复检测**: 去重处理、数据质量管理
- **性能优化**: 索引重建、统计信息更新
- **审计日志**: 维护操作记录、合规追溯

### 1.2 维护流程

```
数据评估 → 备份策略 → 执行维护 → 验证结果 → 审计记录
   ↓          ↓         ↓         ↓         ↓
统计分析   自动备份   归档/清理  数据校验  操作日志
```

### 1.3 技术栈

| 组件 | 技术 | 用途 |
|------|------|------|
| **数据库** | PostgreSQL 15+ | 主数据存储 |
| **归档存储** | S3 / MinIO | 冷数据存储 |
| **任务调度** | Spring @Scheduled | 定时维护任务 |
| **备份工具** | pg_dump / Barman | 数据库备份 |
| **监控告警** | Prometheus + Grafana | 维护任务监控 |
| **审计日志** | ELK Stack | 操作审计 |

---

## 2. 核心功能

### 2.1 数据归档

**目的**: 将历史告警迁移到冷存储,提升查询性能

**归档策略**:
- 按时间范围归档 (30天/90天/365天)
- 按严重性分级归档 (INFO/LOW优先)
- 按状态归档 (仅归档RESOLVED/CLOSED)
- 保留CRITICAL告警不归档

**存储方式**:
- 归档表 (alerts_archive)
- S3对象存储 (Parquet格式)
- 压缩文件 (gzip/zstd)

### 2.2 批量清理

**清理对象**:
- 测试数据 (customerId含test/demo)
- 重复告警 (基于相似度检测)
- 无效告警 (数据完整性检查)
- 临时告警 (TTL过期)

**安全保护**:
- 软删除机制 (deleted_at标记)
- 清理前自动备份
- 分批清理避免锁表
- 清理后统计验证

### 2.3 数据迁移

**迁移场景**:
- 跨数据库迁移 (MySQL → PostgreSQL)
- 环境同步 (开发 → 测试 → 生产)
- 分库分表 (按customerId分片)
- 版本升级 (schema变更)

**迁移工具**:
- Flyway (schema版本管理)
- Liquibase (数据库重构)
- 自定义ETL脚本

### 2.4 重复检测

**检测算法**:
- 完全重复: attackMac + responseIp + responsePort + 时间窗口(5分钟)
- 相似告警: 威胁评分相近 + 攻击者相同 + 时间接近
- 批量重复: 同一批次导入的重复数据

**处理策略**:
- 保留最早/最新的一条
- 合并重复告警(累加attackCount)
- 标记为重复(isDuplicate标志)

---

## 3. 维护操作类型

### 3.1 定期维护 (Scheduled)

| 操作 | 频率 | 执行时间 | 说明 |
|------|------|---------|------|
| **归档低优先级告警** | 每周 | 周日 02:00 | 归档30天前的INFO/LOW |
| **清理测试数据** | 每日 | 每天 03:00 | 删除test/demo客户数据 |
| **重建索引** | 每月 | 每月1日 04:00 | REINDEX优化查询 |
| **更新统计信息** | 每周 | 周一 05:00 | ANALYZE更新执行计划 |
| **备份数据库** | 每日 | 每天 01:00 | 全量/增量备份 |

### 3.2 按需维护 (On-Demand)

- 手动归档指定时间范围
- 批量删除指定客户数据
- 重复告警去重
- 数据完整性修复
- 性能紧急优化

### 3.3 应急维护 (Emergency)

- 数据库空间不足紧急清理
- 性能严重下降紧急优化
- 数据损坏紧急恢复
- 索引损坏紧急重建

---

## 4. 数据模型

### 4.1 维护实体类

#### MaintenanceTask (维护任务)

```java
@Data
@Builder
@Entity
@Table(name = "maintenance_tasks")
public class MaintenanceTask {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String taskType;  // ARCHIVE, CLEANUP, BACKUP, REINDEX
    private String customerId;
    private String status;     // PENDING, RUNNING, COMPLETED, FAILED
    
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    
    @Column(columnDefinition = "jsonb")
    private String parameters;  // JSON参数
    
    private Long processedCount;
    private Long affectedRows;
    private String errorMessage;
    
    private String executedBy;
    private LocalDateTime createdAt;
}
```

#### ArchiveRecord (归档记录)

```java
@Data
@Builder
@Entity
@Table(name = "archive_records")
public class ArchiveRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String customerId;
    private Long archivedCount;
    
    private LocalDateTime archiveStartDate;
    private LocalDateTime archiveEndDate;
    
    private String archiveLocation;  // S3路径或归档表名
    private Long archiveSizeBytes;
    
    private String compressionType;  // GZIP, ZSTD, NONE
    private Boolean isRestored;
    
    private LocalDateTime archivedAt;
    private String archivedBy;
}
```

#### CleanupPolicy (清理策略)

```java
@Data
@Builder
@Entity
@Table(name = "cleanup_policies")
public class CleanupPolicy {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String customerId;
    private String policyName;
    
    private Integer retentionDays;
    private List<String> severities;
    private List<String> statuses;
    
    private Boolean autoCleanup;
    private String cronExpression;
    
    private Boolean requireBackup;
    private Boolean softDelete;
    
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 4.2 请求/响应DTO

#### ArchiveRequest

```java
@Data
public class ArchiveRequest {
    private String customerId;
    private Integer daysOld;           // 归档多少天前的数据
    private List<String> severities;   // 按严重性过滤
    private List<String> statuses;     // 按状态过滤
    private String archiveType;        // TABLE, S3, FILE
    private Boolean createBackup;      // 是否先备份
}
```

#### MaintenanceResponse

```java
@Data
@Builder
public class MaintenanceResponse {
    private Long taskId;
    private String taskType;
    private String status;
    private Long processedCount;
    private Long affectedRows;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Double durationSeconds;
}
```

---

## 5. API端点列表

| 方法 | 端点 | 功能 | 认证 |
|------|------|------|------|
| `POST` | `/api/v1/alerts/maintenance/archive` | 归档历史告警 | ✅ |
| `POST` | `/api/v1/alerts/maintenance/cleanup` | 批量清理告警 | ✅ |
| `POST` | `/api/v1/alerts/maintenance/dedup` | 去重处理 | ✅ |
| `POST` | `/api/v1/alerts/maintenance/reindex` | 重建索引 | ✅ |
| `POST` | `/api/v1/alerts/maintenance/vacuum` | 数据库清理 | ✅ |
| `GET` | `/api/v1/alerts/maintenance/tasks` | 查询维护任务 | ✅ |
| `GET` | `/api/v1/alerts/maintenance/tasks/{id}` | 获取任务详情 | ✅ |
| `POST` | `/api/v1/alerts/maintenance/policies` | 创建清理策略 | ✅ |
| `GET` | `/api/v1/alerts/maintenance/storage/stats` | 存储统计 | ✅ |

---

---

## 6. API详细文档

### 6.1 归档历史告警

**端点**: `POST /api/v1/alerts/maintenance/archive`

**请求体**:

```json
{
  "customerId": "customer-001",
  "daysOld": 90,
  "severities": ["INFO", "LOW"],
  "statuses": ["RESOLVED", "CLOSED"],
  "archiveType": "TABLE",
  "createBackup": true
}
```

**请求示例**:

```bash
# 归档90天前的低优先级已解决告警
curl -X POST http://localhost:8082/api/v1/alerts/maintenance/archive \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "customerId": "customer-001",
    "daysOld": 90,
    "severities": ["INFO", "LOW"],
    "statuses": ["RESOLVED", "CLOSED"],
    "archiveType": "TABLE",
    "createBackup": true
  }'
```

```java
public MaintenanceResponse archiveOldAlerts(String customerId, int daysOld) {
    ArchiveRequest request = ArchiveRequest.builder()
        .customerId(customerId)
        .daysOld(daysOld)
        .severities(Arrays.asList("INFO", "LOW"))
        .statuses(Arrays.asList("RESOLVED", "CLOSED"))
        .archiveType("TABLE")
        .createBackup(true)
        .build();
    
    return restTemplate.postForObject(
        baseUrl + "/maintenance/archive",
        request,
        MaintenanceResponse.class
    );
}
```

**响应示例**:

```json
{
  "taskId": 12345,
  "taskType": "ARCHIVE",
  "status": "COMPLETED",
  "processedCount": 15420,
  "affectedRows": 15420,
  "message": "成功归档15420条告警到alerts_archive表",
  "startedAt": "2024-01-15T10:00:00Z",
  "completedAt": "2024-01-15T10:05:32Z",
  "durationSeconds": 332.5
}
```

---

### 6.2 批量清理告警

**端点**: `POST /api/v1/alerts/maintenance/cleanup`

**请求体**:

```json
{
  "customerId": "customer-001",
  "cleanupType": "TEST_DATA",
  "dryRun": false,
  "batchSize": 1000
}
```

**清理类型**:
- `TEST_DATA`: 清理测试数据 (customerId含test/demo)
- `DUPLICATES`: 清理重复告警
- `INVALID`: 清理无效数据
- `EXPIRED`: 清理过期临时数据

**请求示例**:

```bash
# 清理测试数据 (先dry-run验证)
curl -X POST http://localhost:8082/api/v1/alerts/maintenance/cleanup \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "customerId": "customer-001",
    "cleanupType": "TEST_DATA",
    "dryRun": true,
    "batchSize": 1000
  }'
```

```java
public MaintenanceResponse cleanupTestData(String customerId, boolean dryRun) {
    CleanupRequest request = CleanupRequest.builder()
        .customerId(customerId)
        .cleanupType("TEST_DATA")
        .dryRun(dryRun)
        .batchSize(1000)
        .build();
    
    return restTemplate.postForObject(
        baseUrl + "/maintenance/cleanup",
        request,
        MaintenanceResponse.class
    );
}
```

**响应示例**:

```json
{
  "taskId": 12346,
  "taskType": "CLEANUP",
  "status": "COMPLETED",
  "processedCount": 2340,
  "affectedRows": 2340,
  "message": "成功清理2340条测试数据 (dryRun=false)",
  "startedAt": "2024-01-15T11:00:00Z",
  "completedAt": "2024-01-15T11:02:15Z",
  "durationSeconds": 135.2
}
```

---

### 6.3 去重处理

**端点**: `POST /api/v1/alerts/maintenance/dedup`

**请求体**:

```json
{
  "customerId": "customer-001",
  "timeWindowMinutes": 5,
  "strategy": "KEEP_EARLIEST",
  "autoMerge": true
}
```

**去重策略**:
- `KEEP_EARLIEST`: 保留最早的一条
- `KEEP_LATEST`: 保留最新的一条
- `MERGE_COUNT`: 合并attackCount

**请求示例**:

```bash
curl -X POST http://localhost:8082/api/v1/alerts/maintenance/dedup \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "customerId": "customer-001",
    "timeWindowMinutes": 5,
    "strategy": "MERGE_COUNT",
    "autoMerge": true
  }'
```

```java
public MaintenanceResponse deduplicateAlerts(String customerId) {
    DedupRequest request = DedupRequest.builder()
        .customerId(customerId)
        .timeWindowMinutes(5)
        .strategy("MERGE_COUNT")
        .autoMerge(true)
        .build();
    
    return restTemplate.postForObject(
        baseUrl + "/maintenance/dedup",
        request,
        MaintenanceResponse.class
    );
}
```

**响应示例**:

```json
{
  "taskId": 12347,
  "taskType": "DEDUP",
  "status": "COMPLETED",
  "processedCount": 5680,
  "affectedRows": 1240,
  "message": "检测到1240组重复告警,已合并处理",
  "startedAt": "2024-01-15T12:00:00Z",
  "completedAt": "2024-01-15T12:08:45Z",
  "durationSeconds": 525.3
}
```

---

### 6.4 重建索引

**端点**: `POST /api/v1/alerts/maintenance/reindex`

**请求体**:

```json
{
  "tables": ["alerts", "threat_assessments"],
  "concurrent": true
}
```

**请求示例**:

```bash
curl -X POST http://localhost:8082/api/v1/alerts/maintenance/reindex \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "tables": ["alerts"],
    "concurrent": true
  }'
```

```java
public MaintenanceResponse reindexTables(List<String> tables) {
    ReindexRequest request = ReindexRequest.builder()
        .tables(tables)
        .concurrent(true)
        .build();
    
    return restTemplate.postForObject(
        baseUrl + "/maintenance/reindex",
        request,
        MaintenanceResponse.class
    );
}
```

**响应示例**:

```json
{
  "taskId": 12348,
  "taskType": "REINDEX",
  "status": "COMPLETED",
  "processedCount": 3,
  "affectedRows": 0,
  "message": "成功重建3个索引 (concurrent模式)",
  "startedAt": "2024-01-15T13:00:00Z",
  "completedAt": "2024-01-15T13:15:20Z",
  "durationSeconds": 920.5
}
```

---

### 6.5 数据库清理 (VACUUM)

**端点**: `POST /api/v1/alerts/maintenance/vacuum`

**请求体**:

```json
{
  "tables": ["alerts"],
  "full": false,
  "analyze": true
}
```

**请求示例**:

```bash
curl -X POST http://localhost:8082/api/v1/alerts/maintenance/vacuum \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "tables": ["alerts", "threat_assessments"],
    "full": false,
    "analyze": true
  }'
```

```java
public MaintenanceResponse vacuumDatabase(List<String> tables) {
    VacuumRequest request = VacuumRequest.builder()
        .tables(tables)
        .full(false)
        .analyze(true)
        .build();
    
    return restTemplate.postForObject(
        baseUrl + "/maintenance/vacuum",
        request,
        MaintenanceResponse.class
    );
}
```

**响应示例**:

```json
{
  "taskId": 12349,
  "taskType": "VACUUM",
  "status": "COMPLETED",
  "processedCount": 2,
  "affectedRows": 0,
  "message": "成功VACUUM 2个表,回收空间256MB",
  "startedAt": "2024-01-15T14:00:00Z",
  "completedAt": "2024-01-15T14:12:30Z",
  "durationSeconds": 750.2
}
```

---

### 6.6 查询维护任务

**端点**: `GET /api/v1/alerts/maintenance/tasks`

**查询参数**:
- `customerId` (可选): 客户ID过滤
- `taskType` (可选): 任务类型过滤
- `status` (可选): 状态过滤
- `startDate` (可选): 开始日期
- `endDate` (可选): 结束日期
- `page` (可选): 页码,默认0
- `size` (可选): 每页数量,默认20

**请求示例**:

```bash
# 查询最近的归档任务
curl -X GET "http://localhost:8082/api/v1/alerts/maintenance/tasks?taskType=ARCHIVE&status=COMPLETED&size=10" \
  -H "Authorization: Bearer ${TOKEN}"
```

```java
public List<MaintenanceTask> getMaintenanceTasks(String taskType, String status) {
    String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/maintenance/tasks")
        .queryParam("taskType", taskType)
        .queryParam("status", status)
        .queryParam("size", 10)
        .toUriString();
    
    ResponseEntity<List<MaintenanceTask>> response = restTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        new ParameterizedTypeReference<List<MaintenanceTask>>() {}
    );
    
    return response.getBody();
}
```

**响应示例**:

```json
{
  "content": [
    {
      "id": 12345,
      "taskType": "ARCHIVE",
      "customerId": "customer-001",
      "status": "COMPLETED",
      "scheduledAt": "2024-01-15T10:00:00Z",
      "startedAt": "2024-01-15T10:00:05Z",
      "completedAt": "2024-01-15T10:05:32Z",
      "parameters": "{\"daysOld\":90,\"severities\":[\"INFO\",\"LOW\"]}",
      "processedCount": 15420,
      "affectedRows": 15420,
      "errorMessage": null,
      "executedBy": "system"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 10
}
```

---

### 6.7 存储统计

**端点**: `GET /api/v1/alerts/maintenance/storage/stats`

**请求示例**:

```bash
curl -X GET "http://localhost:8082/api/v1/alerts/maintenance/storage/stats?customerId=customer-001" \
  -H "Authorization: Bearer ${TOKEN}"
```

```java
public StorageStats getStorageStats(String customerId) {
    String url = baseUrl + "/maintenance/storage/stats?customerId=" + customerId;
    return restTemplate.getForObject(url, StorageStats.class);
}
```

**响应示例**:

```json
{
  "customerId": "customer-001",
  "totalAlerts": 125680,
  "activeAlerts": 48520,
  "archivedAlerts": 77160,
  "databaseSizeMB": 2458.5,
  "archiveSizeMB": 1230.2,
  "indexSizeMB": 458.3,
  "estimatedGrowthMBPerDay": 15.2,
  "recommendedArchiveDays": 60,
  "lastArchiveDate": "2024-01-14T02:00:00Z",
  "nextScheduledArchive": "2024-01-21T02:00:00Z"
}
```

---

### 6.8 创建清理策略

**端点**: `POST /api/v1/alerts/maintenance/policies`

**请求体**:

```json
{
  "customerId": "customer-001",
  "policyName": "Monthly Low Priority Cleanup",
  "retentionDays": 30,
  "severities": ["INFO", "LOW"],
  "statuses": ["RESOLVED", "CLOSED"],
  "autoCleanup": true,
  "cronExpression": "0 0 2 1 * *",
  "requireBackup": true,
  "softDelete": true
}
```

**请求示例**:

```bash
curl -X POST http://localhost:8082/api/v1/alerts/maintenance/policies \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "customerId": "customer-001",
    "policyName": "Monthly Low Priority Cleanup",
    "retentionDays": 30,
    "severities": ["INFO", "LOW"],
    "autoCleanup": true,
    "cronExpression": "0 0 2 1 * *"
  }'
```

```java
public CleanupPolicy createCleanupPolicy(String customerId, int retentionDays) {
    CleanupPolicy policy = CleanupPolicy.builder()
        .customerId(customerId)
        .policyName("Auto Cleanup Policy")
        .retentionDays(retentionDays)
        .severities(Arrays.asList("INFO", "LOW"))
        .statuses(Arrays.asList("RESOLVED", "CLOSED"))
        .autoCleanup(true)
        .cronExpression("0 0 2 * * SUN")
        .requireBackup(true)
        .softDelete(true)
        .build();
    
    return restTemplate.postForObject(
        baseUrl + "/maintenance/policies",
        policy,
        CleanupPolicy.class
    );
}
```

**响应示例**:

```json
{
  "id": 123,
  "customerId": "customer-001",
  "policyName": "Monthly Low Priority Cleanup",
  "retentionDays": 30,
  "severities": ["INFO", "LOW"],
  "statuses": ["RESOLVED", "CLOSED"],
  "autoCleanup": true,
  "cronExpression": "0 0 2 1 * *",
  "requireBackup": true,
  "softDelete": true,
  "isActive": true,
  "createdAt": "2024-01-15T15:00:00Z",
  "updatedAt": "2024-01-15T15:00:00Z"
}
```

---

## 7. 使用场景

### 场景1: 自动化归档策略

```java
@Service
@Slf4j
public class AutoArchiveService {
    
    private final MaintenanceClient maintenanceClient;
    
    /**
     * 分级归档策略
     */
    @Scheduled(cron = "0 0 2 * * SUN")  // 每周日凌晨2点
    public void executeAutoArchive() {
        String customerId = "customer-001";
        
        // 1. 归档90天前的INFO级别
        archiveByPriority(customerId, 90, Arrays.asList("INFO"));
        
        // 2. 归档60天前的LOW级别
        archiveByPriority(customerId, 60, Arrays.asList("LOW"));
        
        // 3. 归档30天前的MEDIUM级别
        archiveByPriority(customerId, 30, Arrays.asList("MEDIUM"));
        
        // 4. 检查存储空间
        checkStorageAndAlert(customerId);
    }
    
    private void archiveByPriority(String customerId, int days, List<String> severities) {
        ArchiveRequest request = ArchiveRequest.builder()
            .customerId(customerId)
            .daysOld(days)
            .severities(severities)
            .statuses(Arrays.asList("RESOLVED", "CLOSED"))
            .archiveType("TABLE")
            .createBackup(true)
            .build();
        
        MaintenanceResponse response = maintenanceClient.archive(request);
        
        log.info("Archived {} alerts: severities={}, daysOld={}, duration={}s",
            response.getAffectedRows(),
            severities,
            days,
            response.getDurationSeconds()
        );
    }
    
    private void checkStorageAndAlert(String customerId) {
        StorageStats stats = maintenanceClient.getStorageStats(customerId);
        
        // 数据库超过5GB发送告警
        if (stats.getDatabaseSizeMB() > 5120) {
            log.warn("Database size exceeds 5GB: {}MB, recommend archive",
                stats.getDatabaseSizeMB()
            );
            
            notificationClient.sendEmail(
                "dba@company.com",
                "数据库存储空间告警",
                String.format("当前数据库大小: %.2f GB, 建议执行归档操作",
                    stats.getDatabaseSizeMB() / 1024.0),
                null
            );
        }
    }
}
```

---

### 场景2: 性能优化维护

```java
@Service
@Slf4j
public class PerformanceMaintenanceService {
    
    private final MaintenanceClient maintenanceClient;
    
    /**
     * 每月性能优化任务
     */
    @Scheduled(cron = "0 0 4 1 * *")  // 每月1日凌晨4点
    public void monthlyOptimization() {
        log.info("开始执行月度性能优化...");
        
        // 1. 重建索引
        reindexTables();
        
        // 2. VACUUM清理
        vacuumDatabase();
        
        // 3. 更新统计信息
        analyzeDatabase();
        
        // 4. 检测并清理重复数据
        deduplicateAlerts();
        
        log.info("月度性能优化完成");
    }
    
    private void reindexTables() {
        ReindexRequest request = ReindexRequest.builder()
            .tables(Arrays.asList("alerts", "threat_assessments", "notification_history"))
            .concurrent(true)
            .build();
        
        MaintenanceResponse response = maintenanceClient.reindex(request);
        
        log.info("Reindex completed: tables={}, duration={}s",
            request.getTables().size(),
            response.getDurationSeconds()
        );
    }
    
    private void vacuumDatabase() {
        VacuumRequest request = VacuumRequest.builder()
            .tables(Arrays.asList("alerts", "threat_assessments"))
            .full(false)
            .analyze(true)
            .build();
        
        MaintenanceResponse response = maintenanceClient.vacuum(request);
        
        log.info("Vacuum completed: {}", response.getMessage());
    }
    
    private void analyzeDatabase() {
        // 执行PostgreSQL ANALYZE更新统计信息
        jdbcTemplate.execute("ANALYZE alerts");
        jdbcTemplate.execute("ANALYZE threat_assessments");
        
        log.info("Database statistics updated");
    }
    
    private void deduplicateAlerts() {
        DedupRequest request = DedupRequest.builder()
            .customerId("customer-001")
            .timeWindowMinutes(5)
            .strategy("MERGE_COUNT")
            .autoMerge(true)
            .build();
        
        MaintenanceResponse response = maintenanceClient.dedup(request);
        
        if (response.getAffectedRows() > 0) {
            log.warn("Found duplicates: processed={}, merged={}",
                response.getProcessedCount(),
                response.getAffectedRows()
            );
        }
    }
}
```

---

### 场景3: 灾难恢复演练

```java
@Service
@Slf4j
public class DisasterRecoveryService {
    
    private final MaintenanceClient maintenanceClient;
    private final BackupService backupService;
    
    /**
     * 备份与恢复演练
     */
    public void disasterRecoveryDrill() {
        String customerId = "customer-001";
        
        try {
            // 1. 创建全量备份
            log.info("Step 1: Creating full backup...");
            String backupId = createFullBackup(customerId);
            
            // 2. 模拟数据删除 (在测试环境)
            log.info("Step 2: Simulating data loss...");
            simulateDataLoss(customerId);
            
            // 3. 从备份恢复
            log.info("Step 3: Restoring from backup...");
            restoreFromBackup(customerId, backupId);
            
            // 4. 验证数据完整性
            log.info("Step 4: Validating data integrity...");
            validateDataIntegrity(customerId);
            
            log.info("✅ Disaster recovery drill completed successfully");
            
        } catch (Exception e) {
            log.error("❌ Disaster recovery drill failed", e);
            throw new RuntimeException("Recovery drill failed", e);
        }
    }
    
    private String createFullBackup(String customerId) {
        // 使用pg_dump创建备份
        String backupFile = String.format("/backups/alerts_%s_%s.sql",
            customerId,
            LocalDate.now()
        );
        
        String command = String.format(
            "pg_dump -h localhost -U postgres -d threat_detection -t alerts -f %s",
            backupFile
        );
        
        executeCommand(command);
        
        log.info("Backup created: {}", backupFile);
        return backupFile;
    }
    
    private void simulateDataLoss(String customerId) {
        // 在测试环境删除最近7天的数据
        CleanupRequest request = CleanupRequest.builder()
            .customerId(customerId)
            .cleanupType("RECENT_DATA")
            .daysOld(7)
            .dryRun(false)
            .build();
        
        MaintenanceResponse response = maintenanceClient.cleanup(request);
        
        log.info("Simulated data loss: deleted {} records", response.getAffectedRows());
    }
    
    private void restoreFromBackup(String customerId, String backupFile) {
        // 使用psql恢复备份
        String command = String.format(
            "psql -h localhost -U postgres -d threat_detection -f %s",
            backupFile
        );
        
        executeCommand(command);
        
        log.info("Data restored from: {}", backupFile);
    }
    
    private void validateDataIntegrity(String customerId) {
        // 查询数据量验证
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM alerts WHERE customer_id = ?",
            Long.class,
            customerId
        );
        
        log.info("Data integrity check: {} alerts found", count);
        
        if (count == null || count == 0) {
            throw new IllegalStateException("Data validation failed: no alerts found");
        }
    }
}
```

---

---

## 归档策略建议

### 保留策略

| 告警严重等级 | 推荐保留时间 | 说明 |
|------------|------------|------|
| **CRITICAL** | 365天+ | 长期保留用于审计 |
| **HIGH** | 180天 | 半年内可查询 |
| **MEDIUM** | 90天 | 季度数据 |
| **LOW** | 30天 | 月度数据 |
| **INFO** | 7天 | 仅保留最近记录 |

### 定时归档任务

```java
import org.springframework.scheduling.annotation.Scheduled;

public class ArchiveScheduler {
    
    /**
     * 每周日凌晨2点执行归档
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void weeklyArchive() {
        // 归档30天前的LOW和INFO级别告警
        archiveLowPriorityAlerts(30);
        
        // 归档90天前的MEDIUM级别告警
        archiveMediumPriorityAlerts(90);
        
        // 归档180天前的HIGH级别告警
        archiveHighPriorityAlerts(180);
        
        // CRITICAL告警不自动归档,需手动处理
    }
    
    private void archiveLowPriorityAlerts(int days) {
        // 实现逻辑: 按严重等级过滤归档
    }
}
```

### 归档前备份

```java
public class ArchiveWithBackup {
    
    /**
     * 归档前导出备份
     */
    public void archiveWithBackup(int daysOld) {
        // 1. 导出待归档数据
        List<Alert> toArchive = getAlertsOlderThan(daysOld);
        exportToBackup(toArchive);
        
        // 2. 执行归档
        int archived = archiveClient.archiveOldAlerts(daysOld);
        
        // 3. 验证备份
        if (archived == toArchive.size()) {
            System.out.println("✅ 归档完成,已备份 " + archived + " 条记录");
        } else {
            System.err.println("⚠️ 归档数量不匹配,请检查!");
        }
    }
    
    private void exportToBackup(List<Alert> alerts) {
        // 导出到CSV或数据库归档表
    }
}
```

---

## 使用场景

### 场景1: 数据库性能优化

```java
public class PerformanceOptimizer {
    
    /**
     * 通过归档提升查询性能
     */
    public void optimizeDatabase() {
        // 统计活跃告警数量
        long activeCount = alertService.countActiveAlerts();
        
        if (activeCount > 100000) {
            System.out.println("⚠️ 活跃告警数量过多,开始归档...");
            
            // 归档60天前的告警
            int archived = archiveClient.archiveOldAlerts(60);
            
            System.out.println("✅ 归档完成,释放 " + archived + " 条记录");
            System.out.println("预计查询性能提升: ~" + (archived * 100 / activeCount) + "%");
        }
    }
}
```

### 场景2: 合规审计

```java
public class ComplianceAuditor {
    
    /**
     * 合规审计前确保数据完整
     */
    public void prepareForAudit(int auditYear) {
        // 确保审计年度的数据未被归档
        int daysToKeep = calculateDaysSince(auditYear);
        
        System.out.println("审计年度: " + auditYear);
        System.out.println("保留天数: " + daysToKeep);
        
        // 只归档审计范围外的数据
        if (daysToKeep > 365) {
            int safeArchiveDays = daysToKeep + 30; // 留30天缓冲
            archiveClient.archiveOldAlerts(safeArchiveDays);
        }
    }
}
```

---

## 相关文档

- **[告警CRUD API](./alert_crud_api.md)** - 告警基本操作
- **[告警分析API](./alert_analytics_api.md)** - 告警统计
- **[告警概述](./alert_management_overview.md)** - 系统架构

---

**文档结束**

*最后更新: 2025-01-16*

## 8. Java客户端完整示例

### AlertMaintenanceClient

```java
package com.threatdetection.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * 告警维护API客户端
 */
@Slf4j
@Component
public class AlertMaintenanceClient {
    
    private static final String BASE_URL = "http://localhost:8082/api/v1/alerts/maintenance";
    private final RestTemplate restTemplate;
    
    public AlertMaintenanceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * 归档历史告警
     */
    public MaintenanceResponse archive(ArchiveRequest request) {
        HttpEntity<ArchiveRequest> httpRequest = new HttpEntity<>(request);
        
        ResponseEntity<MaintenanceResponse> response = restTemplate.postForEntity(
            BASE_URL + "/archive",
            httpRequest,
            MaintenanceResponse.class
        );
        
        MaintenanceResponse result = response.getBody();
        
        log.info("Archive completed: taskId={}, affected={}, duration={}s",
            result.getTaskId(),
            result.getAffectedRows(),
            result.getDurationSeconds()
        );
        
        return result;
    }
    
    /**
     * 归档指定天数前的低优先级告警
     */
    public MaintenanceResponse archiveLowPriority(String customerId, int daysOld) {
        ArchiveRequest request = ArchiveRequest.builder()
            .customerId(customerId)
            .daysOld(daysOld)
            .severities(Arrays.asList("INFO", "LOW"))
            .statuses(Arrays.asList("RESOLVED", "CLOSED"))
            .archiveType("TABLE")
            .createBackup(true)
            .build();
        
        return archive(request);
    }
    
    /**
     * 批量清理告警
     */
    public MaintenanceResponse cleanup(CleanupRequest request) {
        HttpEntity<CleanupRequest> httpRequest = new HttpEntity<>(request);
        
        ResponseEntity<MaintenanceResponse> response = restTemplate.postForEntity(
            BASE_URL + "/cleanup",
            httpRequest,
            MaintenanceResponse.class
        );
        
        MaintenanceResponse result = response.getBody();
        
        log.info("Cleanup completed: type={}, affected={}, dryRun={}",
            request.getCleanupType(),
            result.getAffectedRows(),
            request.isDryRun()
        );
        
        return result;
    }
    
    /**
     * 清理测试数据
     */
    public MaintenanceResponse cleanupTestData(String customerId, boolean dryRun) {
        CleanupRequest request = CleanupRequest.builder()
            .customerId(customerId)
            .cleanupType("TEST_DATA")
            .dryRun(dryRun)
            .batchSize(1000)
            .build();
        
        return cleanup(request);
    }
    
    /**
     * 去重处理
     */
    public MaintenanceResponse dedup(DedupRequest request) {
        HttpEntity<DedupRequest> httpRequest = new HttpEntity<>(request);
        
        ResponseEntity<MaintenanceResponse> response = restTemplate.postForEntity(
            BASE_URL + "/dedup",
            httpRequest,
            MaintenanceResponse.class
        );
        
        MaintenanceResponse result = response.getBody();
        
        log.info("Dedup completed: duplicates={}, merged={}",
            result.getProcessedCount(),
            result.getAffectedRows()
        );
        
        return result;
    }
    
    /**
     * 重建索引
     */
    public MaintenanceResponse reindex(ReindexRequest request) {
        HttpEntity<ReindexRequest> httpRequest = new HttpEntity<>(request);
        
        ResponseEntity<MaintenanceResponse> response = restTemplate.postForEntity(
            BASE_URL + "/reindex",
            httpRequest,
            MaintenanceResponse.class
        );
        
        MaintenanceResponse result = response.getBody();
        
        log.info("Reindex completed: tables={}, duration={}s",
            request.getTables().size(),
            result.getDurationSeconds()
        );
        
        return result;
    }
    
    /**
     * 重建所有表索引
     */
    public MaintenanceResponse reindexAll() {
        ReindexRequest request = ReindexRequest.builder()
            .tables(Arrays.asList("alerts", "threat_assessments", "notification_history"))
            .concurrent(true)
            .build();
        
        return reindex(request);
    }
    
    /**
     * VACUUM清理
     */
    public MaintenanceResponse vacuum(VacuumRequest request) {
        HttpEntity<VacuumRequest> httpRequest = new HttpEntity<>(request);
        
        ResponseEntity<MaintenanceResponse> response = restTemplate.postForEntity(
            BASE_URL + "/vacuum",
            httpRequest,
            MaintenanceResponse.class
        );
        
        MaintenanceResponse result = response.getBody();
        
        log.info("Vacuum completed: {}", result.getMessage());
        
        return result;
    }
    
    /**
     * 查询维护任务
     */
    public List<MaintenanceTask> getTasks(String taskType, String status) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/tasks")
            .queryParam("taskType", taskType)
            .queryParam("status", status)
            .queryParam("size", 20)
            .toUriString();
        
        ResponseEntity<PagedResponse<MaintenanceTask>> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<PagedResponse<MaintenanceTask>>() {}
        );
        
        return response.getBody().getContent();
    }
    
    /**
     * 获取任务详情
     */
    public MaintenanceTask getTask(Long taskId) {
        String url = BASE_URL + "/tasks/" + taskId;
        
        MaintenanceTask task = restTemplate.getForObject(url, MaintenanceTask.class);
        
        log.info("Task details: id={}, type={}, status={}",
            task.getId(),
            task.getTaskType(),
            task.getStatus()
        );
        
        return task;
    }
    
    /**
     * 获取存储统计
     */
    public StorageStats getStorageStats(String customerId) {
        String url = BASE_URL + "/storage/stats?customerId=" + customerId;
        
        StorageStats stats = restTemplate.getForObject(url, StorageStats.class);
        
        log.info("Storage stats: dbSize={}MB, archiveSize={}MB, total={}",
            stats.getDatabaseSizeMB(),
            stats.getArchiveSizeMB(),
            stats.getTotalAlerts()
        );
        
        return stats;
    }
    
    /**
     * 创建清理策略
     */
    public CleanupPolicy createCleanupPolicy(CleanupPolicy policy) {
        HttpEntity<CleanupPolicy> httpRequest = new HttpEntity<>(policy);
        
        ResponseEntity<CleanupPolicy> response = restTemplate.postForEntity(
            BASE_URL + "/policies",
            httpRequest,
            CleanupPolicy.class
        );
        
        CleanupPolicy created = response.getBody();
        
        log.info("Cleanup policy created: id={}, name={}, retentionDays={}",
            created.getId(),
            created.getPolicyName(),
            created.getRetentionDays()
        );
        
        return created;
    }
}
```

---

## 9. 最佳实践

### ✅ 推荐做法

#### 9.1 归档前先备份

```java
// ✅ 正确: 归档前创建备份
public void safeArchive(String customerId, int daysOld) {
    ArchiveRequest request = ArchiveRequest.builder()
        .customerId(customerId)
        .daysOld(daysOld)
        .createBackup(true)  // 自动备份
        .archiveType("TABLE")
        .build();
    
    MaintenanceResponse response = maintenanceClient.archive(request);
    
    log.info("Archive completed with backup: affected={}", response.getAffectedRows());
}
```

#### 9.2 清理前使用DryRun验证

```java
// ✅ 正确: 先DryRun验证,再实际清理
public void safeCleanup(String customerId) {
    CleanupRequest dryRunRequest = CleanupRequest.builder()
        .customerId(customerId)
        .cleanupType("TEST_DATA")
        .dryRun(true)  // 先验证
        .build();
    
    MaintenanceResponse dryRunResult = maintenanceClient.cleanup(dryRunRequest);
    
    log.info("DryRun result: will delete {} records", dryRunResult.getAffectedRows());
    
    // 确认后再实际执行
    if (dryRunResult.getAffectedRows() < 10000) {
        dryRunRequest.setDryRun(false);
        MaintenanceResponse result = maintenanceClient.cleanup(dryRunRequest);
        log.info("Cleanup completed: deleted={}", result.getAffectedRows());
    } else {
        log.warn("Too many records to delete: {}, skipping", dryRunResult.getAffectedRows());
    }
}
```

#### 9.3 分级归档策略

```java
// ✅ 正确: 按严重性分级归档
@Scheduled(cron = "0 0 2 * * SUN")
public void tieredArchive() {
    String customerId = "customer-001";
    
    // INFO: 7天
    archiveByPriority(customerId, 7, Arrays.asList("INFO"));
    
    // LOW: 30天
    archiveByPriority(customerId, 30, Arrays.asList("LOW"));
    
    // MEDIUM: 90天
    archiveByPriority(customerId, 90, Arrays.asList("MEDIUM"));
    
    // HIGH: 180天
    archiveByPriority(customerId, 180, Arrays.asList("HIGH"));
    
    // CRITICAL: 不自动归档
}
```

#### 9.4 监控维护任务状态

```java
// ✅ 正确: 监控长时间运行的任务
@Scheduled(fixedRate = 300000)  // 每5分钟检查
public void monitorMaintenanceTasks() {
    List<MaintenanceTask> runningTasks = maintenanceClient.getTasks(null, "RUNNING");
    
    for (MaintenanceTask task : runningTasks) {
        long durationMinutes = ChronoUnit.MINUTES.between(
            task.getStartedAt(),
            LocalDateTime.now()
        );
        
        // 超过30分钟发送告警
        if (durationMinutes > 30) {
            log.warn("Long running task detected: id={}, type={}, duration={}min",
                task.getId(),
                task.getTaskType(),
                durationMinutes
            );
            
            alertOps("维护任务执行超时", task);
        }
    }
}
```

---

### ❌ 避免的做法

#### 9.5 避免在业务高峰期维护

```java
// ❌ 错误: 在业务高峰期执行维护
@Scheduled(cron = "0 0 14 * * *")  // 下午2点 - 业务高峰
public void badSchedule() {
    maintenanceClient.reindexAll();  // 可能影响查询性能
}

// ✅ 正确: 在凌晨低峰期执行
@Scheduled(cron = "0 0 3 * * *")  // 凌晨3点
public void goodSchedule() {
    maintenanceClient.reindexAll();
}
```

#### 9.6 避免归档活跃数据

```java
// ❌ 错误: 归档OPEN状态的告警
ArchiveRequest badRequest = ArchiveRequest.builder()
    .daysOld(30)
    .statuses(Arrays.asList("OPEN", "IN_PROGRESS"))  // 错误!
    .build();

// ✅ 正确: 只归档已关闭的告警
ArchiveRequest goodRequest = ArchiveRequest.builder()
    .daysOld(30)
    .statuses(Arrays.asList("RESOLVED", "CLOSED"))  // 正确
    .build();
```

#### 9.7 避免频繁VACUUM FULL

```java
// ❌ 错误: 每天VACUUM FULL (锁表时间长)
@Scheduled(cron = "0 0 4 * * *")
public void dailyVacuumFull() {
    VacuumRequest request = VacuumRequest.builder()
        .full(true)  // FULL模式锁表
        .build();
    maintenanceClient.vacuum(request);
}

// ✅ 正确: 定期普通VACUUM,季度VACUUM FULL
@Scheduled(cron = "0 0 4 * * SUN")  // 每周
public void weeklyVacuum() {
    VacuumRequest request = VacuumRequest.builder()
        .full(false)
        .analyze(true)
        .build();
    maintenanceClient.vacuum(request);
}

@Scheduled(cron = "0 0 4 1 */3 *")  // 每季度
public void quarterlyVacuumFull() {
    VacuumRequest request = VacuumRequest.builder()
        .full(true)
        .build();
    maintenanceClient.vacuum(request);
}
```

---

## 10. 故障排查

### 10.1 归档任务失败: 磁盘空间不足

**症状**: 归档任务状态为FAILED,错误信息"No space left on device"

**诊断步骤**:

```bash
# 检查磁盘空间
df -h /var/lib/postgresql

# 检查归档表大小
psql -U postgres -d threat_detection -c "
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE tablename LIKE '%archive%'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
"
```

**解决方案**:

```bash
# 1. 清理旧备份
find /backups -name "*.sql" -mtime +30 -delete

# 2. 压缩归档表
psql -U postgres -d threat_detection -c "
VACUUM FULL alerts_archive;
"

# 3. 导出归档数据到S3
pg_dump -t alerts_archive | gzip | aws s3 cp - s3://archive-bucket/alerts_archive.sql.gz

# 4. 清空归档表
psql -U postgres -d threat_detection -c "
TRUNCATE alerts_archive;
"
```

---

### 10.2 REINDEX任务锁死

**症状**: REINDEX任务长时间RUNNING,阻塞其他查询

**诊断步骤**:

```sql
-- 查看锁情况
SELECT 
    pid,
    usename,
    application_name,
    state,
    query,
    wait_event_type,
    wait_event
FROM pg_stat_activity
WHERE state != 'idle'
  AND query LIKE '%REINDEX%';

-- 查看索引重建进度
SELECT 
    phase,
    blocks_total,
    blocks_done,
    tuples_total,
    tuples_done
FROM pg_stat_progress_create_index;
```

**解决方案**:

```java
// 1. 使用CONCURRENT模式避免锁表
ReindexRequest request = ReindexRequest.builder()
    .tables(Arrays.asList("alerts"))
    .concurrent(true)  // 不锁表
    .build();

maintenanceClient.reindex(request);
```

```sql
-- 2. 如果必须终止,先取消后台任务
SELECT pg_cancel_backend(pid) 
FROM pg_stat_activity 
WHERE query LIKE '%REINDEX%';

-- 3. 或强制终止
SELECT pg_terminate_backend(pid) 
FROM pg_stat_activity 
WHERE query LIKE '%REINDEX%';
```

---

### 10.3 去重任务内存溢出

**症状**: DEDUP任务OOM错误,JVM堆内存耗尽

**诊断步骤**:

```bash
# 检查JVM内存使用
jstat -gcutil <pid> 1000 10

# 查看任务处理数量
curl http://localhost:8082/api/v1/alerts/maintenance/tasks/12347
```

**解决方案**:

```java
// 1. 分批处理去重
public void dedupInBatches(String customerId) {
    LocalDateTime start = LocalDateTime.now().minusDays(30);
    LocalDateTime end = LocalDateTime.now();
    
    // 每次处理7天数据
    while (start.isBefore(end)) {
        LocalDateTime batchEnd = start.plusDays(7);
        
        DedupRequest request = DedupRequest.builder()
            .customerId(customerId)
            .startTime(start)
            .endTime(batchEnd)
            .timeWindowMinutes(5)
            .strategy("MERGE_COUNT")
            .build();
        
        maintenanceClient.dedup(request);
        
        log.info("Dedup batch completed: {} to {}", start, batchEnd);
        
        start = batchEnd;
    }
}
```

```yaml
# 2. 增加JVM堆内存
JAVA_OPTS: "-Xmx4g -Xms2g"
```

```sql
-- 3. 使用数据库级去重(更高效)
DELETE FROM alerts a USING (
    SELECT MIN(id) as id, attack_mac, response_ip, response_port
    FROM alerts
    WHERE customer_id = 'customer-001'
      AND created_at >= NOW() - INTERVAL '30 days'
    GROUP BY attack_mac, response_ip, response_port, 
             DATE_TRUNC('minute', created_at)
    HAVING COUNT(*) > 1
) b
WHERE a.attack_mac = b.attack_mac
  AND a.response_ip = b.response_ip
  AND a.response_port = b.response_port
  AND a.id != b.id;
```

---

## 11. 相关文档

| 文档 | 说明 |
|------|------|
| [告警CRUD API](./alert_crud_api.md) | 告警基本操作 |
| [告警分析API](./alert_analytics_api.md) | 告警统计分析 |
| [告警生命周期API](./alert_lifecycle_api.md) | 状态管理 |
| [威胁评估查询API](./threat_assessment_query_api.md) | 威胁数据查询 |
| [PostgreSQL文档](https://www.postgresql.org/docs/) | 数据库官方文档 |

---

**文档结束**

*最后更新: 2025-10-16*
