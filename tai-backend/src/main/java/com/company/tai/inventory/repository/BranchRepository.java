package com.company.tai.inventory.repository;

import com.company.tai.inventory.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {
}
