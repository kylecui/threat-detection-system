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
        
        for (AttackEvent event : elements) {
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
        
        // 计算威胁评分
        double threatScore = calculateThreatScore(
            attackCount, 
            uniqueIps.size(), 
            uniquePorts.size(), 
            uniqueDevices.size(),
            mixedPortWeight,
            context.window().getEnd()
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
                .tier(tier)
                .windowType(windowType)
                .windowStart(Instant.ofEpochMilli(context.window().getStart()))
                .windowEnd(Instant.ofEpochMilli(context.window().getEnd()))
                .timestamp(Instant.now())
                .build();
        
        log.info("Tier {} window: customerId={}, attackMac={}, attackIp={}, mostAccessedHoneypot={}, " +
                "threatScore={}, threatLevel={}, count={}, ips={}, ports={}, devices={}",
                tier, customerId, attackMac, attackIp, mostAccessedHoneypotIp,
                threatScore, threatLevel, attackCount, 
                uniqueIps.size(), uniquePorts.size(), uniqueDevices.size());
        
        out.collect(aggregated);
    }
    
    /**
     * 计算威胁评分
     * 
     * <p>公式: threatScore = (attackCount × uniqueIps × uniquePorts × portWeight) 
     *                     × timeWeight × ipWeight × deviceWeight
     * 
     * @param attackCount 攻击次数
     * @param uniqueIps 唯一诱饵IP数
     * @param uniquePorts 唯一端口数
     * @param uniqueDevices 唯一设备数
     * @param portWeight 端口权重
     * @param windowEndMillis 窗口结束时间戳(毫秒)
     * @return 威胁评分
     */
    private double calculateThreatScore(int attackCount, int uniqueIps, int uniquePorts,
                                       int uniqueDevices, double portWeight, long windowEndMillis) {
        // 1. 基础分数
        double baseScore = attackCount * uniqueIps * uniquePorts * portWeight;
        
        // 2. 时间权重
        double timeWeight = calculateTimeWeight(windowEndMillis);
        
        // 3. IP权重（横向移动范围）
        double ipWeight = calculateIpWeight(uniqueIps);
        
        // 4. 设备权重（多设备攻击）
        double deviceWeight = uniqueDevices > 1 ? 1.5 : 1.0;
        
        return baseScore * timeWeight * ipWeight * deviceWeight;
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
    
}
