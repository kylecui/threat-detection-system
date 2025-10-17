package com.threatdetection.customer.repository;

import com.threatdetection.customer.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 客户数据访问层
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * 根据customerId查找客户
     */
    Optional<Customer> findByCustomerId(String customerId);

    /**
     * 检查customerId是否已存在
     */
    boolean existsByCustomerId(String customerId);

    /**
     * 根据邮箱查找客户
     */
    Optional<Customer> findByEmail(String email);

    /**
     * 根据状态查找客户
     */
    List<Customer> findByStatus(Customer.CustomerStatus status);

    /**
     * 分页查询指定状态的客户
     */
    Page<Customer> findByStatus(Customer.CustomerStatus status, Pageable pageable);

    /**
     * 根据订阅套餐查找客户
     */
    List<Customer> findBySubscriptionTier(Customer.SubscriptionTier tier);

    /**
     * 根据名称模糊查询 (支持分页)
     */
    @Query("SELECT c FROM Customer c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.customerId) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Customer> searchCustomers(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 统计各状态客户数量
     */
    @Query("SELECT c.status, COUNT(c) FROM Customer c GROUP BY c.status")
    List<Object[]> countByStatus();

    /**
     * 统计各订阅套餐客户数量
     */
    @Query("SELECT c.subscriptionTier, COUNT(c) FROM Customer c GROUP BY c.subscriptionTier")
    List<Object[]> countBySubscriptionTier();
}
