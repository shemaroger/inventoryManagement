package com.company.tai.purchasing.repository;

import com.company.tai.purchasing.entity.SupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {
    List<SupplierPayment> findByPurchaseOrderIdOrderByPaymentDateDesc(Long purchaseOrderId);
}
