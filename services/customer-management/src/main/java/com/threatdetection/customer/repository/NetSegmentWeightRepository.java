package com.threatdetection.customer.repository;

import com.threatdetection.customer.model.NetSegmentWeight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NetSegmentWeightRepository extends JpaRepository<NetSegmentWeight, Long> {

    List<NetSegmentWeight> findByCustomerId(String customerId);

    Optional<NetSegmentWeight> findByCustomerIdAndCidr(String customerId, String cidr);

    void deleteByCustomerIdAndId(String customerId, Long id);

    boolean existsByCustomerIdAndCidr(String customerId, String cidr);
}
