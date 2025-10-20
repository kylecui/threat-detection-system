package com.threatdetection.assessment.service;

import com.threatdetection.assessment.model.ThreatLabel;
import com.threatdetection.assessment.repository.ThreatLabelRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 威胁标签服务
 * 
 * <p>Phase 4核心服务: 管理威胁标签和自动打标签
 * 
 * <p>核心功能:
 * 1. 威胁标签查询和管理
 * 2. 基于威胁特征自动打标签
 * 3. 标签匹配和推荐
 * 4. 初始化50个默认标签
 * 
 * <p>标签分类:
 * - APT攻击 (5个标签)
 * - 勒索软件 (4个标签)
 * - 扫描行为 (5个标签)
 * - 横向移动 (5个标签)
 * - 暴力破解 (4个标签)
 * - 数据窃取 (3个标签)
 * - 恶意软件 (4个标签)
 * - 网络异常 (4个标签)
 * - 内部威胁 (3个标签)
 * - 其他 (13个标签)
 * 
 * @author Security Team
 * @version 2.0
 * @since Phase 4
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThreatLabelService {
    
    private final ThreatLabelRepository repository;
    
    /**
     * 根据标签代码查询标签
     * 
     * @param labelCode 标签代码
     * @return 标签对象
     */
    @Cacheable(value = "threatLabels", key = "#labelCode")
    public Optional<ThreatLabel> getLabelByCode(String labelCode) {
        if (labelCode == null || labelCode.isEmpty()) {
            return Optional.empty();
        }
        return repository.findByLabelCode(labelCode);
    }
    
    /**
     * 根据分类查询所有标签
     * 
     * @param category 分类 (如 "APT", "RANSOMWARE")
     * @return 标签列表
     */
    public List<ThreatLabel> getLabelsByCategory(String category) {
        log.info("Querying labels by category: {}", category);
        return repository.findByCategory(category);
    }
    
    /**
     * 查询高危标签 (CRITICAL和HIGH)
     * 
     * @return 高危标签列表
     */
    public List<ThreatLabel> getHighSeverityLabels() {
        log.info("Querying high severity labels");
        return repository.findHighSeverityLabels();
    }
    
    /**
     * 查询所有可用标签代码
     * 
     * @return 标签代码列表
     */
    public List<String> getAllLabelCodes() {
        return repository.findAllLabelCodes();
    }
    
    /**
     * 基于威胁特征自动推荐标签
     * 
     * <p>根据攻击特征自动匹配合适的标签:
     * - 端口特征: RDP(3389) → RDP相关标签
     * - IP特征: 高危地区 → 地理位置标签
     * - 行为特征: 大量端口扫描 → 扫描标签
     * 
     * @param uniquePorts 唯一端口数量
     * @param uniqueIps 唯一IP数量
     * @param threatScore 威胁评分
     * @param portNumbers 端口号列表 (可选)
     * @param ipSegmentCategory IP段分类 (可选)
     * @return 推荐的标签代码列表
     */
    public List<String> recommendLabels(int uniquePorts, int uniqueIps, double threatScore,
                                        List<Integer> portNumbers, String ipSegmentCategory) {
        List<String> labels = new ArrayList<>();
        
        // 1. 基于端口特征推荐标签
        if (portNumbers != null && !portNumbers.isEmpty()) {
            if (portNumbers.contains(3389)) {
                labels.add("LATERAL_RDP");
                if (uniqueIps > 1) {
                    labels.add("APT_LATERAL_MOVE");
                }
            }
            if (portNumbers.contains(445)) {
                labels.add("LATERAL_SMB");
                if (uniqueIps > 1) {
                    labels.add("RANSOMWARE_SMB");
                }
            }
            if (portNumbers.contains(22)) {
                labels.add("LATERAL_SSH");
            }
            if (portNumbers.contains(3306) || portNumbers.contains(5432) || 
                portNumbers.contains(1433) || portNumbers.contains(1521)) {
                labels.add("DATA_EXFIL_DB");
            }
        }
        
        // 2. 基于扫描范围推荐标签
        if (uniquePorts >= 20) {
            labels.add("SCAN_PORT_FULL");
            labels.add("APT_RECON");
        } else if (uniquePorts >= 5) {
            labels.add("SCAN_PORT_COMMON");
        }
        
        // 3. 基于横向移动范围推荐标签
        if (uniqueIps >= 5) {
            labels.add("APT_LATERAL_MOVE");
        }
        
        // 4. 基于IP段分类推荐标签
        if (ipSegmentCategory != null) {
            switch (ipSegmentCategory) {
                case "MALICIOUS":
                    labels.add("MALWARE_BOTNET");
                    labels.add("APT_C2_COMM");
                    break;
                case "TOR_EXIT":
                    labels.add("NETWORK_TOR");
                    break;
                case "VPN_PROVIDER":
                    labels.add("NETWORK_VPN");
                    break;
                case "HIGH_RISK_REGION":
                    labels.add("NETWORK_HIGH_RISK_GEO");
                    break;
            }
        }
        
        // 5. 基于威胁评分推荐标签
        if (threatScore > 200) {
            labels.add("APT_C2_COMM");
        }
        
        // 去重
        return labels.stream().distinct().collect(Collectors.toList());
    }
    
    /**
     * 批量查询标签
     * 
     * @param labelCodes 标签代码列表
     * @return 标签对象列表
     */
    public List<ThreatLabel> getLabelsByCodes(List<String> labelCodes) {
        if (labelCodes == null || labelCodes.isEmpty()) {
            return Collections.emptyList();
        }
        
        return labelCodes.stream()
            .map(this::getLabelByCode)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }
    
    /**
     * 统计各分类的标签数量
     * 
     * @return 分类和数量的映射
     */
    public Map<String, Long> countByCategory() {
        List<Object[]> results = repository.countByCategory();
        Map<String, Long> stats = new HashMap<>();
        
        for (Object[] result : results) {
            String category = (String) result[0];
            Long count = (Long) result[1];
            stats.put(category, count);
        }
        
        return stats;
    }
    
    /**
     * 初始化默认威胁标签
     * 
     * <p>在应用启动时自动调用,验证50个默认标签是否存在
     * 如果数据库已有标签,则跳过初始化
     */
    @PostConstruct
    public void initializeDefaultLabels() {
        long count = repository.count();
        
        if (count > 0) {
            log.info("Threat labels already initialized: {} labels", count);
            
            // 输出统计信息
            Map<String, Long> stats = countByCategory();
            log.info("Label distribution by category:");
            stats.forEach((category, labelCount) -> 
                log.info("  - {}: {} labels", category, labelCount)
            );
            return;
        }
        
        log.warn("⚠️ No threat labels found. Please run init-db.sql.phase4");
    }
}
