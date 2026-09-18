package com.company.tai.purchasing.repository;

import com.company.tai.purchasing.entity.PurchaseOrder;
import com.company.tai.purchasing.entity.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    // `search` matches against the supplier's name or the PO's own id (cast to text so "12"
    // finds PO #12) — same convention as SaleRepository.search().
    @Query("SELECT po FROM PurchaseOrder po WHERE " +
           "(:supplierId IS NULL OR po.supplier.id = :supplierId) AND " +
           "(:status IS NULL OR po.status = :status) AND " +
           "(:search IS NULL OR LOWER(po.supplier.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) " +
           "OR CAST(po.id AS string) LIKE CONCAT('%', CAST(:search AS string), '%')) " +
           "ORDER BY po.createdAt DESC")
    Page<PurchaseOrder> search(@Param("supplierId") Long supplierId,
                                @Param("status") PurchaseOrderStatus status,
                                @Param("search") String search,
                                Pageable pageable);
}
