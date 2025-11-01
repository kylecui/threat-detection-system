package com.threatdetection.ingestion.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Phase 1A: 监控指标服务
 * 使用Micrometer提供详细的性能和业务指标
 */
@Service
public class MetricsService {

    // 计数器指标
    private final Counter logsReceivedCounter;
    private final Counter logsProcessedCounter;
    private final Counter logsFailedCounter;
    private final Counter attackEventsCounter;
    private final Counter statusEventsCounter;
    private final Counter batchRequestsCounter;

    // 定时器指标 - 公开以便外部访问
    public final Timer singleLogProcessingTimer;
    public final Timer batchProcessingTimer;

    public MetricsService(MeterRegistry meterRegistry) {
        // 初始化计数器
        this.logsReceivedCounter = Counter.builder("logs.received.total")
                .description("Total number of logs received")
                .register(meterRegistry);

        this.logsProcessedCounter = Counter.builder("logs.processed.total")
                .description("Total number of logs successfully processed")
                .register(meterRegistry);

        this.logsFailedCounter = Counter.builder("logs.failed.total")
                .description("Total number of logs that failed processing")
                .register(meterRegistry);

        this.attackEventsCounter = Counter.builder("events.attack.total")
                .description("Total number of attack events processed")
                .register(meterRegistry);

        this.statusEventsCounter = Counter.builder("events.status.total")
                .description("Total number of status events processed")
                .register(meterRegistry);

        this.batchRequestsCounter = Counter.builder("batch.requests.total")
                .description("Total number of batch requests received")
                .register(meterRegistry);

        // 初始化定时器
        this.singleLogProcessingTimer = Timer.builder("logs.processing.duration")
                .description("Time taken to process a single log")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.batchProcessingTimer = Timer.builder("batch.processing.duration")
                .description("Time taken to process a batch of logs")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    /**
     * 记录接收到的日志
     */
    public void recordLogReceived() {
        logsReceivedCounter.increment();
    }

    /**
     * 记录成功处理的日志
     */
    public void recordLogProcessed() {
        logsProcessedCounter.increment();
    }

    /**
     * 记录处理失败的日志
     */
    public void recordLogFailed() {
        logsFailedCounter.increment();
    }

    /**
     * 记录攻击事件
     */
    public void recordAttackEvent() {
        attackEventsCounter.increment();
    }

    /**
     * 记录状态事件
     */
    public void recordStatusEvent() {
        statusEventsCounter.increment();
    }

    /**
     * 记录批量请求
     */
    public void recordBatchRequest() {
        batchRequestsCounter.increment();
    }

    /**
     * 记录单条日志处理时间
     */
    public void recordSingleLogProcessingTime(long durationMs) {
        singleLogProcessingTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录批量处理时间
     */
    public void recordBatchProcessingTime(long durationMs) {
        batchProcessingTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 使用Timer包装单条日志处理
     */
    public Timer.Sample startSingleLogProcessingTimer() {
        return Timer.start();
    }

    /**
     * 使用Timer包装批量处理
     */
    public Timer.Sample startBatchProcessingTimer() {
        return Timer.start();
    }
}