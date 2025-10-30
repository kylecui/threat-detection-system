package com.threatdetection.stream.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * 攻击阶段分类器
 *
 * <p>基于端口序列和攻击模式推断APT攻击阶段
 * 支持渐进式分类，当数据不足时降级到单阶段分析
 *
 * @author Threat Detection Team
 * @version 1.0
 */
public class AttackPhaseClassifier {

    private static final Logger logger = LoggerFactory.getLogger(AttackPhaseClassifier.class);

    // 关键端口定义
    private static final Set<Integer> RECON_PORTS = Set.of(21, 22, 23, 25, 53, 80, 110, 143, 443, 993, 995);
    private static final Set<Integer> EXPLOITATION_PORTS = Set.of(135, 139, 445, 3389, 5985, 5986);
    private static final Set<Integer> PERSISTENCE_PORTS = Set.of(3306, 5432, 6379, 27017, 1433);

    /**
     * 攻击阶段分类结果
     */
    public static class AttackPhaseClassification {
        private final String phase;
        private final double confidence;

        public AttackPhaseClassification(String phase, double confidence) {
            this.phase = phase;
            this.confidence = confidence;
        }

        public String getPhase() { return phase; }
        public double getConfidence() { return confidence; }
    }

    /**
     * 分类攻击阶段
     *
     * @param customerId 客户ID
     * @param attackMac 攻击者MAC
     * @param portList 端口列表 (可能为null)
     * @param attackCount 攻击次数
     * @return 分类结果
     */
    public AttackPhaseClassification classifyPhase(String customerId, String attackMac,
                                                  List<Integer> portList, int attackCount) {

        // 如果没有端口列表，基于攻击次数进行基础分类
        if (portList == null || portList.isEmpty()) {
            return classifyByAttackCount(attackCount);
        }

        // 基于端口分析进行精确分类
        return classifyByPorts(portList, attackCount);
    }

    /**
     * 基于端口列表分类攻击阶段
     */
    private AttackPhaseClassification classifyByPorts(List<Integer> portList, int attackCount) {
        int totalPorts = portList.size();

        // 统计各阶段端口数量
        int reconCount = 0;
        int exploitationCount = 0;
        int persistenceCount = 0;

        for (int port : portList) {
            if (RECON_PORTS.contains(port)) {
                reconCount++;
            } else if (EXPLOITATION_PORTS.contains(port)) {
                exploitationCount++;
            } else if (PERSISTENCE_PORTS.contains(port)) {
                persistenceCount++;
            }
        }

        // 计算各阶段的比例
        double reconRatio = (double) reconCount / totalPorts;
        double exploitationRatio = (double) exploitationCount / totalPorts;
        double persistenceRatio = (double) persistenceCount / totalPorts;

        // 确定主要阶段
        String phase;
        double confidence;

        if (persistenceRatio > 0.5) {
            // 主要针对数据存储端口 - 持久化阶段
            phase = "PERSISTENCE";
            confidence = Math.min(0.9, persistenceRatio + 0.1);
        } else if (exploitationRatio > 0.3) {
            // 主要针对系统漏洞端口 - 利用阶段
            phase = "EXPLOITATION";
            confidence = Math.min(0.8, exploitationRatio + 0.2);
        } else if (reconRatio > 0.4) {
            // 主要针对常见服务端口 - 侦察阶段
            phase = "RECON";
            confidence = Math.min(0.7, reconRatio + 0.1);
        } else {
            // 混合或未知模式
            phase = "UNKNOWN";
            confidence = 0.3;
        }

        // 攻击频率调整置信度
        confidence = adjustConfidenceByFrequency(confidence, attackCount);

        logger.debug("Port-based phase classification: ports={}, recon={}, exploitation={}, persistence={}, phase={}, confidence={}",
                    portList, reconCount, exploitationCount, persistenceCount, phase, confidence);

        return new AttackPhaseClassification(phase, confidence);
    }

    /**
     * 基于攻击次数的降级分类
     */
    private AttackPhaseClassification classifyByAttackCount(int attackCount) {
        String phase;
        double confidence;

        if (attackCount > 1000) {
            // 高频攻击 - 可能是自动化扫描或利用阶段
            phase = "EXPLOITATION";
            confidence = 0.6;
        } else if (attackCount > 100) {
            // 中频攻击 - 可能是侦察或初步利用
            phase = "RECON";
            confidence = 0.4;
        } else {
            // 低频攻击 - 未知阶段
            phase = "UNKNOWN";
            confidence = 0.2;
        }

        logger.debug("Fallback phase classification by attack count: count={}, phase={}, confidence={}",
                    attackCount, phase, confidence);

        return new AttackPhaseClassification(phase, confidence);
    }

    /**
     * 根据攻击频率调整置信度
     */
    private double adjustConfidenceByFrequency(double baseConfidence, int attackCount) {
        if (attackCount > 1000) {
            // 高频攻击增加置信度
            return Math.min(0.95, baseConfidence + 0.2);
        } else if (attackCount > 100) {
            // 中频攻击略微增加置信度
            return Math.min(0.85, baseConfidence + 0.1);
        } else if (attackCount < 10) {
            // 低频攻击降低置信度
            return Math.max(0.1, baseConfidence - 0.2);
        }

        return baseConfidence;
    }

    /**
     * 获取阶段描述
     */
    public String getPhaseDescription(String phase) {
        switch (phase) {
            case "RECON":
                return "侦察阶段 - 扫描常见服务端口，收集目标信息";
            case "EXPLOITATION":
                return "利用阶段 - 针对系统漏洞进行攻击";
            case "PERSISTENCE":
                return "持久化阶段 - 访问数据存储，窃取敏感信息";
            case "UNKNOWN":
                return "未知阶段 - 无法确定具体攻击意图";
            default:
                return "未分类";
        }
    }

    /**
     * 获取阶段严重性评分
     */
    public double getPhaseSeverityScore(String phase) {
        switch (phase) {
            case "RECON":
                return 1.0;  // 基础威胁
            case "EXPLOITATION":
                return 2.0;  // 中等威胁
            case "PERSISTENCE":
                return 3.0;  // 高危威胁
            case "UNKNOWN":
                return 1.5;  // 未知但可疑
            default:
                return 1.0;
        }
    }
}