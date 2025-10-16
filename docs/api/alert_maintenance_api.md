# 告警维护API文档

**服务**: Alert Management Service (8082)  
**基础路径**: `/api/v1/alerts`

---

## 归档旧告警

**端点**: `POST /api/v1/alerts/archive`

### 功能说明

将指定天数前的已解决告警归档,用于:
- 清理历史数据
- 提高查询性能
- 数据备份和合规

### 查询参数

| 参数 | 类型 | 必需 | 默认值 | 说明 |
|-----|------|------|--------|------|
| `daysOld` | Integer | ❌ | 30 | 归档多少天前的告警 (≥1) |

### 请求示例 (curl)

```bash
# 归档30天前的已解决告警 (默认)
curl -X POST http://localhost:8082/api/v1/alerts/archive

# 归档90天前的告警
curl -X POST "http://localhost:8082/api/v1/alerts/archive?daysOld=90"
```

### 请求示例 (Java)

```java
public class ArchiveExample {
    
    private static final String BASE_URL = "http://localhost:8082/api/v1/alerts";
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * 归档旧告警
     */
    public int archiveOldAlerts(int daysOld) {
        String url = BASE_URL + "/archive?daysOld=" + daysOld;
        
        ResponseEntity<Map<String, Integer>> response = restTemplate.postForEntity(
            url,
            null,
            new ParameterizedTypeReference<Map<String, Integer>>() {}
        );
        
        int archivedCount = response.getBody().get("archivedCount");
        System.out.println("归档了 " + archivedCount + " 条告警");
        
        return archivedCount;
    }
    
    /**
     * 定期归档任务
     */
    public void scheduledArchive() {
        // 每月归档一次,保留60天数据
        int archived = archiveOldAlerts(60);
        
        if (archived > 0) {
            System.out.println("✅ 定期归档完成: " + archived + " 条记录");
        }
    }
}
```

### 响应示例

**HTTP 200 OK**

```json
{
  "archivedCount": 125
}
```

### 错误码

| HTTP状态码 | 说明 |
|-----------|------|
| 200 | 归档成功 |
| 400 | 参数无效 (daysOld < 1) |
| 500 | 归档失败 |

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
