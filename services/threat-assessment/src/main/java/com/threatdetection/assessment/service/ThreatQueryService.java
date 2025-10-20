package com.threatdetection.assessment.service;

import com.threatdetection.assessment.dto.*;
import com.threatdetection.assessment.model.ThreatAssessment;
import com.threatdetection.assessment.repository.ThreatAssessmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 威胁评估查询服务
 * 
 * <p>对齐API文档: threat_assessment_query_api.md
 * <p>核心功能:
 * <ul>
 *   <li>威胁评估列表查询 (分页)</li>
 *   <li>威胁统计 (等级分布、平均分)</li>
 *   <li>威胁趋势分析 (24小时)</li>
 *   <li>端口分布分析</li>
 * </ul>
 * 
 * @author ThreatDetection Team
 * @version 2.0
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class ThreatQueryService {
    
    private final ThreatAssessmentRepository repository;
    
    // 常用端口名称映射
    private static final Map<Integer, String> PORT_NAMES = Map.ofEntries(
            Map.entry(22, "SSH"),
            Map.entry(23, "Telnet"),
            Map.entry(80, "HTTP"),
            Map.entry(135, "RPC"),
            Map.entry(139, "NetBIOS"),
            Map.entry(445, "SMB"),
            Map.entry(1433, "SQL Server"),
            Map.entry(3306, "MySQL"),
            Map.entry(3389, "RDP"),
            Map.entry(5432, "PostgreSQL"),
            Map.entry(5900, "VNC"),
            Map.entry(8080, "HTTP-Proxy")
    );
    
    public ThreatQueryService(ThreatAssessmentRepository repository) {
        this.repository = repository;
    }
    
    /**
     * 获取评估详情
     * 
     * @param id 评估ID
     * @return 评估详情
     */
    public ThreatAssessmentDetailResponse getAssessmentDetail(Long id) {
        log.debug("Getting assessment detail: id={}", id);
        
        ThreatAssessment assessment = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + id));
        
        return convertToDetailResponse(assessment);
    }
    
    /**
     * 查询评估列表 (分页)
     * 
     * @param customerId 客户ID (必需)
     * @param page 页码 (从0开始)
     * @param size 每页大小
     * @return 分页结果
     */
    public Page<ThreatAssessmentDetailResponse> getAssessmentList(String customerId, int page, int size) {
        log.info("Querying assessment list: customerId={}, page={}, size={}", customerId, page, size);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "assessmentTime"));
        Page<ThreatAssessment> assessmentPage = repository.findByCustomerIdOrderByAssessmentTimeDesc(customerId, pageable);
        
        return assessmentPage.map(this::convertToDetailResponse);
    }
    
    /**
     * 获取威胁统计
     * 
     * @param customerId 客户ID (必需)
     * @return 统计结果
     */
    public ThreatStatisticsResponse getStatistics(String customerId) {
        log.info("Getting threat statistics: customerId={}", customerId);
        
        long totalCount = repository.countByCustomerId(customerId);
        long criticalCount = repository.countByCustomerIdAndLevel(customerId, "CRITICAL");
        long highCount = repository.countByCustomerIdAndLevel(customerId, "HIGH");
        long mediumCount = repository.countByCustomerIdAndLevel(customerId, "MEDIUM");
        long lowCount = repository.countByCustomerIdAndLevel(customerId, "LOW");
        long infoCount = repository.countByCustomerIdAndLevel(customerId, "INFO");
        
        Double avgScore = repository.getAverageThreatScore(customerId);
        Double maxScore = repository.getMaxThreatScore(customerId);
        Double minScore = repository.getMinThreatScore(customerId);
        
        Map<String, Long> levelDistribution = Map.of(
                "CRITICAL", criticalCount,
                "HIGH", highCount,
                "MEDIUM", mediumCount,
                "LOW", lowCount,
                "INFO", infoCount
        );
        
        return ThreatStatisticsResponse.builder()
                .customerId(customerId)
                .totalCount(totalCount)
                .criticalCount(criticalCount)
                .highCount(highCount)
                .mediumCount(mediumCount)
                .lowCount(lowCount)
                .infoCount(infoCount)
                .averageThreatScore(avgScore != null ? avgScore : 0.0)
                .maxThreatScore(maxScore != null ? maxScore : 0.0)
                .minThreatScore(minScore != null ? minScore : 0.0)
                .levelDistribution(levelDistribution)
                .build();
    }
    
    /**
     * 获取威胁趋势 (最近24小时,按小时聚合)
     * 
     * @param customerId 客户ID (必需)
     * @return 趋势数据点列表
     */
    public List<TrendDataPoint> getThreatTrend(String customerId) {
        log.info("Getting threat trend: customerId={}", customerId);
        
        Instant now = Instant.now();
        Instant yesterday = now.minus(24, ChronoUnit.HOURS);
        
        List<Object[]> rawData = repository.getHourlyTrend(customerId, yesterday, now);
        
        return rawData.stream()
                .map(this::convertToTrendDataPoint)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取端口分布
     * 
     * @param customerId 客户ID (必需)
     * @return 端口分布列表 (TOP 10)
     */
    public List<PortDistribution> getPortDistribution(String customerId) {
        log.info("Getting port distribution: customerId={}", customerId);
        
        // 查询最近24小时的威胁
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        List<ThreatAssessment> recentThreats = repository.findRecent24Hours(customerId, since);
        
        // 解析portList字段,统计端口出现次数
        Map<Integer, Long> portCountMap = new HashMap<>();
        
        for (ThreatAssessment threat : recentThreats) {
            String portList = threat.getPortList();
            if (portList != null && !portList.isEmpty()) {
                // 假设portList格式: "80,443,3389" 或 "[80, 443, 3389]"
                String cleaned = portList.replaceAll("[\\[\\]\\s]", "");
                String[] ports = cleaned.split(",");
                
                for (String portStr : ports) {
                    try {
                        int port = Integer.parseInt(portStr.trim());
                        portCountMap.merge(port, 1L, Long::sum);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid port number: {}", portStr);
                    }
                }
            }
        }
        
        // 计算总数
        long totalCount = portCountMap.values().stream().mapToLong(Long::longValue).sum();
        
        // 转换为DTO并排序 (TOP 10)
        return portCountMap.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    int port = entry.getKey();
                    long count = entry.getValue();
                    double percentage = totalCount > 0 ? (count * 100.0 / totalCount) : 0.0;
                    
                    String portName = PORT_NAMES.getOrDefault(port, String.valueOf(port));
                    String label = port + "-" + portName;
                    
                    return PortDistribution.builder()
                            .port(port)
                            .portName(label)
                            .count(count)
                            .percentage(Math.round(percentage * 100.0) / 100.0)
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 转换实体到详情响应DTO
     */
    private ThreatAssessmentDetailResponse convertToDetailResponse(ThreatAssessment assessment) {
        List<String> recommendations = generateRecommendations(assessment);
        
        return ThreatAssessmentDetailResponse.builder()
                .id(assessment.getId())
                .customerId(assessment.getCustomerId())
                .attackMac(assessment.getAttackMac())
                .attackIp("N/A")  // 数据库没有存储attackIp
                .threatScore(assessment.getThreatScore())
                .threatLevel(assessment.getThreatLevel())
                .attackCount(assessment.getAttackCount())
                .uniqueIps(assessment.getUniqueIps())
                .uniquePorts(assessment.getUniquePorts())
                .uniqueDevices(assessment.getUniqueDevices())
                .assessmentTime(assessment.getAssessmentTime())
                .createdAt(assessment.getCreatedAt())
                .portList(assessment.getPortList())
                .portRiskScore(assessment.getPortRiskScore())
                .detectionTier(assessment.getDetectionTier())
                .mitigationRecommendations(recommendations)
                .build();
    }
    
    /**
     * 生成缓解建议
     */
    private List<String> generateRecommendations(ThreatAssessment assessment) {
        List<String> recommendations = new ArrayList<>();
        
        String level = assessment.getThreatLevel();
        
        if ("CRITICAL".equals(level)) {
            recommendations.add("立即隔离攻击源 " + assessment.getAttackMac());
            recommendations.add("检查同网段其他主机是否被攻陷");
            recommendations.add("启动应急响应流程");
        } else if ("HIGH".equals(level)) {
            recommendations.add("尽快隔离攻击源 " + assessment.getAttackMac());
            recommendations.add("审计网络访问日志");
        } else if ("MEDIUM".equals(level)) {
            recommendations.add("监控攻击源 " + assessment.getAttackMac());
            recommendations.add("加强访问控制");
        } else {
            recommendations.add("持续监控");
        }
        
        return recommendations;
    }
    
    /**
     * 转换数据库查询结果到趋势数据点
     * 
     * <p>数据库返回格式: [timestamp, count, avg_score, max_score, critical_count, high_count, medium_count]
     */
    private TrendDataPoint convertToTrendDataPoint(Object[] row) {
        // 处理时间戳 - 可能是Timestamp或Instant
        Instant timestamp;
        if (row[0] instanceof Timestamp) {
            timestamp = ((Timestamp) row[0]).toInstant();
        } else if (row[0] instanceof Instant) {
            timestamp = (Instant) row[0];
        } else {
            throw new IllegalArgumentException("Unexpected timestamp type: " + row[0].getClass());
        }
        
        Long count = ((Number) row[1]).longValue();
        Double avgScore = row[2] != null ? ((BigDecimal) row[2]).doubleValue() : 0.0;
        Double maxScore = row[3] != null ? ((BigDecimal) row[3]).doubleValue() : 0.0;
        Long criticalCount = ((Number) row[4]).longValue();
        Long highCount = ((Number) row[5]).longValue();
        Long mediumCount = ((Number) row[6]).longValue();
        
        return TrendDataPoint.builder()
                .timestamp(timestamp)
                .count(count)
                .averageScore(Math.round(avgScore * 100.0) / 100.0)
                .maxScore(Math.round(maxScore * 100.0) / 100.0)
                .criticalCount(criticalCount)
                .highCount(highCount)
                .mediumCount(mediumCount)
                .build();
    }
}
