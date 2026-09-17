package com.company.tai.sales.repository;

import com.company.tai.sales.entity.SalePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalePaymentRepository extends JpaRepository<SalePayment, Long> {
    List<SalePayment> findBySaleIdOrderByPaymentDateDesc(Long saleId);
    List<SalePayment> findBySaleIdInOrderByPaymentDateAsc(List<Long> saleIds);
}
