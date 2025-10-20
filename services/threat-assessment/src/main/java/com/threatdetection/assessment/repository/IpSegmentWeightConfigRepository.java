package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.IpSegmentWeightConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * IP段权重配置仓储接口
 * 
 * <p>提供IP段配置的数据访问方法:
 * - IP匹配查询 (支持IPv4和IPv6)
 * - 按分类查询
 * - 高危网段筛选
 * - 优先级排序
 * 
 * @author Security Team
 * @version 2.0
 * @since Phase 3
 */
@Repository
public interface IpSegmentWeightConfigRepository extends JpaRepository<IpSegmentWeightConfig, Long> {
    
    /**
     * 根据IP地址查找所属网段配置 (原生SQL实现)
     * 
     * <p>使用inet类型比较,支持IPv4和IPv6
     * <p>当多个网段匹配时,返回优先级最高的配置
     * 
     * @param ipAddress IP地址 (如 "192.168.1.100")
     * @return 匹配的网段配置
     */
    @Query(value = """
        SELECT * FROM ip_segment_weight_config
        WHERE CAST(:ipAddress AS inet) BETWEEN CAST(ip_range_start AS inet) AND CAST(ip_range_end AS inet)
        ORDER BY priority DESC, weight DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<IpSegmentWeightConfig> findByIpAddress(@Param("ipAddress") String ipAddress);
    
    /**
     * 根据网段名称查询
     * 
     * @param segmentName 网段名称
     * @return 网段配置
     */
    Optional<IpSegmentWeightConfig> findBySegmentName(String segmentName);
    
    /**
     * 根据分类查询所有网段
     * 
     * @param category 分类 (如 "CLOUD_AWS", "HIGH_RISK_REGION")
     * @return 该分类下的所有网段配置
     */
    List<IpSegmentWeightConfig> findByCategory(String category);
    
    /**
     * 查询高危网段 (权重 >= 指定阈值)
     * 
     * @param threshold 权重阈值 (如 1.7)
     * @return 高危网段列表,按权重降序
     */
    @Query("SELECT s FROM IpSegmentWeightConfig s WHERE s.weight >= :threshold ORDER BY s.weight DESC, s.priority DESC")
    List<IpSegmentWeightConfig> findHighRiskSegments(@Param("threshold") Double threshold);
    
    /**
     * 查询所有已知恶意网段
     * 
     * @return 恶意网段列表
     */
    @Query("SELECT s FROM IpSegmentWeightConfig s WHERE s.category = 'MALICIOUS' ORDER BY s.priority DESC")
    List<IpSegmentWeightConfig> findMaliciousSegments();
    
    /**
     * 根据权重范围查询
     * 
     * @param minWeight 最小权重
     * @param maxWeight 最大权重
     * @return 符合条件的网段列表
     */
    List<IpSegmentWeightConfig> findByWeightBetween(Double minWeight, Double maxWeight);
    
    /**
     * 统计已配置的网段数量
     * 
     * @return 网段总数
     */
    @Query("SELECT COUNT(s) FROM IpSegmentWeightConfig s")
    long countConfiguredSegments();
    
    /**
     * 按分类统计网段数量
     * 
     * @return 分类和数量的映射
     */
    @Query("SELECT s.category, COUNT(s) FROM IpSegmentWeightConfig s GROUP BY s.category")
    List<Object[]> countByCategory();
    
    /**
     * 检查IP是否属于内网
     * 
     * @param ipAddress IP地址
     * @return 是否为内网IP
     */
    @Query(value = """
        SELECT EXISTS(
            SELECT 1 FROM ip_segment_weight_config
            WHERE category = 'PRIVATE'
            AND CAST(:ipAddress AS inet) BETWEEN CAST(ip_range_start AS inet) AND CAST(ip_range_end AS inet)
        )
        """, nativeQuery = true)
    boolean isPrivateIp(@Param("ipAddress") String ipAddress);
}
