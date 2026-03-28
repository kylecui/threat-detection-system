package com.threatdetection.assessment.service;

import com.threatdetection.assessment.dto.AggregatedAttackData;
import com.threatdetection.assessment.model.ThreatAssessment;
import com.threatdetection.assessment.repository.ThreatAssessmentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 威胁评估服务 - 核心业务逻辑
 * 
 * <p>职责:
 * 1. 接收Kafka威胁告警
 * 2. 调用评分计算器
 * 3. 生成缓解建议
 * 4. 持久化评估记录
 * 
 * <p>注意: 暂不使用熔断器(Resilience4j依赖缺失),后续添加
 * 
 * @author Security Team
 * @version 2.0
 */
@Service
@Transactional
public class ThreatAssessmentService {
    
    private static final Logger logger = LoggerFactory.getLogger(ThreatAssessmentService.class);
    
    private final ThreatScoreCalculator calculator;
    private final ThreatAssessmentRepository repository;
    private final RecommendationEngine recommendationEngine;
    private final MlWeightService mlWeightService;

    @Value("${ml.weight.enabled:false}")
    private boolean mlWeightEnabled;
    
    // Prometheus指标
    private final Timer assessmentTimer;
    private final Counter assessmentCounter;
    private final Counter criticalCounter;
    
    @Autowired
    public ThreatAssessmentService(
            ThreatScoreCalculator calculator,
            ThreatAssessmentRepository repository,
            RecommendationEngine recommendationEngine,
            MlWeightService mlWeightService,
            MeterRegistry meterRegistry) {
        this.calculator = calculator;
        this.repository = repository;
        this.recommendationEngine = recommendationEngine;
        this.mlWeightService = mlWeightService;
        
        // 初始化Prometheus指标
        this.assessmentTimer = Timer.builder("threat.assessment.duration")
                .description("Time taken to assess threat")
                .register(meterRegistry);
        this.assessmentCounter = Counter.builder("threat.assessment.total")
                .description("Total threat assessments")
                .register(meterRegistry);
        this.criticalCounter = Counter.builder("threat.assessment.critical")
                .description("Critical threat assessments")
                .register(meterRegistry);
    }
    
