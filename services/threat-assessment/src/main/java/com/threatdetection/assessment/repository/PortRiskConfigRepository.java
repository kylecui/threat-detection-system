package com.threatdetection.assessment.repository;

import com.threatdetection.assessment.model.PortRiskConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 端口风险配置仓储接口
 * 
 * @author Security Team
 * @version 2.0
 */
@Repository
public interface PortRiskConfigRepository extends JpaRepository<PortRiskConfig, Long> {

    /**
     * 根据端口号查询配置
     */
    Optional<PortRiskConfig> findByPortNumber(Integer portNumber);

    /**
     * 批量查询端口配置
     */
    List<PortRiskConfig> findByPortNumberIn(List<Integer> portNumbers);

    /**
     * 根据风险分类查询
     */
    List<PortRiskConfig> findByCategory(String category);

    /**
     * 查询高危端口 (riskScore >= threshold)
     */
    @Query("SELECT p FROM PortRiskConfig p WHERE p.riskScore >= ?1 ORDER BY p.riskScore DESC")
    List<PortRiskConfig> findHighRiskPorts(Double threshold);

    /**
     * 统计已配置端口数量
     */
    @Query("SELECT COUNT(p) FROM PortRiskConfig p")
    long countConfiguredPorts();
}
