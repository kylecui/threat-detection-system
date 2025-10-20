package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.ThreatLabel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 威胁标签仓储接口
 * 
 * @author Security Team
 * @version 2.0
 * @since Phase 4
 */
@Repository
public interface ThreatLabelRepository extends JpaRepository<ThreatLabel, Long> {
    
    /**
     * 根据标签代码查询
     * 
     * @param labelCode 标签代码
     * @return 标签对象
     */
    Optional<ThreatLabel> findByLabelCode(String labelCode);
    
    /**
     * 根据分类查询所有标签
     * 
     * @param category 分类 (如 "APT", "RANSOMWARE")
     * @return 该分类下的所有标签
     */
    List<ThreatLabel> findByCategory(String category);
    
    /**
     * 根据严重程度查询标签
     * 
     * @param severity 严重程度 (如 "CRITICAL", "HIGH")
     * @return 符合严重程度的标签列表
     */
    List<ThreatLabel> findBySeverity(String severity);
    
    /**
     * 查询严重程度高于等于指定级别的标签
     * 
     * @param severity 最低严重程度
     * @return 标签列表
     */
    @Query("SELECT l FROM ThreatLabel l WHERE l.severity IN ('CRITICAL', 'HIGH') ORDER BY " +
           "CASE l.severity " +
           "WHEN 'CRITICAL' THEN 1 " +
           "WHEN 'HIGH' THEN 2 " +
           "ELSE 3 END")
    List<ThreatLabel> findHighSeverityLabels();
    
    /**
     * 统计各分类的标签数量
     * 
     * @return 分类和数量的映射
     */
    @Query("SELECT l.category, COUNT(l) FROM ThreatLabel l GROUP BY l.category")
    List<Object[]> countByCategory();
    
    /**
     * 查询所有可用标签代码
     * 
     * @return 标签代码列表
     */
    @Query("SELECT l.labelCode FROM ThreatLabel l ORDER BY l.category, l.labelCode")
    List<String> findAllLabelCodes();
}
