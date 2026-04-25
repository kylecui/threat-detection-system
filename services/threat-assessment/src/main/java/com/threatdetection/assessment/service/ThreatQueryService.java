package com.threatdetection.assessment.service;

import com.threatdetection.assessment.dto.*;
import com.threatdetection.assessment.model.ThreatAssessment;
import com.threatdetection.assessment.repository.ThreatAssessmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
@Service
@Transactional(readOnly = true)
public class ThreatQueryService {

    private static final Logger log = LoggerFactory.getLogger(ThreatQueryService.class);
    
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
        return getAssessmentList(customerId, page, size, null, null, null, null);
    }

    public Page<ThreatAssessmentDetailResponse> getAssessmentList(String customerId,
                                                                  int page,
                                                                  int size,
                                                                  String threatLevel,
                                                                  Instant startTime,
                                                                  Instant endTime,
                                                                  String attackMac) {
        log.info("Querying assessment list: customerId={}, page={}, size={}, threatLevel={}, startTime={}, endTime={}, attackMac={}",
                customerId, page, size, threatLevel, startTime, endTime, attackMac);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "assessmentTime"));
        Specification<ThreatAssessment> spec = Specification.where((root, query, cb) -> cb.equal(root.get("customerId"), customerId));

        if (threatLevel != null && !threatLevel.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("threatLevel"), threatLevel.trim()));
        }
        if (startTime != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("assessmentTime"), startTime));
        }
        if (endTime != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("assessmentTime"), endTime));
        }
        if (attackMac != null && !attackMac.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("attackMac"), attackMac.trim()));
        }

        Page<ThreatAssessment> assessmentPage = repository.findAll(spec, pageable);
        
        return assessmentPage.map(this::convertToDetailResponse);
    }

    public Page<ThreatAssessmentDetailResponse> getTenantAssessmentList(List<String> customerIds, int page, int size) {
        log.info("Querying tenant assessment list: customerIds={}, page={}, size={}", customerIds, page, size);

        if (customerIds == null || customerIds.isEmpty()) {
            return Page.empty(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "assessmentTime")));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "assessmentTime"));
        Page<ThreatAssessment> assessmentPage = repository.findByCustomerIdInOrderByAssessmentTimeDesc(customerIds, pageable);

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
        
        ThreatStatisticsResponse response = new ThreatStatisticsResponse();
        response.setCustomerId(customerId);
        response.setTotalCount(totalCount);
        response.setCriticalCount(criticalCount);
        response.setHighCount(highCount);
        response.setMediumCount(mediumCount);
        response.setLowCount(lowCount);
        response.setInfoCount(infoCount);
        response.setAverageThreatScore(avgScore != null ? avgScore : 0.0);
        response.setMaxThreatScore(maxScore != null ? maxScore : 0.0);
        response.setMinThreatScore(minScore != null ? minScore : 0.0);
        response.setLevelDistribution(levelDistribution);
        return response;
    }

    public ThreatStatisticsResponse getTenantStatistics(List<String> customerIds) {
        log.info("Getting tenant threat statistics: customerIds={}", customerIds);

        if (customerIds == null || customerIds.isEmpty()) {
            ThreatStatisticsResponse emptyResponse = new ThreatStatisticsResponse();
            emptyResponse.setCustomerId("TENANT");
            emptyResponse.setTotalCount(0L);
            emptyResponse.setCriticalCount(0L);
            emptyResponse.setHighCount(0L);
            emptyResponse.setMediumCount(0L);
            emptyResponse.setLowCount(0L);
            emptyResponse.setInfoCount(0L);
            emptyResponse.setAverageThreatScore(0.0);
            emptyResponse.setMaxThreatScore(0.0);
            emptyResponse.setMinThreatScore(0.0);
            emptyResponse.setLevelDistribution(Map.of(
                    "CRITICAL", 0L,
                    "HIGH", 0L,
                    "MEDIUM", 0L,
                    "LOW", 0L,
                    "INFO", 0L
            ));
            return emptyResponse;
        }

        long totalCount = repository.countByCustomerIdIn(customerIds);
        long criticalCount = repository.countByCustomerIdInAndLevel(customerIds, "CRITICAL");
        long highCount = repository.countByCustomerIdInAndLevel(customerIds, "HIGH");
        long mediumCount = repository.countByCustomerIdInAndLevel(customerIds, "MEDIUM");
        long lowCount = repository.countByCustomerIdInAndLevel(customerIds, "LOW");
        long infoCount = repository.countByCustomerIdInAndLevel(customerIds, "INFO");

        Double avgScore = repository.getAverageThreatScoreForCustomers(customerIds);
        Double maxScore = repository.getMaxThreatScoreForCustomers(customerIds);
        Double minScore = repository.getMinThreatScoreForCustomers(customerIds);

        Map<String, Long> levelDistribution = Map.of(
                "CRITICAL", criticalCount,
                "HIGH", highCount,
                "MEDIUM", mediumCount,
                "LOW", lowCount,
                "INFO", infoCount
        );

        ThreatStatisticsResponse response = new ThreatStatisticsResponse();
        response.setCustomerId("TENANT");
        response.setTotalCount(totalCount);
        response.setCriticalCount(criticalCount);
        response.setHighCount(highCount);
        response.setMediumCount(mediumCount);
        response.setLowCount(lowCount);
        response.setInfoCount(infoCount);
        response.setAverageThreatScore(avgScore != null ? avgScore : 0.0);
        response.setMaxThreatScore(maxScore != null ? maxScore : 0.0);
        response.setMinThreatScore(minScore != null ? minScore : 0.0);
        response.setLevelDistribution(levelDistribution);
        return response;
    }
    
    /**
     * 获取威胁趋势 (最近24小时,按小时聚合)
     * 
     * @param customerId 客户ID (必需)
     * @return 趋势数据点列表
     */
    public List<TrendDataPoint> getThreatTrend(String customerId, int hours) {
        log.info("Getting threat trend: customerId={}, hours={}", customerId, hours);
        
        Instant now = Instant.now();
        Instant since = now.minus(hours, ChronoUnit.HOURS);
        
        List<Object[]> rawData = repository.getHourlyTrend(customerId, since, now);
        
        return rawData.stream()
                .map(this::convertToTrendDataPoint)
                .collect(Collectors.toList());
    }

    public List<TrendDataPoint> getTenantThreatTrend(List<String> customerIds, int hours) {
        log.info("Getting tenant threat trend: customerIds={}, hours={}", customerIds, hours);

        if (customerIds == null || customerIds.isEmpty()) {
            return Collections.emptyList();
        }

        Instant now = Instant.now();
        Instant since = now.minus(hours, ChronoUnit.HOURS);

        List<Object[]> rawData = repository.getHourlyTrendForCustomers(customerIds, since, now);

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
    public List<PortDistribution> getPortDistribution(String customerId, int hours) {
        log.info("Getting port distribution: customerId={}, hours={}", customerId, hours);
        
        // 查询指定时间范围的威胁
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
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
                    
                    PortDistribution distribution = new PortDistribution();
                    distribution.setPort(port);
                    distribution.setPortName(label);
                    distribution.setCount(count);
                    distribution.setPercentage(Math.round(percentage * 100.0) / 100.0);
                    return distribution;
                })
                .collect(Collectors.toList());
    }

    public List<PortDistribution> getTenantPortDistribution(List<String> customerIds, int hours) {
        log.info("Getting tenant port distribution: customerIds={}, hours={}", customerIds, hours);

        if (customerIds == null || customerIds.isEmpty()) {
            return Collections.emptyList();
        }

        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        List<ThreatAssessment> recentThreats = repository.findRecent24HoursForCustomers(customerIds, since);

        Map<Integer, Long> portCountMap = new HashMap<>();

        for (ThreatAssessment threat : recentThreats) {
            String portList = threat.getPortList();
            if (portList != null && !portList.isEmpty()) {
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

        long totalCount = portCountMap.values().stream().mapToLong(Long::longValue).sum();

        return portCountMap.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    int port = entry.getKey();
                    long count = entry.getValue();
                    double percentage = totalCount > 0 ? (count * 100.0 / totalCount) : 0.0;

                    String portName = PORT_NAMES.getOrDefault(port, String.valueOf(port));
                    String label = port + "-" + portName;

                    PortDistribution distribution = new PortDistribution();
                    distribution.setPort(port);
                    distribution.setPortName(label);
                    distribution.setCount(count);
                    distribution.setPercentage(Math.round(percentage * 100.0) / 100.0);
                    return distribution;
                })
                .collect(Collectors.toList());
    }

    public List<TopAttackerResponse> getTopAttackers(String customerId, int limit, int hours) {
        log.info("Getting top attackers: customerId={}, limit={}, hours={}", customerId, limit, hours);

        int safeLimit = Math.max(limit, 1);
        int safeHours = Math.max(hours, 1);
        Instant since = Instant.now().minus(safeHours, ChronoUnit.HOURS);

        List<Object[]> rows = repository.findTopAttackers(customerId, since, safeLimit);
        return rows.stream().map(this::convertToTopAttackerResponse).collect(Collectors.toList());
    }

    public List<TopAttackerResponse> getTenantTopAttackers(List<String> customerIds, int limit, int hours) {
        log.info("Getting tenant top attackers: customerIds={}, limit={}, hours={}", customerIds, limit, hours);

        if (customerIds == null || customerIds.isEmpty()) {
            return Collections.emptyList();
        }

        int safeLimit = Math.max(limit, 1);
        int safeHours = Math.max(hours, 1);
        Instant since = Instant.now().minus(safeHours, ChronoUnit.HOURS);

        List<Object[]> rows = repository.findTopAttackersForCustomers(customerIds, since, safeLimit);
        return rows.stream().map(this::convertToTopAttackerResponse).collect(Collectors.toList());
    }
    
    /**
     * 转换实体到详情响应DTO
     */
    private ThreatAssessmentDetailResponse convertToDetailResponse(ThreatAssessment assessment) {
        List<String> recommendations = generateRecommendations(assessment);
        
        ThreatAssessmentDetailResponse response = new ThreatAssessmentDetailResponse();
        response.setId(assessment.getId());
        response.setCustomerId(assessment.getCustomerId());
        response.setAttackMac(assessment.getAttackMac());
        response.setAttackIp("N/A");
        response.setThreatScore(assessment.getThreatScore());
        response.setThreatLevel(assessment.getThreatLevel());
        response.setAttackCount(assessment.getAttackCount());
        response.setUniqueIps(assessment.getUniqueIps());
        response.setUniquePorts(assessment.getUniquePorts());
        response.setUniqueDevices(assessment.getUniqueDevices());
        response.setAssessmentTime(assessment.getAssessmentTime());
        response.setCreatedAt(assessment.getCreatedAt());
        response.setPortList(assessment.getPortList());
        response.setPortRiskScore(assessment.getPortRiskScore());
        response.setDetectionTier(assessment.getDetectionTier());
        response.setMitigationRecommendations(recommendations);
        return response;
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
        
        TrendDataPoint point = new TrendDataPoint();
        point.setTimestamp(timestamp);
        point.setCount(count);
        point.setAverageScore(Math.round(avgScore * 100.0) / 100.0);
        point.setMaxScore(Math.round(maxScore * 100.0) / 100.0);
        point.setCriticalCount(criticalCount);
        point.setHighCount(highCount);
        point.setMediumCount(mediumCount);
        return point;
    }

    private TopAttackerResponse convertToTopAttackerResponse(Object[] row) {
        String attackMac = row[0] != null ? String.valueOf(row[0]) : null;
        String attackIp = row[1] != null ? String.valueOf(row[1]) : null;
        long totalCount = row[2] != null ? ((Number) row[2]).longValue() : 0L;

        double maxThreatScore;
        if (row[3] instanceof BigDecimal bigDecimal) {
            maxThreatScore = bigDecimal.doubleValue();
        } else if (row[3] instanceof Number number) {
            maxThreatScore = number.doubleValue();
        } else {
            maxThreatScore = 0.0;
        }

        String maxThreatLevel = row[4] != null ? String.valueOf(row[4]) : null;

        return TopAttackerResponse.builder()
                .attackMac(attackMac)
                .attackIp(attackIp)
                .totalCount(totalCount)
                .maxThreatScore(maxThreatScore)
                .maxThreatLevel(maxThreatLevel)
                .build();
    }
}
