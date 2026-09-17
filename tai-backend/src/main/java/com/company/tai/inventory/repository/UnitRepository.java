package com.company.tai.inventory.repository;

import com.company.tai.inventory.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<Unit, Long> {
}
