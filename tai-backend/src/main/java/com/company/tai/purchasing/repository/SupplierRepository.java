package com.company.tai.purchasing.repository;

import com.company.tai.purchasing.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
}
