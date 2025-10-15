package com.threatdetection.ingestion.repository;

import com.threatdetection.ingestion.model.AttackEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 原始攻击事件Repository
 */
@Repository
public interface AttackEventRepository extends JpaRepository<AttackEventEntity, Long> {
    
    /**
     * 根据客户ID查询事件
     */
    List<AttackEventEntity> findByCustomerIdOrderByEventTimestampDesc(String customerId);
    
    /**
     * 根据攻击者MAC地址查询事件
     */
    List<AttackEventEntity> findByAttackMacOrderByEventTimestampDesc(String attackMac);
    
    /**
     * 根据客户ID和攻击者MAC查询事件
     */
    List<AttackEventEntity> findByCustomerIdAndAttackMacOrderByEventTimestampDesc(
        String customerId, String attackMac);
    
    /**
     * 查询指定时间范围内的事件
     */
    @Query("SELECT e FROM AttackEventEntity e " +
           "WHERE e.customerId = :customerId " +
           "AND e.eventTimestamp >= :startTime " +
           "AND e.eventTimestamp < :endTime " +
           "ORDER BY e.eventTimestamp DESC")
    List<AttackEventEntity> findEventsByTimeRange(
        @Param("customerId") String customerId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime);
    
    /**
     * 统计指定时间范围内的事件数量
     */
    @Query("SELECT COUNT(e) FROM AttackEventEntity e " +
           "WHERE e.customerId = :customerId " +
           "AND e.attackMac = :attackMac " +
           "AND e.eventTimestamp >= :startTime " +
           "AND e.eventTimestamp < :endTime")
    Long countEventsByMacAndTimeRange(
        @Param("customerId") String customerId,
        @Param("attackMac") String attackMac,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime);
}
