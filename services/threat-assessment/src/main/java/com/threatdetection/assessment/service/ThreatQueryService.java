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
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

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
            Map.entry(21, "FTP"),
            Map.entry(22, "SSH"),
            Map.entry(23, "Telnet"),
            Map.entry(25, "SMTP"),
            Map.entry(53, "DNS"),
            Map.entry(80, "HTTP"),
            Map.entry(110, "POP3"),
            Map.entry(135, "RPC"),
            Map.entry(139, "NetBIOS"),
            Map.entry(143, "IMAP"),
            Map.entry(443, "HTTPS"),
            Map.entry(445, "SMB"),
            Map.entry(993, "IMAPS"),
            Map.entry(995, "POP3S"),
            Map.entry(1433, "SQL Server"),
            Map.entry(1521, "Oracle"),
            Map.entry(2049, "NFS"),
            Map.entry(3306, "MySQL"),
            Map.entry(3389, "RDP"),
            Map.entry(5432, "PostgreSQL"),
            Map.entry(5900, "VNC"),
            Map.entry(6379, "Redis"),
            Map.entry(8080, "HTTP-Proxy"),
            Map.entry(9200, "Elasticsearch"),
            Map.entry(27017, "MongoDB")
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

    @Transactional
    @CacheEvict(value = "overviewSnapshots", allEntries = true)
    public void deleteAssessment(Long id, String customerId, Long tenantId) {
        ThreatAssessment assessment = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found: " + id));
        verifyDeleteScope(assessment, customerId, tenantId);
        repository.delete(assessment);
        log.info("Deleted assessment id={} customerId={} tenantId={}", id, customerId, tenantId);
    }

    @Transactional
    @CacheEvict(value = "overviewSnapshots", allEntries = true)
    public int deleteAssessments(List<Long> ids, String customerId, Long tenantId) {
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(ids);
        List<ThreatAssessment> assessments = repository.findAllById(uniqueIds);
        if (assessments.size() != uniqueIds.size()) {
            throw new IllegalArgumentException("One or more assessments were not found");
        }
        assessments.forEach(assessment -> verifyDeleteScope(assessment, customerId, tenantId));
        repository.deleteAllInBatch(assessments);
        log.info("Deleted {} assessments customerId={} tenantId={}", assessments.size(), customerId, tenantId);
        return assessments.size();
    }

    private void verifyDeleteScope(ThreatAssessment assessment, String customerId, Long tenantId) {
        if (customerId != null && !customerId.isBlank()
                && !customerId.trim().equals(assessment.getCustomerId())) {
            throw new IllegalArgumentException("Assessment is outside the requested customer scope");
        }
        if (tenantId != null && !repository.existsByIdAndTenantId(assessment.getId(), tenantId)) {
            throw new IllegalArgumentException("Assessment is outside the requested tenant scope");
        }
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
        return getAssessmentList(customerId, page, size, null, null, null, null, null);
    }

    public Page<ThreatAssessmentDetailResponse> getAssessmentList(String customerId,
                                                                  int page,
                                                                  int size,
                                                                  String threatLevel,
                                                                  Instant startTime,
                                                                  Instant endTime,
                                                                  String attackMac,
                                                                  Integer detectionTier) {
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
        if (detectionTier != null) {
            if (detectionTier < 1 || detectionTier > 3) {
                throw new IllegalArgumentException("detection_tier must be 1, 2 or 3");
            }
            spec = spec.and((root, query, cb) -> cb.equal(root.get("detectionTier"), detectionTier));
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
        return toStatisticsResponse(customerId, repository.getStatisticsAggregate(customerId));
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

        return toStatisticsResponse("TENANT", repository.getStatisticsAggregateForCustomers(customerIds));
    }

    @Cacheable(
            value = "overviewSnapshots",
            key = "(#customerId == null ? '' : #customerId) + ':' + (#tenantId == null ? '' : #tenantId) + ':' + #hours",
            sync = true
    )
    public OverviewSnapshotResponse getOverviewSnapshot(String customerId, Long tenantId, int hours) {
        int safeHours = Math.max(1, Math.min(hours, 24 * 90));
        Instant now = Instant.now();
        boolean dailyBuckets = safeHours > 48;
        int bucketCount = dailyBuckets ? Math.max(1, (int) Math.ceil(safeHours / 24.0)) : safeHours;
        Instant bucketEnd = dailyBuckets
                ? now.truncatedTo(ChronoUnit.DAYS)
                : now.truncatedTo(ChronoUnit.HOURS);
        Instant since = dailyBuckets
                ? bucketEnd.minus(bucketCount - 1L, ChronoUnit.DAYS)
                : bucketEnd.minus(bucketCount - 1L, ChronoUnit.HOURS);

        if (customerId != null && !customerId.isBlank()) {
            List<Object[]> rawTrend = dailyBuckets
                    ? repository.getDailyTrend(customerId, since, now)
                    : repository.getHourlyTrend(customerId, since, now);
            return OverviewSnapshotResponse.builder()
                    .generatedAt(now)
                    .statistics(toStatisticsResponse(customerId,
                            repository.getStatisticsAggregateSince(customerId, since)))
                    .trend(fillTrendBuckets(rawTrend, since, bucketEnd, dailyBuckets))
                    .portDistribution(toPortDistribution(repository.getPortDistributionRows(customerId, since, 15)))
                    .nonPortProtocols(toNonPortProtocols(repository.getNonPortProtocolRows(customerId, since)))
                    .topAttackers(getTopAttackers(customerId, 10, safeHours))
                    .recentThreats(repository.findDistinctRecentByCustomer(customerId, since, 10).stream()
                            .map(this::convertToDetailResponse).collect(Collectors.toList()))
                    .build();
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("customer_id or tenant_id is required");
        }
        List<Object[]> rawTrend = dailyBuckets
                ? repository.getDailyTrendForTenant(tenantId, since, now)
                : repository.getHourlyTrendForTenant(tenantId, since, now);
        return OverviewSnapshotResponse.builder()
                .generatedAt(now)
                .statistics(toStatisticsResponse("TENANT",
                        repository.getStatisticsAggregateForTenantSince(tenantId, since)))
                .trend(fillTrendBuckets(rawTrend, since, bucketEnd, dailyBuckets))
                .portDistribution(toPortDistribution(repository.getPortDistributionRowsForTenant(tenantId, since, 15)))
                .nonPortProtocols(toNonPortProtocols(repository.getNonPortProtocolRowsForTenant(tenantId, since)))
                .topAttackers(repository.findTopAttackersForTenant(tenantId, since, 10).stream()
                        .map(this::convertToTopAttackerResponse).collect(Collectors.toList()))
                .recentThreats(repository.findDistinctRecentByTenant(tenantId, since, 10).stream()
                        .map(this::convertToDetailResponse).collect(Collectors.toList()))
                .build();
    }

    private List<TrendDataPoint> fillTrendBuckets(List<Object[]> rows,
                                                  Instant bucketStart,
                                                  Instant bucketEnd,
                                                  boolean dailyBuckets) {
        ChronoUnit unit = dailyBuckets ? ChronoUnit.DAYS : ChronoUnit.HOURS;
        Map<Instant, TrendDataPoint> existing = rows.stream()
                .map(this::convertToTrendDataPoint)
                .collect(Collectors.toMap(
                        item -> item.getTimestamp().truncatedTo(unit),
                        item -> item,
                        (left, right) -> left));
        List<TrendDataPoint> result = new ArrayList<>();
        for (Instant cursor = bucketStart; !cursor.isAfter(bucketEnd); cursor = cursor.plus(1, unit)) {
            TrendDataPoint item = existing.get(cursor);
            if (item == null) {
                item = TrendDataPoint.builder()
                        .timestamp(cursor)
                        .count(0L)
                        .averageScore(0.0)
                        .maxScore(0.0)
                        .criticalCount(0L)
                        .highCount(0L)
                        .mediumCount(0L)
                        .build();
            }
            result.add(item);
        }
        return result;
    }

    private List<NonPortProtocolDistribution> toNonPortProtocols(List<Object[]> rows) {
        return rows.stream()
                .map(row -> NonPortProtocolDistribution.builder()
                        .protocol(String.valueOf(row[0]))
                        .count(number(row[1]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    private List<PortDistribution> toPortDistribution(List<Object[]> rows) {
        long fallbackTotal = rows.stream().mapToLong(row -> number(row[1]).longValue()).sum();
        return rows.stream().map(row -> {
            int port = number(row[0]).intValue();
            long count = number(row[1]).longValue();
            long total = row.length > 2 ? number(row[2]).longValue() : fallbackTotal;
            PortDistribution item = new PortDistribution();
            item.setPort(port);
            item.setPortName(PORT_NAMES.get(port));
            item.setCount(count);
            item.setPercentage(total == 0 ? 0.0 : Math.round(count * 10000.0 / total) / 100.0);
            return item;
        }).collect(Collectors.toList());
    }

    private ThreatStatisticsResponse toStatisticsResponse(String scope, Object[] row) {
        if (row == null || row.length < 9) {
            row = new Object[] {0L, 0L, 0L, 0L, 0L, 0L, 0.0, 0.0, 0.0};
        }

        long totalCount = number(row[0]).longValue();
        long criticalCount = number(row[1]).longValue();
        long highCount = number(row[2]).longValue();
        long mediumCount = number(row[3]).longValue();
        long lowCount = number(row[4]).longValue();
        long infoCount = number(row[5]).longValue();

        ThreatStatisticsResponse response = new ThreatStatisticsResponse();
        response.setCustomerId(scope);
        response.setTotalCount(totalCount);
        response.setCriticalCount(criticalCount);
        response.setHighCount(highCount);
        response.setMediumCount(mediumCount);
        response.setLowCount(lowCount);
        response.setInfoCount(infoCount);
        response.setAverageThreatScore(number(row[6]).doubleValue());
        response.setMaxThreatScore(number(row[7]).doubleValue());
        response.setMinThreatScore(number(row[8]).doubleValue());
        response.setLevelDistribution(Map.of(
                "CRITICAL", criticalCount,
                "HIGH", highCount,
                "MEDIUM", mediumCount,
                "LOW", lowCount,
                "INFO", infoCount
        ));
        return response;
    }

    private Number number(Object value) {
        return value instanceof Number ? (Number) value : 0;
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
     * @return 原始攻击事件端口分布列表 (TOP 15)
     */
    public List<PortDistribution> getPortDistribution(String customerId, int hours) {
        log.info("Getting port distribution: customerId={}, hours={}", customerId, hours);
        
        int safeHours = Math.max(1, Math.min(hours, 24 * 90));
        Instant since = Instant.now().minus(safeHours, ChronoUnit.HOURS);
        return toPortDistribution(repository.getPortDistributionRows(customerId, since, 15));
    }

    public List<PortDistribution> getTenantPortDistribution(List<String> customerIds, int hours) {
        log.info("Getting tenant port distribution: customerIds={}, hours={}", customerIds, hours);

        if (customerIds == null || customerIds.isEmpty()) {
            return Collections.emptyList();
        }

        int safeHours = Math.max(1, Math.min(hours, 24 * 90));
        Instant since = Instant.now().minus(safeHours, ChronoUnit.HOURS);
        return toPortDistribution(repository.getPortDistributionRowsForCustomers(customerIds, since, 15));
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
     * 获取按设备分组的威胁评估列表
     */
    public Page<GroupedThreatResponse> getGroupedAssessmentList(String customerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Object[]> rawPage = repository.findGroupedByCustomerId(customerId, pageable);
        return rawPage.map(this::convertToGroupedResponse);
    }

    /**
     * 获取多客户按设备分组的威胁评估列表
     */
    public Page<GroupedThreatResponse> getTenantGroupedAssessmentList(List<String> customerIds, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Object[]> rawPage = repository.findGroupedByCustomerIds(customerIds, pageable);
        return rawPage.map(this::convertToGroupedResponse);
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
        response.setAttackIp(assessment.getAttackIp());
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

        // Build score breakdown from persisted fields
        ScoreBreakdown breakdown = ScoreBreakdown.builder()
                .baseScore(assessment.getBaseScore() != null ? assessment.getBaseScore() : 0.0)
                .timeWeight(assessment.getTimeWeight() != null ? assessment.getTimeWeight() : 1.0)
                .ipWeight(assessment.getIpWeight() != null ? assessment.getIpWeight() : 1.0)
                .portWeight(assessment.getPortWeight() != null ? assessment.getPortWeight() : 1.0)
                .deviceWeight(assessment.getDeviceWeight() != null ? assessment.getDeviceWeight() : 1.0)
                .attackSourceWeight(assessment.getAttackSourceWeight() != null ? assessment.getAttackSourceWeight() : 1.0)
                .honeypotSensitivityWeight(assessment.getHoneypotSensitivityWeight() != null ? assessment.getHoneypotSensitivityWeight() : 1.0)
                .combinedSegmentWeight(assessment.getCombinedSegmentWeight() != null ? assessment.getCombinedSegmentWeight() : 1.0)
                .attackRateWeight(assessment.getAttackRateWeight() != null ? assessment.getAttackRateWeight() : 1.0)
                .attackRate(assessment.getAttackRate() != null ? assessment.getAttackRate() : 0.0)
                .mlWeight(assessment.getMlWeight() != null ? assessment.getMlWeight() : 1.0)
                .mlEnabled(assessment.getMlWeight() != null && assessment.getMlWeight() != 1.0)
                .rawScore(assessment.getRawScore() != null ? assessment.getRawScore() : 0.0)
                .normalizedScore(assessment.getThreatScore() != null ? assessment.getThreatScore() : 0.0)
                .formula("log₁₀(baseScore × timeW × ipW × portW × deviceW × segmentW × rateW × mlW + 1) × 25")
                .build();
        response.setScoreBreakdown(breakdown);
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

    private GroupedThreatResponse convertToGroupedResponse(Object[] row) {
        String attackMac = row[0] != null ? String.valueOf(row[0]) : null;
        String customerId = row[1] != null ? String.valueOf(row[1]) : null;
        String maxThreatLevel = row[2] != null ? String.valueOf(row[2]) : null;

        double maxThreatScore;
        if (row[3] instanceof java.math.BigDecimal bd) {
            maxThreatScore = bd.doubleValue();
        } else if (row[3] instanceof Number n) {
            maxThreatScore = n.doubleValue();
        } else {
            maxThreatScore = 0.0;
        }

        long assessmentCount = row[4] != null ? ((Number) row[4]).longValue() : 0L;

        Instant latestAssessmentTime;
        if (row[5] instanceof java.sql.Timestamp ts) {
            latestAssessmentTime = ts.toInstant();
        } else if (row[5] instanceof Instant inst) {
            latestAssessmentTime = inst;
        } else {
            latestAssessmentTime = null;
        }

        long totalAttackCount = row[6] != null ? ((Number) row[6]).longValue() : 0L;
        int tierCount = row[7] != null ? ((Number) row[7]).intValue() : 0;

        return GroupedThreatResponse.builder()
                .attackMac(attackMac)
                .customerId(customerId)
                .maxThreatLevel(maxThreatLevel)
                .maxThreatScore(maxThreatScore)
                .assessmentCount(assessmentCount)
                .latestAssessmentTime(latestAssessmentTime)
                .totalAttackCount(totalAttackCount)
                .tierCount(tierCount)
                .build();
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
