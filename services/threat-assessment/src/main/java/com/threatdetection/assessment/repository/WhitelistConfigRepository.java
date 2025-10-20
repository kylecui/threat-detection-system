package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.WhitelistConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 白名单配置仓储接口
 * 
 * @author Security Team
 * @version 2.0
 * @since Phase 4
 */
@Repository
public interface WhitelistConfigRepository extends JpaRepository<WhitelistConfig, Long> {
    
    /**
     * 查询客户的所有白名单配置
     * 
     * @param customerId 客户ID
     * @return 白名单配置列表
     */
    List<WhitelistConfig> findByCustomerId(String customerId);
    
    /**
     * 查询客户的有效白名单配置 (激活且未过期)
     * 
     * @param customerId 客户ID
     * @return 有效的白名单配置列表
     */
    @Query("SELECT w FROM WhitelistConfig w WHERE w.customerId = :customerId " +
           "AND w.isActive = true " +
           "AND (w.expiresAt IS NULL OR w.expiresAt > CURRENT_TIMESTAMP)")
    List<WhitelistConfig> findActiveByCustomerId(@Param("customerId") String customerId);
    
    /**
     * 检查IP是否在白名单中
     * 
     * @param customerId 客户ID
     * @param ipAddress IP地址
     * @return 是否在白名单中
     */
    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END " +
           "FROM WhitelistConfig w " +
           "WHERE w.customerId = :customerId " +
           "AND w.whitelistType IN ('IP', 'COMBINED') " +
           "AND w.ipAddress = :ipAddress " +
           "AND w.isActive = true " +
           "AND (w.expiresAt IS NULL OR w.expiresAt > CURRENT_TIMESTAMP)")
    boolean isIpWhitelisted(@Param("customerId") String customerId, 
                           @Param("ipAddress") String ipAddress);
    
    /**
     * 检查MAC是否在白名单中
     * 
     * @param customerId 客户ID
     * @param macAddress MAC地址
     * @return 是否在白名单中
     */
    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END " +
           "FROM WhitelistConfig w " +
           "WHERE w.customerId = :customerId " +
           "AND w.whitelistType IN ('MAC', 'COMBINED') " +
           "AND w.macAddress = :macAddress " +
           "AND w.isActive = true " +
           "AND (w.expiresAt IS NULL OR w.expiresAt > CURRENT_TIMESTAMP)")
    boolean isMacWhitelisted(@Param("customerId") String customerId, 
                            @Param("macAddress") String macAddress);
    
    /**
     * 检查IP+MAC组合是否在白名单中
     * 
     * @param customerId 客户ID
     * @param ipAddress IP地址
     * @param macAddress MAC地址
     * @return 是否在白名单中
     */
    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END " +
           "FROM WhitelistConfig w " +
           "WHERE w.customerId = :customerId " +
           "AND w.whitelistType = 'COMBINED' " +
           "AND w.ipAddress = :ipAddress " +
           "AND w.macAddress = :macAddress " +
           "AND w.isActive = true " +
           "AND (w.expiresAt IS NULL OR w.expiresAt > CURRENT_TIMESTAMP)")
    boolean isCombinationWhitelisted(@Param("customerId") String customerId,
                                     @Param("ipAddress") String ipAddress,
                                     @Param("macAddress") String macAddress);
    
    /**
     * 查询即将过期的白名单 (7天内)
     * 
     * @return 即将过期的白名单列表
     */
    @Query("SELECT w FROM WhitelistConfig w " +
           "WHERE w.expiresAt IS NOT NULL " +
           "AND w.expiresAt > CURRENT_TIMESTAMP " +
           "AND w.expiresAt < :threshold " +
           "AND w.isActive = true")
    List<WhitelistConfig> findExpiringSoon(@Param("threshold") Instant threshold);
    
    /**
     * 查询已过期但仍激活的白名单 (需要清理)
     * 
     * @return 已过期的白名单列表
     */
    @Query("SELECT w FROM WhitelistConfig w " +
           "WHERE w.expiresAt IS NOT NULL " +
           "AND w.expiresAt < CURRENT_TIMESTAMP " +
           "AND w.isActive = true")
    List<WhitelistConfig> findExpired();
    
    /**
     * 根据类型查询白名单
     * 
     * @param customerId 客户ID
     * @param whitelistType 白名单类型
     * @return 白名单配置列表
     */
    List<WhitelistConfig> findByCustomerIdAndWhitelistType(String customerId, String whitelistType);
}
