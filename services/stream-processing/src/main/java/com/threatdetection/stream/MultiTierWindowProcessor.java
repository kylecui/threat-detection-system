package com.threatdetection.stream;

import com.threatdetection.stream.model.AttackEvent;
import com.threatdetection.stream.model.AggregatedAttackData;
import com.threatdetection.stream.functions.AttackEventPreprocessor;
import com.threatdetection.stream.functions.AttackEventKeySelector;
import com.threatdetection.stream.functions.TierWindowProcessor;
import com.threatdetection.stream.functions.AggregationToJsonMapper;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MVP Phase 0: 3层时间窗口处理器
 * 
 * 实现MVP要求的3层时间窗口架构:
 * - 30秒窗口: 勒索软件快速检测
 * - 5分钟窗口: 主要威胁检测窗口 
 * - 15分钟窗口: APT慢速扫描检测
 * 
 * 每层窗口独立聚合和评分，支持不同类型的威胁模式
 */
public class MultiTierWindowProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(MultiTierWindowProcessor.class);

    /**
     * 处理3层时间窗口
     * @param attackStream 攻击事件流
     * @param bootstrapServers Kafka地址
     * @return 处理后的威胁告警流
     */
    public static DataStream<String> processMultiTierWindows(
            DataStream<AttackEvent> attackStream, 
            String bootstrapServers) {
        
        // 数据预处理：转换为Tuple2<customerId:attackMac, AttackEvent>
        DataStream<AttackEvent> preprocessed = attackStream
            .map(new AttackEventPreprocessor())
            .name("attack-event-preprocessor");

        // 第1层: 30秒窗口 - 勒索软件快速检测
        DataStream<AggregatedAttackData> tier1Aggregations = preprocessed
            .keyBy(new AttackEventKeySelector())
            .window(TumblingProcessingTimeWindows.of(Time.seconds(30)))
            .process(new TierWindowProcessor(1, "RANSOMWARE_DETECTION"))
            .name("tier1-30s-window");

        // 第2层: 5分钟窗口 - 主要威胁检测
        DataStream<AggregatedAttackData> tier2Aggregations = preprocessed
            .keyBy(new AttackEventKeySelector())
            .window(TumblingProcessingTimeWindows.of(Time.minutes(5)))
            .process(new TierWindowProcessor(2, "MAIN_THREAT_DETECTION"))
            .name("tier2-5min-window");

        // 第3层: 15分钟窗口 - APT慢速扫描
        DataStream<AggregatedAttackData> tier3Aggregations = preprocessed
            .keyBy(new AttackEventKeySelector())
            .window(TumblingProcessingTimeWindows.of(Time.minutes(15)))
            .process(new TierWindowProcessor(3, "APT_SLOW_SCAN"))
            .name("tier3-15min-window");

        // 合并所有层级的聚合数据
        DataStream<AggregatedAttackData> allAggregations = tier1Aggregations
            .union(tier2Aggregations, tier3Aggregations);
        
        // 转换为JSON字符串输出
        DataStream<String> allAlerts = allAggregations
            .map(new AggregationToJsonMapper())
            .name("aggregation-to-json");


        // 威胁评分和分级
        logger.info("Multi-tier window processing configured: 30s/5min/15min");
        
        return allAlerts;
    }
}