    /**
     * 执行威胁评估
     * 
     * <p>TODO: 后续添加Resilience4j熔断器
     * 
     * @param data 聚合攻击数据
     * @return 评估结果
     */
    public ThreatAssessment assessThreat(AggregatedAttackData data) {
        logger.info("Assessing threat: customerId={}, attackMac={}", 
                   data.getCustomerId(), data.getAttackMac());
        
        long startTime = System.currentTimeMillis();
        assessmentCounter.increment();
        
        try {
            return assessmentTimer.recordCallable(() -> performAssessment(data));
        } catch (Exception e) {
            logger.error("Threat assessment failed: customerId={}, attackMac={}, error={}",
                        data.getCustomerId(), data.getAttackMac(), e.getMessage(), e);
            
            // 返回降级评估
            return fallbackAssessment(data, e);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Assessment completed: customerId={}, attackMac={}, duration={}ms",
                       data.getCustomerId(), data.getAttackMac(), duration);
        }
    }
    
    /**
     * 执行评估的核心逻辑
     */
    private ThreatAssessment performAssessment(AggregatedAttackData data) {
        // 1. 计算威胁评分
        double threatScore = calculator.calculateThreatScore(data);
        
        // 2. 判定威胁等级
        String threatLevel = calculator.determineThreatLevel(threatScore);
        
        // 3. 计算权重因子 (用于记录和审计)
        double timeWeight = calculator.calculateEnhancedTimeWeight(data.getCustomerId(), data.getTimestamp());
        double ipWeight = calculator.calculateIpWeight(data.getUniqueIps());
        double portWeight = calculator.calculatePortWeight(data.getUniquePorts());
        double deviceWeight = calculator.calculateDeviceWeight(data.getUniqueDevices());
        
        // 4. 生成缓解建议 (简化实现,不调用RecommendationEngine)
        List<String> recommendations = generateSimpleRecommendations(threatLevel, data);
        
        // 5. 创建评估记录
        ThreatAssessment assessment = new ThreatAssessment();
        assessment.setCustomerId(data.getCustomerId());
        assessment.setAttackMac(data.getAttackMac());
        assessment.setAttackIp(data.getAttackIp());
        
        // 设置评分结果
        assessment.setThreatScore(threatScore);
        assessment.setThreatLevel(threatLevel);
        
        // 设置风险因子
        assessment.setAttackCount(data.getAttackCount());
        assessment.setUniqueIps(data.getUniqueIps());
        assessment.setUniquePorts(data.getUniquePorts());
        assessment.setUniqueDevices(data.getUniqueDevices());
        
        // 设置权重因子
        assessment.setTimeWeight(timeWeight);
        assessment.setIpWeight(ipWeight);
        assessment.setPortWeight(portWeight);
        assessment.setDeviceWeight(deviceWeight);

        if (mlWeightEnabled) {
            Integer tier = data.getDetectionTier();
            double mlWeight = mlWeightService.getMlWeight(data.getCustomerId(), data.getAttackMac(), tier);
            assessment.setMlWeight(mlWeight);
        } else {
            assessment.setMlWeight(1.0);
        }
        assessment.setPreMLScore(threatScore);
        
        // 设置缓解建议 (转换为JSON字符串)
        assessment.setMitigationRecommendations(String.join("; ", recommendations));
        assessment.setMitigationStatus("PENDING");
        
        // 设置时间戳 (使用Instant类型)
        assessment.setAssessmentTime(data.getTimestamp());
        assessment.setCreatedAt(Instant.now());
        assessment.setUpdatedAt(Instant.now());
        
        // 6. 持久化到PostgreSQL
        ThreatAssessment saved = repository.save(assessment);
        
        // 7. 统计CRITICAL威胁
        if ("CRITICAL".equals(threatLevel)) {
            criticalCounter.increment();
            logger.warn("⚠️ CRITICAL threat detected: customerId={}, attackMac={}, score={}",
                       data.getCustomerId(), data.getAttackMac(), threatScore);
        }
        
        logger.info("✅ Threat assessment saved: id={}, customerId={}, level={}, score={}",
                   saved.getId(), data.getCustomerId(), threatLevel, threatScore);
        
        return saved;
    }
    
    /**
     * 生成简化的缓解建议
     * 
     * <p>TODO: 后续集成完整的RecommendationEngine
     */
    private List<String> generateSimpleRecommendations(String threatLevel, AggregatedAttackData data) {
        List<String> recommendations = new java.util.ArrayList<>();
        
        switch (threatLevel) {
            case "CRITICAL":
                recommendations.add("立即隔离攻击源 " + data.getAttackIp() + " (" + data.getAttackMac() + ")");
                recommendations.add("检查同网段其他主机是否被攻陷");
                recommendations.add("审计攻击源的网络访问日志");
                recommendations.add("启动应急响应流程");
                recommendations.add("通知安全团队进行深度分析");
                break;
            case "HIGH":
                recommendations.add("阻断攻击源 " + data.getAttackIp() + " 的高危端口访问");
                recommendations.add("监控攻击源的后续行为");
                recommendations.add("审计相关日志");
                break;
            case "MEDIUM":
                recommendations.add("创建告警单,分配安全分析师");
                recommendations.add("监控攻击源 " + data.getAttackIp());
                break;
            case "LOW":
                recommendations.add("加入监控列表");
                break;
            default:
                recommendations.add("记录日志");
                break;
        }
        
        return recommendations;
    }
    
    /**
     * 熔断器降级方法
     * 
     * <p>当评估服务不可用时,返回默认评估结果
     */
    public ThreatAssessment fallbackAssessment(AggregatedAttackData data, Exception e) {
        logger.error("⚠️ Circuit breaker activated - using fallback assessment: customerId={}, attackMac={}",
                    data.getCustomerId(), data.getAttackMac(), e);
        
        // 创建降级评估记录
        ThreatAssessment fallback = new ThreatAssessment();
        fallback.setCustomerId(data.getCustomerId());
        fallback.setAttackMac(data.getAttackMac());
        fallback.setAttackIp(data.getAttackIp());
        
        // 使用默认值
        fallback.setThreatScore(0.0);
        fallback.setThreatLevel("UNKNOWN");
        fallback.setAttackCount(data.getAttackCount());
        fallback.setUniqueIps(data.getUniqueIps());
        fallback.setUniquePorts(data.getUniquePorts());
        fallback.setUniqueDevices(data.getUniqueDevices());
        fallback.setMlWeight(1.0);
        fallback.setPreMLScore(0.0);
        
        fallback.setMitigationRecommendations("系统暂时不可用,请稍后重试");
        fallback.setMitigationStatus("PENDING");
        
        fallback.setAssessmentTime(data.getTimestamp());
        fallback.setCreatedAt(Instant.now());
        
        return fallback;
    }
}
