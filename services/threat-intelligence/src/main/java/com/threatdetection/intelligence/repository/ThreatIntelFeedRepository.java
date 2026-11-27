package com.threatdetection.intelligence.repository;

import com.threatdetection.intelligence.model.ThreatIntelFeed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThreatIntelFeedRepository extends JpaRepository<ThreatIntelFeed, Long> {

    Optional<ThreatIntelFeed> findByFeedName(String feedName);

    List<ThreatIntelFeed> findByEnabledTrue();

    /**
     * Stable UI order: the built-in source is always first and external feeds
     * retain their creation order. Database row order must never be relied on,
     * especially after polling or enabled-status updates create new row versions.
     */
    @Query("""
            SELECT feed
            FROM ThreatIntelFeed feed
            ORDER BY CASE WHEN UPPER(feed.feedType) = 'INTERNAL' THEN 0 ELSE 1 END,
                     feed.id ASC
            """)
    List<ThreatIntelFeed> findAllInDisplayOrder();

    long countByEnabledTrue();

    @Query("""
            SELECT COUNT(feed)
            FROM ThreatIntelFeed feed
            WHERE feed.enabled = true AND UPPER(feed.feedType) <> 'INTERNAL'
            """)
    long countEnabledExternalFeeds();
}
