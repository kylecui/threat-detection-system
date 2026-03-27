package com.threatdetection.intelligence.repository;

import com.threatdetection.intelligence.model.ThreatIntelFeed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ThreatIntelFeedRepository extends JpaRepository<ThreatIntelFeed, Long> {

    Optional<ThreatIntelFeed> findByFeedName(String feedName);

    List<ThreatIntelFeed> findByEnabledTrue();
}
