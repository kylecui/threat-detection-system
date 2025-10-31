package com.threatdetection.ingestion.service;

import com.threatdetection.ingestion.model.AttackEvent;
import com.threatdetection.ingestion.model.ImportMode;
import com.threatdetection.ingestion.model.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 场景感知导入服务
 *
 * <p>根据不同的导入场景（迁移、补全、离线分析）采用不同的处理策略：
 * - MIGRATION: 系统迁移，合并APT积累数据
 * - COMPLETION: 数据补全，客户特定历史数据合并
 * - OFFLINE: 离线分析，独立处理并全局威胁关联
 *
 * <p>核心特性：
 * - 事件去重：使用Redis防止重复处理
 * - 场景路由：根据模式选择合适的处理逻辑
 * - 性能监控：记录处理时间和成功率
 */
@Service
public class ScenarioAwareImportService {

    private static final Logger logger = LoggerFactory.getLogger(ScenarioAwareImportService.class);

    private static final String EVENT_DEDUPE_KEY_PREFIX = "event:dedupe:";
    private static final Duration EVENT_DEDUPE_TTL = Duration.ofHours(24); // 24小时去重窗口

    private final RedisTemplate<String, String> redisTemplate;
    private final AsyncBatchLogIngestionService batchIngestionService;
    private final AptTemporalAccumulationService aptAccumulationService;
    private final ThreatAssessmentMergeService threatAssessmentMergeService;
    private final MetricsService metricsService;

    public ScenarioAwareImportService(
            RedisTemplate<String, String> redisTemplate,
            AsyncBatchLogIngestionService batchIngestionService,
            AptTemporalAccumulationService aptAccumulationService,
            ThreatAssessmentMergeService threatAssessmentMergeService,
            MetricsService metricsService) {
        this.redisTemplate = redisTemplate;
        this.batchIngestionService = batchIngestionService;
        this.aptAccumulationService = aptAccumulationService;
        this.threatAssessmentMergeService = threatAssessmentMergeService;
        this.metricsService = metricsService;
    }

    /**
     * 根据导入模式处理日志批次
     *
     * @param logs 原始日志列表
     * @param mode 导入模式
     * @param customerId 可选的客户ID（用于completion模式）
     * @return 导入结果
     */
    public ImportResult processBatchWithMode(List<String> logs, ImportMode mode, String customerId) {
        long startTime = System.currentTimeMillis();

        logger.info("Processing batch with mode={}, customerId={}, logCount={}",
                   mode, customerId, logs.size());

        try {
            switch (mode) {
                case MIGRATION:
                    return processMigrationBatch(logs, startTime);
                case COMPLETION:
                    return processCompletionBatch(logs, customerId, startTime);
                case OFFLINE:
                    return processOfflineBatch(logs, startTime);
                default:
                    throw new IllegalArgumentException("Unsupported import mode: " + mode);
            }
        } catch (Exception e) {
            logger.error("Batch processing failed for mode {}: {}", mode, e.getMessage(), e);
            long processingTime = System.currentTimeMillis() - startTime;
            return ImportResult.error(logs.size(), "Processing failed: " + e.getMessage(), processingTime);
        }
    }

    /**
     * 系统迁移模式处理
     *
     * <p>特点：
     * - 需要合并APT积累数据
     * - 保持历史威胁评估的连续性
     * - 可能包含跨时间段的数据
     */
    private ImportResult processMigrationBatch(List<String> logs, long startTime) {
        logger.info("Processing migration batch with {} logs", logs.size());

        // 1. 过滤重复事件
        List<String> dedupedLogs = filterDuplicateEvents(logs);

        // 2. 正常批量处理
        var batchResponse = batchIngestionService.processBatch(dedupedLogs);

        // 3. 合并APT积累数据
        mergeAptAccumulations(dedupedLogs);

        // 4. 合并威胁评估
        mergeThreatAssessments(dedupedLogs);

        long processingTime = System.currentTimeMillis() - startTime;
        int duplicateCount = logs.size() - dedupedLogs.size();

        logger.info("Migration batch completed: total={}, deduped={}, duplicates={}, time={}ms",
                   logs.size(), dedupedLogs.size(), duplicateCount, processingTime);

        return ImportResult.success(
            logs.size(),
            dedupedLogs.size(),
            duplicateCount,
            batchResponse.getErrorCount(),
            processingTime
        );
    }

    /**
     * 数据补全模式处理
     *
     * <p>特点：
     * - 针对特定客户的历史数据补全
     * - 需要与现有数据合并
     * - 保持客户数据的完整性
     */
    private ImportResult processCompletionBatch(List<String> logs, String customerId, long startTime) {
        logger.info("Processing completion batch for customer {} with {} logs", customerId, logs.size());

        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required for completion mode");
        }

