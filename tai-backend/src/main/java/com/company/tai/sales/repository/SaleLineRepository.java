package com.company.tai.sales.repository;

import com.company.tai.sales.entity.SaleLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleLineRepository extends JpaRepository<SaleLine, Long> {
}
