package com.threatdetection.ingestion.service;

import com.threatdetection.ingestion.model.AttackEvent;
import com.threatdetection.ingestion.model.BatchLogResponse;
import com.threatdetection.ingestion.model.IngestionResult;
import com.threatdetection.ingestion.model.StatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Phase 1A: 异步批量日志处理服务
 * 实现高性能的批量日志处理，支持异步处理和错误隔离
 */
@Service
public class AsyncBatchLogIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncBatchLogIngestionService.class);

    private final LogParserService logParserService;
    private final KafkaProducerService kafkaProducerService;
    private final MetricsService metricsService;

    public AsyncBatchLogIngestionService(LogParserService logParserService,
                                       KafkaProducerService kafkaProducerService,
                                       MetricsService metricsService) {
        this.logParserService = logParserService;
        this.kafkaProducerService = kafkaProducerService;
        this.metricsService = metricsService;
    }

    /**
     * Phase 1A: 异步处理单个日志
     * 返回CompletableFuture以支持异步处理
     */
    @Async
    public CompletableFuture<IngestionResult> processLogAsync(String rawLog, int index) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.nanoTime();

            try {
                logger.debug("Processing log {} asynchronously", index);

                Optional<Object> parsedEvent = logParserService.parseLog(rawLog);
                if (parsedEvent.isPresent()) {
                    Object event = parsedEvent.get();
                    String eventId = generateEventId(event, index);

                    if (event instanceof AttackEvent) {
                        kafkaProducerService.sendAttackEvent((AttackEvent) event);
                        IngestionResult result = IngestionResult.success(eventId);
                        result.setEventType("ATTACK");
                        // Phase 1A: 记录成功指标
                        metricsService.recordLogProcessed();
                        metricsService.recordAttackEvent();
                        return result;
                    } else if (event instanceof StatusEvent) {
                        kafkaProducerService.sendStatusEvent((StatusEvent) event);
                        IngestionResult result = IngestionResult.success(eventId);
                        result.setEventType("STATUS");
                        // Phase 1A: 记录成功指标
                        metricsService.recordLogProcessed();
                        metricsService.recordStatusEvent();
                        return result;
                    } else {
                        return IngestionResult.error("Unknown event type");
                    }
                } else {
                    // Phase 1A: 记录失败指标
                    metricsService.recordLogFailed();
                    return IngestionResult.error("Parse failed");
                }

            } catch (Exception e) {
                logger.error("Async processing failed for log {}: {}", index, e.getMessage());
                // Phase 1A: 记录失败指标
                metricsService.recordLogFailed();
                return IngestionResult.error("Processing error: " + e.getMessage());
            } finally {
                long endTime = System.nanoTime();
                long durationMs = (endTime - startTime) / 1_000_000;
                // Phase 1A: 记录处理时间
                metricsService.recordSingleLogProcessingTime(durationMs);
                logger.debug("Log {} processed in {} ms", index, durationMs);
            }
        });
    }

    /**
     * Phase 1A: 批量处理日志
     * 使用CompletableFuture.allOf实现并行处理
     */
    public BatchLogResponse processBatch(List<String> logs) {
        long startTime = System.currentTimeMillis();

        if (logs == null || logs.isEmpty()) {
            return createEmptyResponse(0);
        }

        int totalCount = logs.size();
        List<CompletableFuture<IngestionResult>> futures = new ArrayList<>();

        // 提交所有异步任务
        for (int i = 0; i < logs.size(); i++) {
            futures.add(processLogAsync(logs.get(i), i));
        }

        try {
            // 等待所有任务完成
            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );

            // 阻塞等待完成 (在实际生产环境中可能需要超时控制)
            allOf.get();

            // 收集结果
            List<IngestionResult> results = new ArrayList<>();
            int successCount = 0;

            for (CompletableFuture<IngestionResult> future : futures) {
                IngestionResult result = future.get(); // 此时任务已完成
                results.add(result);
                if (result.isSuccess()) {
                    successCount++;
                }
            }

            long processingTime = System.currentTimeMillis() - startTime;

            BatchLogResponse response = new BatchLogResponse(
                totalCount,
                successCount,
                totalCount - successCount,
                results,
                processingTime
            );

            logger.info("Batch processing completed: {} total, {} success, {} errors, {} ms",
                       totalCount, successCount, totalCount - successCount, processingTime);

            return response;

        } catch (Exception e) {
            logger.error("Batch processing failed: {}", e.getMessage(), e);
            long processingTime = System.currentTimeMillis() - startTime;

            return new BatchLogResponse(
                totalCount,
                0,
                totalCount,
                createErrorResults(totalCount, "Batch processing failed: " + e.getMessage()),
                processingTime
            );
        }
    }

    /**
     * 生成事件ID
     */
    private String generateEventId(Object event, int index) {
        if (event instanceof AttackEvent) {
            AttackEvent attackEvent = (AttackEvent) event;
            return String.format("attack-%s-%d-%d",
                attackEvent.getDevSerial(),
                attackEvent.getLogTime(),
                index);
        } else if (event instanceof StatusEvent) {
            StatusEvent statusEvent = (StatusEvent) event;
            return String.format("status-%s-%d-%d",
                statusEvent.getDevSerial(),
                statusEvent.getDevStartTime(),
                index);
        }
        return String.format("unknown-%d", index);
    }

    /**
     * 创建空的响应
     */
    private BatchLogResponse createEmptyResponse(long processingTime) {
        return new BatchLogResponse(0, 0, 0, new ArrayList<>(), processingTime);
    }

    /**
     * 创建错误结果列表
     */
    private List<IngestionResult> createErrorResults(int count, String errorMessage) {
        List<IngestionResult> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(IngestionResult.error(errorMessage));
        }
        return results;
    }
}