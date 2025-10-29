package com.threatdetection.stream.functions;

import com.threatdetection.stream.model.AggregatedAttackData;
import com.threatdetection.stream.model.AttackEvent;
import com.threatdetection.stream.service.PortWeightService;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * 时间窗口聚合处理器
 * 
 * <p>按窗口聚合攻击数据：
 * - Tier 1: 30秒窗口 - 勒索软件检测
 * - Tier 2: 5分钟窗口 - 主要威胁检测
 * - Tier 3: 15分钟窗口 - APT检测
 */
public class TierWindowProcessor 
        extends ProcessWindowFunction<AttackEvent, AggregatedAttackData, String, TimeWindow> {
    
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(TierWindowProcessor.class);
    
    private final int tier;
    private final String windowType;
    private transient PortWeightService portWeightService;
    
    public TierWindowProcessor(int tier, String windowType) {
        this.tier = tier;
        this.windowType = windowType;
    }
    
    @Override
    public void open(org.apache.flink.configuration.Configuration parameters) throws Exception {
        super.open(parameters);
        // 初始化服务（在分布式环境中每个TaskManager实例化一次）
        this.portWeightService = new PortWeightService();
    }
    
    @Override
    public void process(
            String key,
            Context context,
            Iterable<AttackEvent> elements,
            Collector<AggregatedAttackData> out) throws Exception {
        
        // V4.0 Phase 3: 将Iterable转换为List以便计算时间分布
        List<AttackEvent> eventList = StreamSupport.stream(elements.spliterator(), false)
                .collect(Collectors.toList());
        
        if (eventList.isEmpty()) {
            return;
        }
        
        // 提取 customerId 和 attackMac
        String[] parts = key.split(":", 2);
        if (parts.length != 2) {
            log.warn("Invalid key format: {}", key);
            return;
        }
        
        String customerId = parts[0];
        String attackMac = parts[1];
        
        // 统计指标
        int attackCount = 0;
        Set<String> uniqueIps = new HashSet<>();
        Set<Integer> uniquePorts = new HashSet<>();
        Set<String> uniqueDevices = new HashSet<>();
        
        // V4.0 Phase 2: 收集攻击IP和蜜罐访问统计
        String attackIp = null;
        java.util.Map<String, Integer> honeypotAccessCount = new java.util.HashMap<>();
        
        for (AttackEvent event : eventList) {
            attackCount++;
            
            // 收集攻击源IP (所有事件的attackIp应该相同，取第一个)
            if (attackIp == null && event.getAttackIp() != null) {
                attackIp = event.getAttackIp();
            }
            
            // 统计每个蜜罐IP的访问次数
            String honeypotIp = event.getResponseIp();
            honeypotAccessCount.put(honeypotIp, honeypotAccessCount.getOrDefault(honeypotIp, 0) + 1);
            
            uniqueIps.add(event.getResponseIp());
            uniquePorts.add(event.getResponsePort());
            uniqueDevices.add(event.getDevSerial());
        }
        
        // 找出访问最多的蜜罐IP
        String mostAccessedHoneypotIp = honeypotAccessCount.entrySet().stream()
            .max(java.util.Map.Entry.comparingByValue())
            .map(java.util.Map.Entry::getKey)
            .orElse(null);
        
        // 计算混合端口权重 (修正：只传入ports Set)
        double mixedPortWeight = portWeightService.calculateMixedPortWeight(uniquePorts);
        
        // V4.0 Phase 3: 计算时间分布权重
        long windowStart = context.window().getStart();
        long windowEnd = context.window().getEnd();
        long windowSize = windowEnd - windowStart;
        long eventTimeSpan = calculateEventTimeSpan(eventList);
        double burstIntensity = calculateBurstIntensity(eventList, windowSize);
        double timeDistWeight = calculateTimeDistributionWeight(burstIntensity);
        
        // 计算威胁评分 (包含时间分布权重)
        double threatScore = calculateThreatScore(
            attackCount, 
            uniqueIps.size(), 
            uniquePorts.size(), 
            uniqueDevices.size(),
            mixedPortWeight,
            timeDistWeight,  // 新增参数
            windowEnd
        );
        
        // 确定威胁等级
        String threatLevel = determineThreatLevel(threatScore);
        
        // 构建聚合结果
        AggregatedAttackData aggregated = AggregatedAttackData.builder()
                .customerId(customerId)
                .attackMac(attackMac)
                .attackIp(attackIp)  // V4.0 Phase 2
                .mostAccessedHoneypotIp(mostAccessedHoneypotIp)  // V4.0 Phase 2
                .attackCount(attackCount)
                .uniqueIps(uniqueIps.size())
                .uniquePorts(uniquePorts.size())
                .uniqueDevices(uniqueDevices.size())
                .mixedPortWeight(mixedPortWeight)
                .threatScore(threatScore)
                .threatLevel(threatLevel)
                .eventTimeSpan(eventTimeSpan)              // V4.0 Phase 3
                .burstIntensity(burstIntensity)            // V4.0 Phase 3
                .timeDistributionWeight(timeDistWeight)    // V4.0 Phase 3
                .tier(tier)
                .windowType(windowType)
                .windowStart(Instant.ofEpochMilli(context.window().getStart()))
                .windowEnd(Instant.ofEpochMilli(context.window().getEnd()))
                .timestamp(Instant.now())
                .build();
        
        log.info("Tier {} window: customerId={}, attackMac={}, attackIp={}, mostAccessedHoneypot={}, " +
                "threatScore={}, threatLevel={}, timeDistWeight={}, burstIntensity={}, " +
                "count={}, ips={}, ports={}, devices={}, timeSpan={}ms of {}ms window",
                tier, customerId, attackMac, attackIp, mostAccessedHoneypotIp,
                threatScore, threatLevel, String.format("%.2f", timeDistWeight), String.format("%.3f", burstIntensity),
                attackCount, uniqueIps.size(), uniquePorts.size(), uniqueDevices.size(),
                eventTimeSpan, windowSize);
        
        out.collect(aggregated);
    }
    
    /**
     * 计算威胁评分
     * 
     * <p>公式: threatScore = (attackCount × uniqueIps × uniquePorts × portWeight) 
     *                     × timeWeight × ipWeight × deviceWeight × timeDistWeight
     * 
     * @param attackCount 攻击次数
     * @param uniqueIps 唯一诱饵IP数
     * @param uniquePorts 唯一端口数
     * @param uniqueDevices 唯一设备数
     * @param portWeight 端口权重
     * @param timeDistWeight 时间分布权重
     * @param windowEndMillis 窗口结束时间戳(毫秒)
     * @return 威胁评分
     */
    private double calculateThreatScore(int attackCount, int uniqueIps, int uniquePorts,
                                       int uniqueDevices, double portWeight, 
                                       double timeDistWeight, long windowEndMillis) {
        // 1. 基础分数
        double baseScore = attackCount * uniqueIps * uniquePorts * portWeight;
        
        // 2. 时间权重
        double timeWeight = calculateTimeWeight(windowEndMillis);
        
        // 3. IP权重（横向移动范围）
        double ipWeight = calculateIpWeight(uniqueIps);
        
        // 4. 设备权重（多设备攻击）
        double deviceWeight = uniqueDevices > 1 ? 1.5 : 1.0;
        
        // 5. V4.0 Phase 3: 时间分布权重
        return baseScore * timeWeight * ipWeight * deviceWeight * timeDistWeight;
    }
    
    /**
     * 计算时间权重
     */
    private double calculateTimeWeight(long timestampMillis) {
        Instant instant = Instant.ofEpochMilli(timestampMillis);
        int hour = instant.atZone(java.time.ZoneId.systemDefault()).getHour();
        
        if (hour >= 0 && hour < 6) return 1.2;   // 深夜异常
        if (hour >= 6 && hour < 9) return 1.1;   // 早晨时段
        if (hour >= 9 && hour < 17) return 1.0;  // 工作时间
        if (hour >= 17 && hour < 21) return 0.9; // 傍晚时段
        return 0.8;                              // 夜间时段
    }
    
    /**
     * 计算IP权重（横向移动范围）
     */
    private double calculateIpWeight(int uniqueIps) {
        if (uniqueIps >= 10) return 2.0;  // 大规模扫描
        if (uniqueIps >= 6) return 1.7;   // 广泛扫描
        if (uniqueIps >= 4) return 1.5;   // 中等扫描
        if (uniqueIps >= 2) return 1.3;   // 小范围扫描
        return 1.0;                        // 单一目标
    }
    
    /**
     * 确定威胁等级
     */
    private String determineThreatLevel(double score) {
        if (score >= 200) return "CRITICAL";
        if (score >= 100) return "HIGH";
        if (score >= 50) return "MEDIUM";
        if (score >= 10) return "LOW";
        return "INFO";
    }
    
    /**
     * V4.0 Phase 3: 计算事件时间跨度
     * 
     * <p>过滤异常时间戳（如1970年纪元时间），只计算有效事件的时间跨度
     * 
     * @param events 事件列表
     * @return 时间跨度（毫秒）
     */
    private long calculateEventTimeSpan(List<AttackEvent> events) {
        if (events.size() < 2) {
            return 0;
        }
        
        // 过滤异常时间戳：排除1970年之前或2000年之前的数据（明显错误）
        // 正常的威胁事件应该是近期的，至少应该在2020年之后
        final long MIN_VALID_TIMESTAMP = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli();
        
        java.util.List<Long> validTimestamps = events.stream()
                .map(AttackEvent::getTimestamp)
                .map(Instant::toEpochMilli)
                .filter(t -> t >= MIN_VALID_TIMESTAMP)  // 过滤异常时间戳
                .collect(java.util.stream.Collectors.toList());
        
        // 如果过滤后少于2个有效事件，返回0
        if (validTimestamps.size() < 2) {
            log.warn("Insufficient valid timestamps for timespan calculation: total={}, valid={}",
                    events.size(), validTimestamps.size());
            return 0;
        }
        
        long minTime = validTimestamps.stream()
                .min(Long::compare)
                .get();
        
        long maxTime = validTimestamps.stream()
                .max(Long::compare)
                .get();
        
        long timeSpan = maxTime - minTime;
        
        log.debug("Calculated event timespan: {}ms from {} valid events (total: {})",
                timeSpan, validTimestamps.size(), events.size());
        
        return timeSpan;
    }
    
    /**
     * V4.0 Phase 3: 计算爆发强度系数 (Burst Intensity Coefficient)
     * 
     * <p>公式: BIC = 1 - (eventTimeSpan / windowSize)
     * <p>取值范围: [0, 1]
     * - BIC = 1: 所有事件集中在一个时间点（最高爆发）
     * - BIC = 0: 事件均匀分布在整个窗口（无爆发）
     * 
     * @param events 事件列表
     * @param windowSize 窗口大小（毫秒）
     * @return 爆发强度系数
     */
    private double calculateBurstIntensity(List<AttackEvent> events, long windowSize) {
        if (events.size() < 2 || windowSize == 0) {
            return 0.0;
        }
        
        long timeSpan = calculateEventTimeSpan(events);
        double intensity = 1.0 - (double) timeSpan / windowSize;
        
        // 确保结果在 [0, 1] 范围内
        return Math.max(0.0, Math.min(1.0, intensity));
    }
    
    /**
     * V4.0 Phase 3: 计算时间分布权重
     * 
     * <p>公式: timeDistWeight = 1.0 + (burstIntensity × 2.0)
     * <p>取值范围: [1.0, 3.0]
     * - 1.0: 完全分散（无威胁加成）
     * - 3.0: 完全集中（勒索软件爆发）
     * 
     * @param burstIntensity 爆发强度系数
     * @return 时间分布权重
     */
    private double calculateTimeDistributionWeight(double burstIntensity) {
        return 1.0 + (burstIntensity * 2.0);
    }
    
}
