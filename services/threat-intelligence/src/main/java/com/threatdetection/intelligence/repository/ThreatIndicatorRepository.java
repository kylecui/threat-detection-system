package com.threatdetection.intelligence.repository;

import com.threatdetection.intelligence.model.IocType;
import com.threatdetection.intelligence.model.Severity;
import com.threatdetection.intelligence.model.ThreatIndicator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ThreatIndicatorRepository extends JpaRepository<ThreatIndicator, Long> {

    List<ThreatIndicator> findByIocValueAndValidUntilIsNullOrIocValueAndValidUntilAfter(
            String iocValue1, String iocValue2, Instant now);

    /**
     * Effective indicators exclude rows owned by a feed that an operator has
     * disabled. Indicators whose source is not a configured feed (for example,
     * manually-created/internal data) remain eligible.
     */
    @Query("SELECT t FROM ThreatIndicator t WHERE t.iocValue = :iocValue " +
            "AND (t.validUntil IS NULL OR t.validUntil > :now) " +
            "AND NOT EXISTS (SELECT f.id FROM ThreatIntelFeed f " +
            "WHERE f.feedName = t.sourceName AND f.enabled = false)")
    List<ThreatIndicator> findActiveByIocValue(@Param("iocValue") String iocValue, @Param("now") Instant now);

    List<ThreatIndicator> findBySourceName(String sourceName);

    Optional<ThreatIndicator> findByIocValueAndSourceName(String iocValue, String sourceName);

    long countBySourceName(String sourceName);

    @Query("SELECT COUNT(t) FROM ThreatIndicator t WHERE t.sourceName = :sourceName " +
            "AND (t.validUntil IS NULL OR t.validUntil > :now)")
    long countActiveBySourceName(@Param("sourceName") String sourceName, @Param("now") Instant now);

    @Query("SELECT COUNT(t) FROM ThreatIndicator t " +
            "WHERE (t.validUntil IS NULL OR t.validUntil > :now) " +
            "AND NOT EXISTS (SELECT f.id FROM ThreatIntelFeed f " +
            "WHERE f.feedName = t.sourceName AND f.enabled = false)")
    long countActive(@Param("now") Instant now);

    Page<ThreatIndicator> findByIocType(IocType iocType, Pageable pageable);

    Page<ThreatIndicator> findBySeverity(Severity severity, Pageable pageable);

    Page<ThreatIndicator> findByIocTypeAndSeverity(IocType iocType, Severity severity, Pageable pageable);
}
