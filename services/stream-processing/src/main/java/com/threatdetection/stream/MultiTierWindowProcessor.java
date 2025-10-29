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
 * - Tier 1窗口(默认30秒): 勒索软件快速检测
 * - Tier 2窗口(默认5分钟): 主要威胁检测窗口 
 * - Tier 3窗口(默认15分钟): APT慢速扫描检测
 * 
 * 每层窗口独立聚合和评分，支持不同类型的威胁模式
 * 
 * 环境变量配置:
 * 时间窗口:
 * - TIER1_WINDOW_SECONDS: Tier 1窗口时长(秒), 默认30, 推荐范围10-300秒
 * - TIER2_WINDOW_SECONDS: Tier 2窗口时长(秒), 默认300 (5分钟), 推荐范围60-1800秒
 * - TIER3_WINDOW_SECONDS: Tier 3窗口时长(秒), 默认900 (15分钟), 推荐范围300-7200秒
 * 
 * 窗口名称:
 * - TIER1_WINDOW_NAME: Tier 1窗口名称, 默认"勒索软件快速检测"
 * - TIER2_WINDOW_NAME: Tier 2窗口名称, 默认"主要威胁检测"
 * - TIER3_WINDOW_NAME: Tier 3窗口名称, 默认"APT慢速扫描检测"
 */
