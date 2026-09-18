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

    // `search` matches against the customer's name or the sale's own id (cast to text so
    // "12" finds Sale #12) — covers the two things a user actually types when hunting for a
    // sale in a long list.
    @Query("SELECT s FROM Sale s WHERE " +
           "(:customerId IS NULL OR s.customer.id = :customerId) AND " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:search IS NULL OR LOWER(s.customer.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "OR CAST(s.id AS string) LIKE CONCAT('%', CAST(:search AS string), '%')) " +
           "ORDER BY s.createdAt DESC")
    Page<Sale> search(@Param("customerId") Long customerId,
                       @Param("status") SaleStatus status,
                       @Param("search") String search,
                       Pageable pageable);

    List<Sale> findByCustomerIdOrderBySaleDateAsc(Long customerId);

    List<Sale> findByStatusAndSaleDateBetweenOrderBySaleDateAscIdAsc(SaleStatus status, LocalDate startDate, LocalDate endDate);
}
