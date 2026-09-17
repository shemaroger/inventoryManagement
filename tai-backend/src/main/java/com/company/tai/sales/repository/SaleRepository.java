package com.company.tai.sales.repository;

import com.company.tai.sales.entity.Sale;
import com.company.tai.sales.entity.SaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    @Query("SELECT s FROM Sale s WHERE " +
           "(:customerId IS NULL OR s.customer.id = :customerId) AND " +
           "(:status IS NULL OR s.status = :status) " +
           "ORDER BY s.createdAt DESC")
    Page<Sale> search(@Param("customerId") Long customerId,
                       @Param("status") SaleStatus status,
                       Pageable pageable);

    List<Sale> findByCustomerIdOrderBySaleDateAsc(Long customerId);

    List<Sale> findByStatusAndSaleDateBetweenOrderBySaleDateAscIdAsc(SaleStatus status, LocalDate startDate, LocalDate endDate);
}