public class MultiTierWindowProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(MultiTierWindowProcessor.class);
    
    // 时间窗口配置（可通过环境变量自定义）
    private static final int TIER1_WINDOW_SECONDS;
    private static final int TIER2_WINDOW_SECONDS;
    private static final int TIER3_WINDOW_SECONDS;
    
    // 窗口名称配置（可通过环境变量自定义）
    private static final String TIER1_WINDOW_NAME;
    private static final String TIER2_WINDOW_NAME;
    private static final String TIER3_WINDOW_NAME;
    
    // 窗口时长限制（防止配置错误导致性能问题）
    private static final int MIN_WINDOW_SECONDS = 10;         // 最小窗口: 10秒
    private static final int TIER1_MAX_RECOMMENDED = 300;     // Tier 1推荐最大值: 5分钟
    private static final int TIER2_MAX_RECOMMENDED = 1800;    // Tier 2推荐最大值: 30分钟
    private static final int TIER3_MAX_RECOMMENDED = 7200;    // Tier 3推荐最大值: 2小时
    
    static {
        // 读取时间窗口配置
        TIER1_WINDOW_SECONDS = getEnvInt("TIER1_WINDOW_SECONDS", 30);
        TIER2_WINDOW_SECONDS = getEnvInt("TIER2_WINDOW_SECONDS", 300);
        TIER3_WINDOW_SECONDS = getEnvInt("TIER3_WINDOW_SECONDS", 900);
        
        // 读取窗口名称配置
        TIER1_WINDOW_NAME = getEnvString("TIER1_WINDOW_NAME", "勒索软件快速检测");
        TIER2_WINDOW_NAME = getEnvString("TIER2_WINDOW_NAME", "主要威胁检测");
        TIER3_WINDOW_NAME = getEnvString("TIER3_WINDOW_NAME", "APT慢速扫描检测");
        
        // 配置验证
        validateWindowConfiguration();
        
        // 启动日志
        logger.info("==================== 时间窗口配置 ====================");
        logger.info("Tier 1 窗口: {} 秒 ({})", TIER1_WINDOW_SECONDS, TIER1_WINDOW_NAME);
        logger.info("Tier 2 窗口: {} 秒 ({} 分钟) ({})", 
                    TIER2_WINDOW_SECONDS, TIER2_WINDOW_SECONDS / 60, TIER2_WINDOW_NAME);
        logger.info("Tier 3 窗口: {} 秒 ({} 分钟) ({})", 
                    TIER3_WINDOW_SECONDS, TIER3_WINDOW_SECONDS / 60, TIER3_WINDOW_NAME);
        logger.info("=====================================================");
    }
    
    /**
     * 读取环境变量整数值
     */
    private static int getEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("无效的环境变量 {}: {}, 使用默认值: {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 读取环境变量字符串值
     */
    private static String getEnvString(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }
    
    /**
     * 验证时间窗口配置的合理性
     */
    private static void validateWindowConfiguration() {
        // 验证最小值（强制限制）
        if (TIER1_WINDOW_SECONDS < MIN_WINDOW_SECONDS) {
            throw new IllegalArgumentException(
                String.format("Tier 1 窗口(%d秒)不能小于最小窗口限制(%d秒)", 
                              TIER1_WINDOW_SECONDS, MIN_WINDOW_SECONDS));
        }
        if (TIER2_WINDOW_SECONDS < MIN_WINDOW_SECONDS) {
            throw new IllegalArgumentException(
                String.format("Tier 2 窗口(%d秒)不能小于最小窗口限制(%d秒)", 
                              TIER2_WINDOW_SECONDS, MIN_WINDOW_SECONDS));
        }
        if (TIER3_WINDOW_SECONDS < MIN_WINDOW_SECONDS) {
            throw new IllegalArgumentException(
                String.format("Tier 3 窗口(%d秒)不能小于最小窗口限制(%d秒)", 
                              TIER3_WINDOW_SECONDS, MIN_WINDOW_SECONDS));
        }
        
        // 验证推荐最大值（仅警告）
        if (TIER1_WINDOW_SECONDS > TIER1_MAX_RECOMMENDED) {
            logger.warn("⚠️  Tier 1 窗口({} 秒 = {} 分钟)超过推荐最大值({} 秒 = {} 分钟), 可能影响快速检测效果",
                       TIER1_WINDOW_SECONDS, TIER1_WINDOW_SECONDS / 60,
                       TIER1_MAX_RECOMMENDED, TIER1_MAX_RECOMMENDED / 60);
        }
        if (TIER2_WINDOW_SECONDS > TIER2_MAX_RECOMMENDED) {
            logger.warn("⚠️  Tier 2 窗口({} 秒 = {} 分钟)超过推荐最大值({} 秒 = {} 分钟), 可能延迟告警",
                       TIER2_WINDOW_SECONDS, TIER2_WINDOW_SECONDS / 60,
                       TIER2_MAX_RECOMMENDED, TIER2_MAX_RECOMMENDED / 60);
        }
        if (TIER3_WINDOW_SECONDS > TIER3_MAX_RECOMMENDED) {
            logger.warn("⚠️  Tier 3 窗口({} 秒 = {} 分钟)超过推荐最大值({} 秒 = {} 分钟), 检测延迟较长",
                       TIER3_WINDOW_SECONDS, TIER3_WINDOW_SECONDS / 60,
                       TIER3_MAX_RECOMMENDED, TIER3_MAX_RECOMMENDED / 60);
        }
        
        // 验证层级递增关系（仅警告）
        if (TIER2_WINDOW_SECONDS < TIER1_WINDOW_SECONDS) {
            logger.warn("⚠️  Tier 2 窗口({} 秒)小于 Tier 1 窗口({} 秒), 这可能不是预期配置",
                       TIER2_WINDOW_SECONDS, TIER1_WINDOW_SECONDS);
        }
        if (TIER3_WINDOW_SECONDS < TIER2_WINDOW_SECONDS) {
            logger.warn("⚠️  Tier 3 窗口({} 秒)小于 Tier 2 窗口({} 秒), 这可能不是预期配置",
                       TIER3_WINDOW_SECONDS, TIER2_WINDOW_SECONDS);
        }
    }

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

        // 第1层: Tier 1窗口 (快速威胁检测 - 勒索软件)
        DataStream<AggregatedAttackData> tier1Aggregations = preprocessed
            .keyBy(new AttackEventKeySelector())
            .window(TumblingProcessingTimeWindows.of(Time.seconds(TIER1_WINDOW_SECONDS)))
            .process(new TierWindowProcessor(1, TIER1_WINDOW_NAME))
            .name("tier1-" + TIER1_WINDOW_SECONDS + "s-window");

        // 第2层: Tier 2窗口 (主要威胁检测)
        DataStream<AggregatedAttackData> tier2Aggregations = preprocessed
            .keyBy(new AttackEventKeySelector())
            .window(TumblingProcessingTimeWindows.of(Time.seconds(TIER2_WINDOW_SECONDS)))
            .process(new TierWindowProcessor(2, TIER2_WINDOW_NAME))
            .name("tier2-" + TIER2_WINDOW_SECONDS + "s-window");

        // 第3层: Tier 3窗口 (APT慢速扫描检测)
        DataStream<AggregatedAttackData> tier3Aggregations = preprocessed
            .keyBy(new AttackEventKeySelector())
            .window(TumblingProcessingTimeWindows.of(Time.seconds(TIER3_WINDOW_SECONDS)))
            .process(new TierWindowProcessor(3, TIER3_WINDOW_NAME))
            .name("tier3-" + TIER3_WINDOW_SECONDS + "s-window");

        // 合并所有层级的聚合数据
        DataStream<AggregatedAttackData> allAggregations = tier1Aggregations
            .union(tier2Aggregations, tier3Aggregations);
        
        // 转换为JSON字符串输出
        DataStream<String> allAlerts = allAggregations
            .map(new AggregationToJsonMapper())
            .name("aggregation-to-json");

        logger.info("✅ 3层时间窗口处理器已启动: Tier1={}s, Tier2={}s({}min), Tier3={}s({}min)",
                   TIER1_WINDOW_SECONDS, 
                   TIER2_WINDOW_SECONDS, TIER2_WINDOW_SECONDS / 60,
                   TIER3_WINDOW_SECONDS, TIER3_WINDOW_SECONDS / 60);
        
        return allAlerts;
    }
}