        // 1. 过滤重复事件（按客户）
        List<String> dedupedLogs = filterDuplicateEventsForCustomer(logs, customerId);

        // 2. 客户特定批量处理
        var batchResponse = batchIngestionService.processBatch(dedupedLogs);

        // 3. 合并客户特定的威胁评估
        mergeCustomerThreatAssessments(dedupedLogs, customerId);

        long processingTime = System.currentTimeMillis() - startTime;
        int duplicateCount = logs.size() - dedupedLogs.size();

        logger.info("Completion batch completed for customer {}: total={}, deduped={}, duplicates={}, time={}ms",
                   customerId, logs.size(), dedupedLogs.size(), duplicateCount, processingTime);

        return ImportResult.success(
            logs.size(),
            dedupedLogs.size(),
            duplicateCount,
            batchResponse.getErrorCount(),
            processingTime
        );
    }

    /**
     * 离线分析模式处理
     *
     * <p>特点：
     * - 独立处理，不影响生产数据
     * - 生成全局威胁关联分析
     * - 用于安全研究和威胁情报
     */
    private ImportResult processOfflineBatch(List<String> logs, long startTime) {
        logger.info("Processing offline batch with {} logs", logs.size());

        // 1. 过滤重复事件（全局）
        List<String> dedupedLogs = filterDuplicateEvents(logs);

        // 2. 离线批量处理（可能使用不同的topic或sink）
        var batchResponse = batchIngestionService.processBatch(dedupedLogs);

        // 3. 生成全局威胁关联分析
        generateGlobalThreatAnalysis(dedupedLogs);

        long processingTime = System.currentTimeMillis() - startTime;
        int duplicateCount = logs.size() - dedupedLogs.size();

        logger.info("Offline batch completed: total={}, deduped={}, duplicates={}, time={}ms",
                   logs.size(), dedupedLogs.size(), duplicateCount, processingTime);

        return ImportResult.success(
            logs.size(),
            dedupedLogs.size(),
            duplicateCount,
            batchResponse.getErrorCount(),
            processingTime
        );
    }

    /**
     * 过滤重复事件（全局）
     */
    private List<String> filterDuplicateEvents(List<String> logs) {
        return logs.stream()
            .filter(log -> {
                String eventKey = generateEventKey(log);
                Boolean isNew = redisTemplate.opsForValue().setIfAbsent(
                    EVENT_DEDUPE_KEY_PREFIX + eventKey,
                    "1",
                    EVENT_DEDUPE_TTL
                );
                return Boolean.TRUE.equals(isNew);
            })
            .toList();
    }

    /**
     * 过滤重复事件（按客户）
     */
    private List<String> filterDuplicateEventsForCustomer(List<String> logs, String customerId) {
        return logs.stream()
            .filter(log -> {
                String eventKey = generateEventKeyForCustomer(log, customerId);
                Boolean isNew = redisTemplate.opsForValue().setIfAbsent(
                    EVENT_DEDUPE_KEY_PREFIX + eventKey,
                    "1",
                    EVENT_DEDUPE_TTL
                );
                return Boolean.TRUE.equals(isNew);
            })
            .toList();
    }

    /**
     * 生成全局事件去重键
     */
    private String generateEventKey(String log) {
        // 基于日志内容生成唯一键
        // 这里需要解析日志提取关键字段
        // 暂时使用简单的hash，实际实现需要解析日志
        return String.valueOf(log.hashCode());
    }

    /**
     * 生成客户特定事件去重键
     */
    private String generateEventKeyForCustomer(String log, String customerId) {
        return customerId + ":" + generateEventKey(log);
    }

    /**
     * 合并APT积累数据
     */
    private void mergeAptAccumulations(List<String> logs) {
        logger.info("Merging APT accumulations for {} logs", logs.size());
        aptAccumulationService.mergeAptAccumulations(logs);
    }

    /**
     * 合并威胁评估
     */
    private void mergeThreatAssessments(List<String> logs) {
        logger.info("Merging threat assessments for {} logs", logs.size());
        threatAssessmentMergeService.mergeThreatAssessments(logs);
    }

    /**
     * 合并客户特定威胁评估
     */
    private void mergeCustomerThreatAssessments(List<String> logs, String customerId) {
        logger.info("Merging threat assessments for customer {} with {} logs", customerId, logs.size());
        threatAssessmentMergeService.mergeCustomerThreatAssessments(logs, customerId);
    }

    /**
     * 生成全局威胁关联分析
     */
    private void generateGlobalThreatAnalysis(List<String> logs) {
        logger.info("Generating global threat analysis for {} logs", logs.size());
        // TODO: 实现全局威胁关联分析逻辑
        // 生成离线分析报告，存储到专门的分析数据库或文件
    }
}